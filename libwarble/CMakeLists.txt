cmake_minimum_required (VERSION 2.8)

include (CheckIncludeFile)
include (CheckFunctionExists)
include (CheckLibraryExists)

if(MSVC)
set(LIBM "")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /W4")
else(MSVC)
set(LIBM "m")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC -std=c99 -Wpedantic -Wall")
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -g3 -O0 -march=native -fsanitize=address")
    #set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-no_pie,")
else()
  if("${CMAKE_C_COMPILER_ID}" STREQUAL "Clang")
      set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2")
    else()
      set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2")
  endif()
  if(CMAKE_BUILD_TYPE STREQUAL "Profiling")
      set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -g3")
  endif()
endif()
endif(MSVC)

include_directories (include)
include_directories (../libcorrect/include)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_USE_MATH_DEFINES")

add_library(openwarble src/warble.c src/warble_complex.c)

target_link_libraries (openwarble correct_static ${LIBM})

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
openwarble correct_static)

set_property(TARGET Test_WARBLE PROPERTY FOLDER "tests")

# Add to test suite
enable_testing()
add_test( NAME warble_test1
    WORKING_DIRECTORY ${TEST_DATA_DIR}
    COMMAND Test_WARBLE )
