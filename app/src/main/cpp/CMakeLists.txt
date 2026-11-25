cmake_minimum_required(VERSION 3.22.1)
project(phantom_native LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android's toolchain must configure every dependency. ExternalProject_Add commonly
# creates a second host build and is intentionally not used here.
option(PHANTOM_WITH_LLAMA "Build llama.cpp from external/llama.cpp" OFF)
option(PHANTOM_WITH_WHISPER "Build whisper.cpp from external/whisper.cpp" OFF)

add_library(phantom_native SHARED phantom_native.cpp)
find_library(log-lib log)
target_link_libraries(phantom_native PRIVATE ${log-lib})

if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(phantom_native PRIVATE -O3)
endif()

if(PHANTOM_WITH_LLAMA)
    if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/external/llama.cpp/CMakeLists.txt")
        message(FATAL_ERROR "PHANTOM_WITH_LLAMA=ON but external/llama.cpp is missing. See docs/native-build.md.")
    endif()
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    add_subdirectory(external/llama.cpp)
    target_compile_definitions(phantom_native PRIVATE PHANTOM_WITH_LLAMA=1)
    target_link_libraries(phantom_native PRIVATE llama)
endif()

if(PHANTOM_WITH_WHISPER)
    if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/external/whisper.cpp/CMakeLists.txt")
        message(FATAL_ERROR "PHANTOM_WITH_WHISPER=ON but external/whisper.cpp is missing. See docs/native-build.md.")
    endif()
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    add_subdirectory(external/whisper.cpp)
    target_link_libraries(phantom_native PRIVATE whisper)
endif()

# Piper is not a single self-contained Android target: it requires ONNX Runtime,
# espeak-ng data and voice assets. It remains a separately versioned adapter.
