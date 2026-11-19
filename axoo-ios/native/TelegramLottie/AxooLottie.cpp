#include "AxooLottie.h"
#include "rlottie.h"
#include <zlib.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>

struct AXAnimation {
    std::unique_ptr<rlottie::Animation> renderer;
    AXAnimationInfo info;
    std::mutex mutex;
};

namespace {
constexpr size_t maxInput = 2 * 1024 * 1024, maxJSON = 4 * 1024 * 1024;
std::atomic<uint64_t> nextKey{0};

bool decode(const uint8_t *data, size_t count, std::string &json) {
    if (!data || count == 0 || count > maxInput) return false;
    if (count < 2 || data[0] != 0x1f || data[1] != 0x8b) {
        json.assign(reinterpret_cast<const char *>(data), count);
        return true;
    }
    z_stream stream{};
    stream.next_in = const_cast<Bytef *>(data);
    stream.avail_in = static_cast<uInt>(count);
    if (inflateInit2(&stream, 16 + MAX_WBITS) != Z_OK) return false;
    struct End { z_stream *stream; ~End() { inflateEnd(stream); } } end{&stream};
    char block[16384];
    int result;
    do {
        stream.next_out = reinterpret_cast<Bytef *>(block);
        stream.avail_out = sizeof(block);
        result = inflate(&stream, Z_NO_FLUSH);
        const size_t size = sizeof(block) - stream.avail_out;
        if (json.size() + size > maxJSON) return false;
        json.append(block, size);
    } while (result == Z_OK);
    return result == Z_STREAM_END && stream.avail_in == 0 && !json.empty();
}
}

extern "C" AXAnimation *AXAnimationCreate(const uint8_t *data, size_t count) {
    try {
        std::string json;
        if (!decode(data, count, json)) return nullptr;
        auto renderer = rlottie::Animation::loadFromData(std::move(json),
            "axoo-ios-" + std::to_string(nextKey.fetch_add(1)), nullptr, rlottie::FitzModifier::None);
        if (!renderer) return nullptr;
        size_t width = 0, height = 0;
        renderer->size(width, height);
        const auto frames = renderer->totalFrame();
        const auto fps = renderer->frameRate();
        if (!width || !height || width > 8192 || height > 8192 || frames == 0 ||
            frames > 36000 || !std::isfinite(fps) || fps <= 0 || fps > 240) return nullptr;
        auto handle = std::unique_ptr<AXAnimation>(new AXAnimation());
        handle->info = {static_cast<uint32_t>(width), static_cast<uint32_t>(height),
                        static_cast<uint32_t>(frames), fps};
        handle->renderer = std::move(renderer);
        return handle.release();
    } catch (...) { return nullptr; }
}

extern "C" AXAnimationInfo AXAnimationGetInfo(AXAnimation *animation) {
    return animation ? animation->info : AXAnimationInfo{};
}

extern "C" int AXAnimationRender(AXAnimation *animation, uint32_t frame, uint32_t width,
                                  uint32_t height, uint8_t *pixels, size_t count) {
    if (!animation || !pixels || width == 0 || height == 0 || width > 1024 || height > 1024 ||
        count != size_t(width) * height * 4 || reinterpret_cast<uintptr_t>(pixels) % alignof(uint32_t))
        return AXAnimationInvalidInput;
    if (frame >= animation->info.frame_count) return AXAnimationInvalidFrame;
    try {
        std::lock_guard<std::mutex> lock(animation->mutex);
        std::memset(pixels, 0, count);
        rlottie::Surface surface(reinterpret_cast<uint32_t *>(pixels), width, height, size_t(width) * 4);
        bool rendered = false;
        animation->renderer->renderSync(frame, surface, true, &rendered);
        return rendered ? AXAnimationOK : AXAnimationRenderFailed;
    } catch (...) { return AXAnimationRenderFailed; }
}

extern "C" void AXAnimationDestroy(AXAnimation *animation) { delete animation; }
