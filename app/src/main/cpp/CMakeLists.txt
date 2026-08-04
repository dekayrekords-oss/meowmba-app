cmake_minimum_required(VERSION 3.22)
project(sdcpp_bridge)

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
