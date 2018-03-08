cmake_minimum_required (VERSION 2.8)

project (openwarble)

add_subdirectory (src/main/c/libcorrect)

add_subdirectory (src)

enable_testing()