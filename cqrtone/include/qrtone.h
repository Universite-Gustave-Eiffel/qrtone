/*
 * BSD 3-Clause License
 *
 * Copyright (c) Unité Mixte de Recherche en Acoustique Environnementale (univ-gustave-eiffel)
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
 
 
#ifndef QRTONE_H
#define QRTONE_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

enum QRTONE_ERROR_CODES { QRTONE_NO_ERRORS = 0, QRTONE_ILLEGAL_ARGUMENT = 1, QRTONE_DIVIDE_BY_ZERO = 2, QRTONE_REED_SOLOMON_ERROR
 = 3, QRTONE_ILLEGAL_STATE_EXCEPTION = 4};

typedef struct _generic_gf_poly_t {
	int32_t* coefficients;             /**< coefficients as ints representing elements of GF(size), arranged from most significant (highest-power term) coefficient to least significant */
	int32_t coefficients_length;      /**< Length of message attached to distance*/
} generic_gf_poly_t;

typedef struct _generic_gf_t {
	int32_t* exp_table;
	int32_t* log_table;
	int32_t size;
    int32_t primitive;
    int32_t generator_base;
	generic_gf_poly_t zero;
	generic_gf_poly_t one;
} generic_gf_t;

typedef struct _reed_solomon_cached_generator_t {
    struct _reed_solomon_cached_generator_t* previous;
    int32_t index;
    generic_gf_poly_t* value;
} reed_solomon_cached_generator_t;

typedef struct _reed_solomon_encoder_t {
    generic_gf_t field;
    reed_solomon_cached_generator_t* cached_generators;
} reed_solomon_encoder_t;

void qrtone_generic_gf_poly_init(generic_gf_poly_t* this, int* coefficients, int coefficients_length);

void qrtone_generic_gf_init(generic_gf_t* this, int32_t primitive, int32_t size, int32_t b);

void qrtone_generic_gf_free(generic_gf_t* this);

void qrtone_generic_gf_poly_free(generic_gf_poly_t* this);

int qrtone_generic_gf_build_monomial(generic_gf_poly_t* poly, int32_t degree, int32_t coefficient);

void qrtone_generic_gf_poly_multiply(generic_gf_poly_t* this, generic_gf_t* field, int32_t scalar, generic_gf_poly_t* result);

void qrtone_generic_gf_poly_copy(generic_gf_poly_t* this, generic_gf_poly_t* other);

int qrtone_generic_gf_poly_is_zero(generic_gf_poly_t* this);

int32_t qrtone_generic_gf_poly_get_coefficient(generic_gf_poly_t* this, int32_t degree);

int32_t qrtone_generic_gf_inverse(generic_gf_t* this, int32_t a);

void qrtone_generic_gf_poly_add_or_substract(generic_gf_poly_t* this, generic_gf_poly_t* other, generic_gf_poly_t* result);

int32_t qrtone_generic_gf_poly_get_degree(generic_gf_poly_t* this);

void qrtone_generic_gf_poly_multiply_other(generic_gf_poly_t* this, generic_gf_t* field, generic_gf_poly_t* other, generic_gf_poly_t* result);

/**
 * @return product of a and b in GF(size)
 */
int32_t qrtone_generic_gf_multiply(generic_gf_t* this, int32_t a, int32_t b);

/**
 * @return evaluation of this polynomial at a given point
 */
int32_t qrtone_generic_gf_poly_evaluate_at(generic_gf_poly_t* this, generic_gf_t* field, int32_t a);

void qrtone_reed_solomon_encoder_free(reed_solomon_encoder_t* this);

void qrtone_reed_solomon_encoder_init(reed_solomon_encoder_t* this, int32_t primitive, int32_t size, int32_t b);

void qrtone_reed_solomon_encoder_encode(reed_solomon_encoder_t* this, int32_t* to_encode, int32_t to_encode_length, int32_t ec_bytes);

/**
 * @return QRTONE_ERROR_CODES
 */
int32_t qrtone_reed_solomon_decoder_decode(generic_gf_t* field, int32_t* to_decode, int32_t to_decode_length, int32_t ec_bytes);







#ifdef __cplusplus
}
#endif

#endif