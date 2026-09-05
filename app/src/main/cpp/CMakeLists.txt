cmake_minimum_required(VERSION 3.22)
project(sdcpp_bridge)

# Needle ships a small, official Android arm64 static runtime. Pin both its
# release URL and digest: configure fails closed if upstream bytes ever change.
set(NEEDLE_DIR "${CMAKE_BINARY_DIR}/needle2")
file(MAKE_DIRECTORY "${NEEDLE_DIR}")
file(DOWNLOAD
    "https://huggingface.co/Cactus-Compute/needle2/resolve/b739205bcda01f71ebf0ba180f75a885d515ad96/android-arm64/libneedle.a"
    "${NEEDLE_DIR}/libneedle.a"
    EXPECTED_HASH SHA256=93738ae3a9488cbc3104eb65bf49093683c26eb01b0d257e980e499d1d06a9f4
    TLS_VERIFY ON
    SHOW_PROGRESS
)
add_library(needle_runtime STATIC IMPORTED)
set_target_properties(needle_runtime PROPERTIES IMPORTED_LOCATION "${NEEDLE_DIR}/libneedle.a")

include(FetchContent)
FetchContent_Declare(
    stable_diffusion_cpp
    GIT_REPOSITORY https://github.com/leejet/stable-diffusion.cpp.git
    GIT_TAG ea7f0c87cfe4c673263b4c201c596c7f1cbe2528
    GIT_SUBMODULES_RECURSE TRUE
)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(stable_diffusion_cpp)

add_library(sd_jni_bridge SHARED sd_jni_bridge.cpp)

# Android 15+ refuses to install APKs whose native libraries are not built for
# 16KB memory pages ("the following libraries are not 16kb aligned"). NDK r27's
# linker still defaults to 4KB, so ask for the larger page size explicitly —
# this is what AGP's alignment check and the device installer both require.
target_link_options(sd_jni_bridge PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(sd_jni_bridge PRIVATE stable-diffusion log android)
target_include_directories(sd_jni_bridge PRIVATE ${stable_diffusion_cpp_SOURCE_DIR}/include)

add_library(needle_jni_bridge SHARED needle_jni_bridge.cpp)
target_link_options(needle_jni_bridge PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(needle_jni_bridge PRIVATE needle_runtime log android)
