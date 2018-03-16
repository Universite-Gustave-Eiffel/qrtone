cmake_minimum_required (VERSION 3.0)

include (CheckIncludeFile)
include (CheckFunctionExists)
include (CheckLibraryExists)


check_function_exists (memset HAVE_MEMSET)
check_function_exists (memmove HAVE_MEMMOVE)
check_library_exists (c malloc "" HAVE_LIBC)

include_directories (main/c/)
include_directories (main/c/libcorrect/include)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_USE_MATH_DEFINES")

add_library(openwarble main/c/warble.c main/c/warble_complex.c)

target_link_libraries (openwarble correct m)

#------------#
#    TEST 1
#------------#

set(TEST_DATA_DIR "${PROJECT_BINARY_DIR}/TestAudio")

file(COPY test/resources/c/audioTest_44100_16bitsPCM_0.0872s_1760.raw 
    DESTINATION ${TEST_DATA_DIR})

# Test executable and folder
set(WARBLE_TESTS
    test/c/Test_warble.c)


add_executable(Test_WARBLE ${WARBLE_TESTS})

target_link_libraries (Test_WARBLE
    openwarble correct)

set_property(TARGET Test_WARBLE PROPERTY FOLDER "tests")

# Add to test suite
enable_testing()
add_test( NAME warble_test1
    WORKING_DIRECTORY ${TEST_DATA_DIR}
    COMMAND Test_WARBLE )