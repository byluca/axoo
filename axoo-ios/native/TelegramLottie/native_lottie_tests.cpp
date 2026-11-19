#include "AxooLottie.h"
#include <atomic>
#include <fstream>
#include <iostream>
#include <iterator>
#include <memory>
#include <string>
#include <thread>
#include <vector>
#include <zlib.h>

#define CHECK(condition) do { if (!(condition)) { std::cerr << "FAIL line " << __LINE__ << ": " << #condition << '\n'; return 1; } } while (false)
using Animation = std::unique_ptr<AXAnimation, decltype(&AXAnimationDestroy)>;

int main(int argc, char **argv) {
    CHECK(argc == 3);
    CHECK(AXAnimationCreate(nullptr, 0) == nullptr);
    const std::string bad = "not JSON";
    CHECK(AXAnimationCreate(reinterpret_cast<const uint8_t *>(bad.data()), bad.size()) == nullptr);
    const uint8_t truncated[] = {0x1f, 0x8b, 0x08};
    CHECK(AXAnimationCreate(truncated, sizeof(truncated)) == nullptr);
    const std::string path = std::string(argv[1]) + "/animations/via-re-david/sunglasses.tgs";
    std::ifstream stream(path, std::ios::binary);
    CHECK(stream.good());
    const std::vector<uint8_t> data((std::istreambuf_iterator<char>(stream)), std::istreambuf_iterator<char>());
    Animation animation(AXAnimationCreate(data.data(), data.size()), AXAnimationDestroy);
    CHECK(animation != nullptr);
    const auto info = AXAnimationGetInfo(animation.get());
    CHECK(info.width > 0 && info.height > 0 && info.frame_count > 1 && info.frames_per_second > 0);
    std::vector<uint32_t> pixels(96 * 96, 0xffffffff);
    CHECK(AXAnimationRender(animation.get(), 0, 96, 96, reinterpret_cast<uint8_t *>(pixels.data()), pixels.size() * 4) == AXAnimationOK);
    CHECK(AXAnimationRender(animation.get(), info.frame_count, 96, 96, reinterpret_cast<uint8_t *>(pixels.data()), pixels.size() * 4) == AXAnimationInvalidFrame);
    CHECK(AXAnimationRender(animation.get(), 0, 96, 96, reinterpret_cast<uint8_t *>(pixels.data()), 4) == AXAnimationInvalidInput);
    const auto first = pixels;
    std::atomic<int> errors{0};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&] {
        std::vector<uint32_t> buffer(96 * 96);
        for (int j = 0; j < 20; ++j) {
            if (AXAnimationRender(animation.get(), 0, 96, 96, reinterpret_cast<uint8_t *>(buffer.data()), buffer.size() * 4) != AXAnimationOK || buffer != first) ++errors;
        }
    });
    for (auto &thread : threads) thread.join();
    CHECK(errors == 0);
    // A solid-red Lottie layer verifies the packed pixel contract independently
    // of the source artwork (RGBA bytes, despite the upstream ARGB doc comment).
    const std::string solid = R"({"v":"5.5.7","fr":30,"ip":0,"op":30,"w":32,"h":32,"layers":[{"ty":1,"ind":1,"sw":32,"sh":32,"sc":"#ff0000","ip":0,"op":30,"st":0,"ks":{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[0,0,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}}}]})";
    Animation red(AXAnimationCreate(reinterpret_cast<const uint8_t *>(solid.data()), solid.size()), AXAnimationDestroy);
    CHECK(red != nullptr);
    std::vector<uint32_t> square(32 * 32);
    CHECK(AXAnimationRender(red.get(), 0, 32, 32, reinterpret_cast<uint8_t *>(square.data()), square.size() * 4) == AXAnimationOK);
    const auto *rgba = reinterpret_cast<const uint8_t *>(&square[16 * 32 + 16]);
    CHECK(rgba[0] == 255 && rgba[1] == 0 && rgba[2] == 0 && rgba[3] == 255);
    auto translucent = solid;
    const auto opacity = translucent.find("\"k\":100");
    CHECK(opacity != std::string::npos);
    translucent.replace(opacity, 7, "\"k\":50");
    Animation half(AXAnimationCreate(reinterpret_cast<const uint8_t *>(translucent.data()), translucent.size()), AXAnimationDestroy);
    CHECK(half != nullptr);
    CHECK(AXAnimationRender(half.get(), 0, 32, 32, reinterpret_cast<uint8_t *>(square.data()), square.size() * 4) == AXAnimationOK);
    CHECK(rgba[0] == rgba[3] && rgba[3] >= 126 && rgba[3] <= 129 && rgba[1] == 0 && rgba[2] == 0);
    // Exercise the shipping bridge's gzip loader on every bundled asset.
    std::ifstream manifest(argv[2]);
    CHECK(manifest.good());
    std::string assetPath;
    size_t assetCount = 0, frameCount = 0;
    while (std::getline(manifest, assetPath)) {
        if (assetPath.empty()) continue;
        std::ifstream assetFile(assetPath, std::ios::binary);
        CHECK(assetFile.good());
        const std::vector<uint8_t> compressed((std::istreambuf_iterator<char>(assetFile)), std::istreambuf_iterator<char>());
        Animation asset(AXAnimationCreate(compressed.data(), compressed.size()), AXAnimationDestroy);
        CHECK(asset != nullptr);
        const auto metadata = AXAnimationGetInfo(asset.get());
        bool visible = false;
        for (auto frame : {uint32_t(0), metadata.frame_count / 2, metadata.frame_count - 1}) {
            CHECK(AXAnimationRender(asset.get(), frame, 96, 96, reinterpret_cast<uint8_t *>(pixels.data()), pixels.size() * 4) == AXAnimationOK);
            for (auto pixel : pixels) visible |= (pixel >> 24) != 0;
            ++frameCount;
        }
        CHECK(visible);
        ++assetCount;
    }
    CHECK(assetCount > 0);
    std::cout << "Bundled TGS through shipping bridge: " << assetCount << " assets, " << frameCount << " frames\n";
    std::cout << "Native TGS/JSON, invalid input, pixel format and 80 concurrent shared-handle renders passed\n";
}
