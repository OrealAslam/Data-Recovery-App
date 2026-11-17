cmake_minimum_required(VERSION 3.18.1)

# Set the project name.
project(appmodules)

# 1. Find the log library using a safe variable name
find_library(LOG_LIB log)

# 2. Include React Native's official CMake utilities.
include(${REACT_ANDROID_DIR}/cmake-utils/ReactNative-application.cmake)

# 3. Link the log library to React Native's appmodules
target_link_libraries(appmodules ${LOG_LIB})

# 4. Define your custom shared library using the file from the new jni folder
add_library(nativecarver SHARED ${CMAKE_CURRENT_SOURCE_DIR}/../jni/native-carver.cpp)

# 5. Link your custom library against React Native core and the log library
target_link_libraries(
    nativecarver
    ReactAndroid::reactnative
    ReactAndroid::jsi
    fbjni::fbjni
    ${LOG_LIB}
)

target_compile_options(nativecarver PRIVATE -std=c++17 -Wall -Wextra)