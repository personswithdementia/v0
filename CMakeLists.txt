cmake_minimum_required(VERSION 3.22.1)

project(ongoma)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add Oboe library
add_subdirectory(external/oboe)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/external/oboe/include
)

# Source files
set(SOURCES
    src/main/cpp/SimpleAudioEngine.cpp
    src/main/cpp/SimpleJNIBridge.cpp
)

# Create shared library
add_library(${CMAKE_PROJECT_NAME} SHARED ${SOURCES})

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
    log
    oboe
)

# Android specific
target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    ANDROID=1
)
