cmake_minimum_required(VERSION 3.10)
project(rlottie_jni LANGUAGES C CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LOTTIE_MODULE OFF CACHE BOOL "" FORCE)
set(LOTTIE_THREAD OFF CACHE BOOL "" FORCE)
set(LOTTIE_TEST OFF CACHE BOOL "" FORCE)

add_subdirectory(lottie_engine EXCLUDE_FROM_ALL)
add_library(rlottie_jni SHARED lottie_bridge.cpp math_fallbacks.cpp)

target_include_directories(rlottie_jni PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/lottie_engine/inc"
)

target_link_libraries(rlottie_jni
    lottie_engine
    jnigraphics
    log
)

target_compile_options(rlottie_jni PRIVATE -std=c++14 -O2)

set(EXPORT_SCRIPT "${CMAKE_CURRENT_BINARY_DIR}/jni_exports.lds")
file(WRITE "${EXPORT_SCRIPT}" "{ global: Java_*; local: *; };\n")
target_link_options(
    rlottie_jni
    PRIVATE
    "-Wl,--version-script=${EXPORT_SCRIPT}"
    "-Wl,-z,max-page-size=16384"
)
