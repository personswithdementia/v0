cmake_minimum_required(VERSION 3.22.1)

project(ongoma)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(external/oboe)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/external/oboe/include
)

set(SOURCES
    src/main/cpp/SimpleAudioEngine.cpp
    src/main/cpp/SimpleJNIBridge.cpp
    src/main/cpp/AudioEngineTest.cpp
)
add_library(${CMAKE_PROJECT_NAME} SHARED ${SOURCES})

target_link_libraries(${CMAKE_PROJECT_NAME}
    log
    oboe
)
target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    ANDROID=1
)
