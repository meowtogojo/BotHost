cmake_minimum_required(VERSION 3.22.1)
project(bothost_native)

add_library(native-lib SHARED native-lib.cpp)

# The Node.js Mobile release package must be extracted into app/libnode:
#   app/libnode/include/node/node.h
#   app/libnode/bin/arm64-v8a/libnode.so
#   app/libnode/bin/x86_64/libnode.so
if(EXISTS "${CMAKE_SOURCE_DIR}/../../../../libnode/include/node/node.h")
    include_directories("${CMAKE_SOURCE_DIR}/../../../../libnode/include/node")
    add_library(node SHARED IMPORTED)
    set_target_properties(node PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../../../../libnode/bin/${ANDROID_ABI}/libnode.so")
    target_link_libraries(native-lib node)
endif()

find_library(log-lib log)
target_link_libraries(native-lib ${log-lib})
