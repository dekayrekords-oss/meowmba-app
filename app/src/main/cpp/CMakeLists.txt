cmake_minimum_required(VERSION 3.22)
project(sdcpp_bridge)

# Needle ships a small, official Android arm64 static runtime. Pin both its
# release URL and digest: configure fails closed if upstream bytes ever change.
set(NEEDLE_SHA256 "2a5286cd8d4d750f8ee6eb885f76fd57aacee5daefa2e4e378bcfe583e7e999e")
set(NEEDLE_LIBRARY "" CACHE FILEPATH "Path to the pinned Android arm64 Needle archive")
if(NEEDLE_LIBRARY)
    if(NOT EXISTS "${NEEDLE_LIBRARY}")
        message(FATAL_ERROR "Needle cache does not exist: ${NEEDLE_LIBRARY}")
    endif()
    file(SHA256 "${NEEDLE_LIBRARY}" NEEDLE_ACTUAL_SHA256)
    if(NOT NEEDLE_ACTUAL_SHA256 STREQUAL NEEDLE_SHA256)
        message(FATAL_ERROR "Needle cache SHA-256 mismatch; expected ${NEEDLE_SHA256}, got ${NEEDLE_ACTUAL_SHA256}")
    endif()
else()
    set(NEEDLE_DIR "${CMAKE_BINARY_DIR}/needle3")
    file(MAKE_DIRECTORY "${NEEDLE_DIR}")
    set(NEEDLE_LIBRARY "${NEEDLE_DIR}/libneedle.a")
    set(NEEDLE_ACTUAL_SHA256 "")
    if(EXISTS "${NEEDLE_LIBRARY}")
        file(SHA256 "${NEEDLE_LIBRARY}" NEEDLE_ACTUAL_SHA256)
    endif()
    if(NOT NEEDLE_ACTUAL_SHA256 STREQUAL NEEDLE_SHA256)
        # Download to a temporary file so an interrupted connection cannot
        # truncate a usable cached archive. Never relax the pinned digest.
        file(DOWNLOAD
            "https://huggingface.co/Cactus-Compute/needle3/resolve/c1fc4d4cb32993156a880ceb8ff171b03b1f166a/android-arm64/libneedle.a"
            "${NEEDLE_LIBRARY}.part"
            TLS_VERIFY ON TIMEOUT 120 INACTIVITY_TIMEOUT 20
            STATUS NEEDLE_DOWNLOAD_STATUS SHOW_PROGRESS)
        list(GET NEEDLE_DOWNLOAD_STATUS 0 NEEDLE_DOWNLOAD_CODE)
        if(NOT NEEDLE_DOWNLOAD_CODE EQUAL 0)
            file(REMOVE "${NEEDLE_LIBRARY}.part")
            message(FATAL_ERROR "Needle download failed: ${NEEDLE_DOWNLOAD_STATUS}. Retry with network access or -PnativeNeedleLibrary=/verified/path/libneedle.a")
        endif()
        file(SHA256 "${NEEDLE_LIBRARY}.part" NEEDLE_ACTUAL_SHA256)
        if(NOT NEEDLE_ACTUAL_SHA256 STREQUAL NEEDLE_SHA256)
            file(REMOVE "${NEEDLE_LIBRARY}.part")
            message(FATAL_ERROR "Downloaded Needle archive failed SHA-256 verification")
        endif()
        file(RENAME "${NEEDLE_LIBRARY}.part" "${NEEDLE_LIBRARY}")
    endif()
endif()
add_library(needle_runtime STATIC IMPORTED)
set_target_properties(needle_runtime PROPERTIES IMPORTED_LOCATION "${NEEDLE_LIBRARY}")

include(FetchContent)
set(SD_PIN "ea7f0c87cfe4c673263b4c201c596c7f1cbe2528")
if(FETCHCONTENT_SOURCE_DIR_STABLE_DIFFUSION_CPP)
    find_package(Git REQUIRED)
    execute_process(COMMAND "${GIT_EXECUTABLE}" -C "${FETCHCONTENT_SOURCE_DIR_STABLE_DIFFUSION_CPP}" rev-parse HEAD
        OUTPUT_VARIABLE SD_CACHE_HEAD OUTPUT_STRIP_TRAILING_WHITESPACE RESULT_VARIABLE SD_GIT_RESULT)
    if(NOT SD_GIT_RESULT EQUAL 0 OR NOT SD_CACHE_HEAD STREQUAL SD_PIN)
        message(FATAL_ERROR "Stable Diffusion cache must be a Git checkout at ${SD_PIN}")
    endif()
    execute_process(COMMAND "${GIT_EXECUTABLE}" -C "${FETCHCONTENT_SOURCE_DIR_STABLE_DIFFUSION_CPP}" submodule status --recursive
        OUTPUT_VARIABLE SD_SUBMODULES RESULT_VARIABLE SD_GIT_RESULT)
    if(NOT SD_GIT_RESULT EQUAL 0 OR SD_SUBMODULES MATCHES "(^|\n)[-+U]")
        message(FATAL_ERROR "Stable Diffusion cache has missing or mismatched submodules")
    endif()
    execute_process(COMMAND "${GIT_EXECUTABLE}" -C "${FETCHCONTENT_SOURCE_DIR_STABLE_DIFFUSION_CPP}" status --porcelain --untracked-files=normal
        OUTPUT_VARIABLE SD_CACHE_CHANGES OUTPUT_STRIP_TRAILING_WHITESPACE RESULT_VARIABLE SD_GIT_RESULT)
    if(NOT SD_GIT_RESULT EQUAL 0 OR SD_CACHE_CHANGES)
        message(FATAL_ERROR "Stable Diffusion cache must be clean, including its submodules")
    endif()
endif()
FetchContent_Declare(
    stable_diffusion_cpp
    GIT_REPOSITORY https://github.com/leejet/stable-diffusion.cpp.git
    GIT_TAG ${SD_PIN}
    GIT_SUBMODULES_RECURSE TRUE
)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(stable_diffusion_cpp)

add_library(sd_jni_bridge SHARED sd_jni_bridge.cpp)

# Android devices using 16 KB memory pages need compatible ELF segments.
# NDK r27 defaults to 4 KB; request 16 KB explicitly for these bridges.
# Prebuilt dependencies must also be checked in the finished bundle.
target_link_options(sd_jni_bridge PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(sd_jni_bridge PRIVATE stable-diffusion log android)
target_include_directories(sd_jni_bridge PRIVATE ${stable_diffusion_cpp_SOURCE_DIR}/include)

add_library(needle_jni_bridge SHARED needle_jni_bridge.cpp)
target_link_options(needle_jni_bridge PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(needle_jni_bridge PRIVATE needle_runtime log android)
