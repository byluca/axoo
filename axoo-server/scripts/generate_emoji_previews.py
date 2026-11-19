#!/usr/bin/env python3
"""Render static Content Desk previews using the Android checkout's TGS engine.

Requires cmake, a C++ compiler and Pillow. Writes only to axoo-server and a
temporary build directory; the Android checkout is read-only.
"""

import argparse
import gzip
import json
from pathlib import Path
import re
import subprocess
import tempfile

from PIL import Image


ROOT = Path(__file__).resolve().parent.parent
SIZE = 128
RENDERER = r'''
#include <rlottie.h>
#include <algorithm>
#include <cstdint>
#include <iostream>
#include <iterator>
#include <vector>

int main() {
    std::string json{std::istreambuf_iterator<char>(std::cin), {}};
    auto animation = rlottie::Animation::loadFromData(json, "preview", nullptr);
    if (!animation || !animation->totalFrame()) return 1;
    std::vector<uint32_t> pixels(128 * 128);
    rlottie::Surface surface(pixels.data(), 128, 128, 128 * 4);
    for (double position : {0.0, 0.1, 0.25, 0.5, 0.75}) {
        bool result = false;
        animation->renderSync(animation->frameAtPos(position), surface, true, &result);
        if (std::any_of(pixels.begin(), pixels.end(), [](uint32_t p) { return p >> 24; })) break;
    }
    for (auto pixel : pixels) {
        unsigned alpha = pixel >> 24;
        for (int shift : {16, 8, 0}) {
            unsigned value = alpha ? std::min(255u, ((pixel >> shift) & 255) * 255 / alpha) : 0;
            std::cout.put(static_cast<char>(value));
        }
        std::cout.put(static_cast<char>(alpha));
    }
}
'''
CMAKE = '''cmake_minimum_required(VERSION 3.10)
project(emoji_preview LANGUAGES C CXX ASM)
set(CMAKE_CXX_STANDARD 14)
add_subdirectory("${ENGINE_DIR}" engine)
target_compile_options(lottie_engine PRIVATE -include limits)
add_executable(emoji_preview render.cpp)
target_link_libraries(emoji_preview PRIVATE lottie_engine pthread)
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-root", type=Path, default=ROOT.parent / "axoo-kotlin")
    args = parser.parse_args()
    android = args.android_root.resolve()
    assets = android / "app/src/main/assets/axooji"
    engine = android / "app/src/main/cpp/lottie_engine"
    catalog = json.loads((assets / "catalog.json").read_text())
    output = ROOT / "cmd/utility/contentdesk/emoji-previews"
    with tempfile.TemporaryDirectory(prefix="axoo-emoji-preview-") as temp:
        source = Path(temp)
        (source / "CMakeLists.txt").write_text(CMAKE)
        (source / "render.cpp").write_text(RENDERER)
        build = source / "build"
        subprocess.run(["cmake", "-S", str(source), "-B", str(build),
                        f"-DENGINE_DIR={engine}", "-DCMAKE_BUILD_TYPE=Release"], check=True)
        subprocess.run(["cmake", "--build", str(build), "--parallel", "4"], check=True)
        for emoji in catalog["emojis"]:
            name = emoji["name"]
            if not re.fullmatch(r"[a-z0-9-]+/[a-z0-9-]+", name):
                raise ValueError(f"Invalid emoji name: {name}")
            data = gzip.decompress((assets / "animations" / f"{name}.tgs").read_bytes())
            result = subprocess.run([str(build / "emoji_preview")], input=data,
                                    capture_output=True, check=True)
            image = Image.frombytes("RGBA", (SIZE, SIZE), result.stdout)
            if not image.getchannel("A").getbbox():
                raise ValueError(f"Empty preview: {name}")
            path = output / f"{name}.webp"
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path, "WEBP", quality=85, method=6)
    print(f"Generated {len(catalog['emojis'])} static previews in {output}")


if __name__ == "__main__":
    main()
