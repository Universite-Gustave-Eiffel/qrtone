include_directories (src/main/c/)

cmake_minimum_required (VERSION 2.6)

project (openwarble)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_USE_MATH_DEFINES")

add_library(openwarble src/main/c/warble.c)

#------------#
#    TEST 1
#------------#

# Data files
set(TEST_DATA_DIR "${PROJECT_BINARY_DIR}/TestAudio")

# Test executable and folder
set(WARBLE_TESTS
    src/test/c/Test_warble.c)


add_executable(Test_WARBLE ${WARBLE_TESTS})

target_link_libraries (Test_WARBLE
    openwarble)

set_property(TARGET Test_WARBLE PROPERTY FOLDER "tests")

# Add to test suite
enable_testing()
add_test( NAME warble_test1
    WORKING_DIRECTORY ${TEST_DATA_DIR}
    COMMAND Test_WARBLE )