include_directories (src/main/c/)

cmake_minimum_required (VERSION 2.6)

project (openwarble)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DCHECK_OVERFLOW -Dkiss_fft_scalar=double")

add_library(openwarble src/main/c/warble.c src/main/c/kiss_fft.c)

#------------#
#    TEST 1
#------------#

# Data files
set(TEST_DATA_DIR "${PROJECT_BINARY_DIR}/TestAudio")

# Test executable and folder
set(YIN_TESTS
    src/test/c/Test_FFT.c)


add_executable(Test_FFT ${YIN_TESTS})

target_link_libraries (Test_FFT
    openwarble)

set_property(TARGET Test_FFT PROPERTY FOLDER "tests")

# Add to test suite
enable_testing()
add_test( NAME warble_test1
    WORKING_DIRECTORY ${TEST_DATA_DIR}
    COMMAND Test_FFT )