cmake_minimum_required(VERSION 3.10)
project(lottie_engine LANGUAGES C CXX ASM)

add_library(lottie_engine STATIC
    src/lottie/lottieanimation.cpp
    src/lottie/lottieitem.cpp
    src/lottie/lottiekeypath.cpp
    src/lottie/lottieloader.cpp
    src/lottie/lottiemodel.cpp
    src/lottie/lottieparser.cpp
    src/lottie/lottieproxymodel.cpp
    src/vector/freetype/v_ft_math.cpp
    src/vector/freetype/v_ft_raster.cpp
    src/vector/freetype/v_ft_stroker.cpp
    src/vector/pixman/vregion.cpp
    src/vector/stb/stb_image.cpp
    src/vector/vbezier.cpp
    src/vector/vbitmap.cpp
    src/vector/vbrush.cpp
    src/vector/vcompositionfunctions.cpp
    src/vector/vdasher.cpp
    src/vector/vdebug.cpp
    src/vector/vdrawable.cpp
    src/vector/vdrawhelper.cpp
    src/vector/vdrawhelper_neon.cpp
    src/vector/velapsedtimer.cpp
    src/vector/vimageloader.cpp
    src/vector/vinterpolator.cpp
    src/vector/vmatrix.cpp
    src/vector/vpainter.cpp
    src/vector/vpath.cpp
    src/vector/vpathmesure.cpp
    src/vector/vraster.cpp
    src/vector/vrect.cpp
    src/vector/vrle.cpp
)

target_compile_options(lottie_engine PUBLIC
    -Wall
    -fno-rtti
    -finline-functions
    -ffast-math
    -Os
    -fno-unwind-tables
    -fno-asynchronous-unwind-tables
    -Wnon-virtual-dtor
    -Woverloaded-virtual
    -Wno-unused-parameter
    -fvisibility=hidden
)

# Telegram's Android build wires pixman GNU-asm NEON sources through its full
# native build. In Axoo this backend is isolated behind a selector, and NDK 27
# fails those asm macros with the local CMake path. Build the portable C++ path
# for Phase B compile validation; NEON can be revisited after correctness tests.
target_compile_options(lottie_engine PRIVATE
    -U__ARM_NEON__
    -U__ARM64_NEON__
)

set_target_properties(lottie_engine PROPERTIES ANDROID_ARM_MODE arm)

target_compile_definitions(lottie_engine PUBLIC
    HAVE_PTHREAD
    NDEBUG
)

target_include_directories(lottie_engine PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/inc
    ${CMAKE_CURRENT_SOURCE_DIR}/src/vector
    ${CMAKE_CURRENT_SOURCE_DIR}/src/vector/pixman
    ${CMAKE_CURRENT_SOURCE_DIR}/src/vector/freetype
    ${CMAKE_CURRENT_SOURCE_DIR}/src/vector/stb
)
