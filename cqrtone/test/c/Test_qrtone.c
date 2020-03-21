/*
* BSD 3-Clause License
*
* Copyright (c) 2018, Ifsttar
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
*  Redistributions of source code must retain the above copyright notice, this
*   list of conditions and the following disclaimer.
*
*  Redistributions in binary form must reproduce the above copyright notice,
*   this list of conditions and the following disclaimer in the documentation
*   and/or other materials provided with the distribution.
*
*  Neither the name of the copyright holder nor the names of its
*   contributors may be used to endorse or promote products derived from
*   this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/


#ifdef _DEBUG
#define _CRTDBG_MAP_ALLOC
#ifdef _WIN32
#include <crtdbg.h>
#endif
#endif

#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <assert.h>
#include <stdio.h>

#include "qrtone.h"
#include "minunit.h"

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

#define CHECK(a) if(!a) return -1

MU_TEST(testPolynomial) {

		generic_gf_t field;
		qrtone_generic_gf_init(&field, 0x011D, 256, 0);
		mu_assert_int_eq(0, field.zero.coefficients[0]);

		generic_gf_poly_t poly;
		mu_assert_int_eq(QRTONE_NO_ERRORS, qrtone_generic_gf_build_monomial(&poly, 0, -1));
		mu_assert_int_eq(-1, poly.coefficients[0]);

		qrtone_generic_gf_poly_free(&poly);
		qrtone_generic_gf_free(&field);
}

MU_TEST(testZero) {

	generic_gf_t field;
	qrtone_generic_gf_init(&field, 0x011D, 256, 0);

	generic_gf_poly_t poly;
	mu_assert_int_eq(QRTONE_NO_ERRORS, qrtone_generic_gf_build_monomial(&poly, 1, 0));
	mu_assert_int_eq(field.zero.coefficients_length, poly.coefficients_length);
	mu_assert_int_eq(field.zero.coefficients[0], poly.coefficients[0]);
	qrtone_generic_gf_poly_free(&poly);

	mu_assert_int_eq(QRTONE_NO_ERRORS, qrtone_generic_gf_build_monomial(&poly, 1, 2));
	generic_gf_poly_t res;
	qrtone_generic_gf_poly_multiply(&poly,&field, 0, &res);
	mu_assert_int_eq(field.zero.coefficients_length, res.coefficients_length);
	mu_assert_int_eq(field.zero.coefficients[0], res.coefficients[0]);
	qrtone_generic_gf_poly_free(&poly);
	qrtone_generic_gf_poly_free(&res);


	qrtone_generic_gf_free(&field);
}


MU_TEST(testEvaluate) {

	generic_gf_t field;
	qrtone_generic_gf_init(&field, 0x011D, 256, 0);

	generic_gf_poly_t poly;
	mu_assert_int_eq(QRTONE_NO_ERRORS, qrtone_generic_gf_build_monomial(&poly, 0, 3));

	mu_assert_int_eq(3, qrtone_generic_gf_poly_evaluate_at(&poly, &field, 0));


	qrtone_generic_gf_poly_free(&poly);
	qrtone_generic_gf_free(&field);
}

void testEncoder(reed_solomon_encoder_t* encoder, int32_t* data_words,int32_t data_words_length, int32_t* ec_words, int32_t ec_words_length) {
	int32_t message_expected_length = data_words_length + ec_words_length;
	int32_t* message_expected = malloc(sizeof(int32_t) * message_expected_length);

	int32_t message_length = data_words_length + ec_words_length;
	int32_t* message = malloc(sizeof(int32_t) * message_length);
	memset(message, 0, sizeof(int32_t) * message_length);
	memcpy(message_expected, data_words, sizeof(int32_t) * data_words_length);
	memcpy(message_expected + data_words_length, ec_words, sizeof(int32_t) * ec_words_length);
	memcpy(message, data_words, sizeof(int32_t) * data_words_length);

	qrtone_reed_solomon_encoder_encode(encoder, message, message_length, ec_words_length);

	mu_assert_int_array_eq(message_expected, message_expected_length, message, message_length);
	
	free(message_expected);
	free(message);
}

MU_TEST(testDataMatrix) {

	reed_solomon_encoder_t encoder;
	qrtone_reed_solomon_encoder_init(&encoder, 0x012D, 256, 1);
	int32_t data_words[3] = { 142, 164, 186 };
	int32_t ec_words[5] = { 114, 25 , 5, 88, 102};

	testEncoder(&encoder, data_words, 3, ec_words, 5);

	qrtone_reed_solomon_encoder_free(&encoder);
}

MU_TEST_SUITE(test_suite) {
	MU_RUN_TEST(testEvaluate);
	MU_RUN_TEST(testPolynomial);
	MU_RUN_TEST(testZero);
	MU_RUN_TEST(testDataMatrix);
}

int main(int argc, char** argv) {
	MU_RUN_SUITE(test_suite);
	MU_REPORT();
	#ifdef _WIN32
	#ifdef _DEBUG
		_CrtDumpMemoryLeaks(); //Display memory leaks
	#endif
	#endif
	return minunit_status == 1 || minunit_fail > 0 ? -1 : 0;
}
