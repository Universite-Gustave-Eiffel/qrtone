cmake_minimum_required (VERSION 2.8)

include (CheckIncludeFile)
include (CheckFunctionExists)
include (CheckLibraryExists)

if(MSVC)
set(LIBM "")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /W4")
else(MSVC)
set(LIBM "m")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC -std=c99 -pedantic -Wall")
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

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_USE_MATH_DEFINES")

add_library(qrtone src/reed_solomon.c "src/qrtone.c")

target_link_libraries (qrtone ${LIBM})

#------------#
#    TEST 1
#------------#
set(TEST_DATA_DIR "${PROJECT_BINARY_DIR}/TestAudio")

file(MAKE_DIRECTORY ${TEST_DATA_DIR})

# Test executable and folder
set(ECC_TESTS
    test/c/Test_reed_solomon.c)


add_executable(Test_REED_SOLOMON ${ECC_TESTS})

target_link_libraries (Test_REED_SOLOMON
qrtone )

set_property(TARGET Test_REED_SOLOMON PROPERTY FOLDER "tests")

# Add to test suite
enable_testing()
add_test( NAME ecc_test1
    WORKING_DIRECTORY ${TEST_DATA_DIR}
    COMMAND Test_REED_SOLOMON )
    
#------------#
#    TEST 2
#------------#

#file(COPY test/resources/c/audioTest_44100_16bitsPCM_0.0872s_1760.raw
#    DESTINATION ${TEST_DATA_DIR})

# Test executable and folder
set(QRTONE_TESTS
    test/c/test_qrtone.c)


add_executable(Test_QRTONE ${QRTONE_TESTS})

target_link_libraries (Test_QRTONE
qrtone )

set_property(TARGET Test_QRTONE PROPERTY FOLDER "tests")

# Add to test suite

add_test( NAME qrtone_test1
    WORKING_DIRECTORY ${TEST_DATA_DIR}
    COMMAND Test_QRTONE )
