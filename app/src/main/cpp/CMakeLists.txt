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
target_link_libraries(sd_jni_bridge PRIVATE stable-diffusion log android)
target_include_directories(sd_jni_bridge PRIVATE ${stable_diffusion_cpp_SOURCE_DIR}/include)
