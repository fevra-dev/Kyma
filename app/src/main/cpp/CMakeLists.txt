# Kyma ggwave JNI
# Builds ggwave C++ library and JNI bridge for data-over-sound encode/decode.
cmake_minimum_required(VERSION 3.22)
project(ggwave_jni)

# Suppress ggwave CMake policy warnings (CMP0077)
if(POLICY CMP0077)
  cmake_policy(SET CMP0077 NEW)
endif()

# Disable ggwave standalone options (no tests, no examples)
set(GGWAVE_BUILD_TESTS OFF)
set(GGWAVE_BUILD_EXAMPLES OFF)
set(GGWAVE_STANDALONE OFF)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "ggwave as static lib" FORCE)

# Add ggwave as subdirectory - builds ggwave static lib
add_subdirectory(ggwave ${CMAKE_BINARY_DIR}/ggwave)

# JNI shared library — ggwave data-over-sound
add_library(ggwave_jni SHARED ggwave_jni.cpp)
target_include_directories(ggwave_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/ggwave/include
)
target_link_libraries(ggwave_jni
    ggwave
    android
    log
)

# JNI shared library — secure crypto (guaranteed memory wiping via volatile memset)
add_library(secure_crypto_jni SHARED secure_crypto_jni.cpp)
target_link_libraries(secure_crypto_jni
    android
    log
)
