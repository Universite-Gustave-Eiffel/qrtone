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
 

/**
 * @file reed_solomon.h
 * @author Nicolas Fortin @NicolasCumu
 * @author Sean Owen (java version)
 * @author David Olivier (java version)
 * @date 24/03/2020
 * @brief Api for generic Reed-Solomon ECC
 * Reference algorithm is the ZXing QR-Code Apache License source code.
 * @link https://github.com/zxing/zxing/tree/master/core/src/main/java/com/google/zxing/common/reedsolomon
 */

#ifndef ECC_H
#define ECC_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

enum ECC_ERROR_CODES { ECC_NO_ERRORS = 0, ECC_ILLEGAL_ARGUMENT = 1, ECC_DIVIDE_BY_ZERO = 2, ECC_REED_SOLOMON_ERROR
 = 3, ECC_ILLEGAL_STATE_EXCEPTION = 4};

typedef struct _ecc_generic_gf_poly_t {
	int32_t* coefficients;             /**< coefficients as ints representing elements of GF(size), arranged from most significant (highest-power term) coefficient to least significant */
	int32_t coefficients_length;      /**< Length of message attached to distance*/
} ecc_generic_gf_poly_t;

typedef struct _ecc_generic_gf_t {
	int32_t* exp_table;
	int32_t* log_table;
	int32_t size;
    int32_t primitive;
    int32_t generator_base;
	ecc_generic_gf_poly_t zero;
	ecc_generic_gf_poly_t one;
} ecc_generic_gf_t;

typedef struct _ecc_reed_solomon_cached_generator_t {
    struct _ecc_reed_solomon_cached_generator_t* previous;
    int32_t index;
    ecc_generic_gf_poly_t* value;
} ecc_reed_solomon_cached_generator_t;

typedef struct _ecc_reed_solomon_encoder_t {
    ecc_generic_gf_t field;
    ecc_reed_solomon_cached_generator_t* cached_generators;
} ecc_reed_solomon_encoder_t;

void ecc_generic_gf_poly_init(ecc_generic_gf_poly_t* this, int* coefficients, int coefficients_length);

void ecc_generic_gf_init(ecc_generic_gf_t* this, int32_t primitive, int32_t size, int32_t b);

void ecc_generic_gf_free(ecc_generic_gf_t* this);

void ecc_generic_gf_poly_free(ecc_generic_gf_poly_t* this);

int ecc_generic_gf_build_monomial(ecc_generic_gf_poly_t* poly, int32_t degree, int32_t coefficient);

void ecc_generic_gf_poly_multiply(ecc_generic_gf_poly_t* this, ecc_generic_gf_t* field, int32_t scalar, ecc_generic_gf_poly_t* result);

int ecc_generic_gf_poly_is_zero(ecc_generic_gf_poly_t* this);

int32_t ecc_generic_gf_poly_get_coefficient(ecc_generic_gf_poly_t* this, int32_t degree);

int32_t ecc_generic_gf_inverse(ecc_generic_gf_t* this, int32_t a);

void ecc_generic_gf_poly_add_or_substract(ecc_generic_gf_poly_t* this, ecc_generic_gf_poly_t* other, ecc_generic_gf_poly_t* result);

int32_t ecc_generic_gf_poly_get_degree(ecc_generic_gf_poly_t* this);

void ecc_generic_gf_poly_multiply_other(ecc_generic_gf_poly_t* this, ecc_generic_gf_t* field, ecc_generic_gf_poly_t* other, ecc_generic_gf_poly_t* result);

/**
 * @return product of a and b in GF(size)
 */
int32_t ecc_generic_gf_multiply(ecc_generic_gf_t* this, int32_t a, int32_t b);

/**
 * @return evaluation of this polynomial at a given point
 */
int32_t ecc_generic_gf_poly_evaluate_at(ecc_generic_gf_poly_t* this, ecc_generic_gf_t* field, int32_t a);

void ecc_reed_solomon_encoder_free(ecc_reed_solomon_encoder_t* this);

void ecc_reed_solomon_encoder_init(ecc_reed_solomon_encoder_t* this, int32_t primitive, int32_t size, int32_t b);

void ecc_reed_solomon_encoder_encode(ecc_reed_solomon_encoder_t* this, int32_t* to_encode, int32_t to_encode_length, int32_t ec_bytes);

/**
 * @return ecc_ERROR_CODES
 */
int32_t ecc_reed_solomon_decoder_decode(ecc_generic_gf_t* field, int32_t* to_decode, int32_t to_decode_length, int32_t ec_bytes, int32_t* fixedErrors);







#ifdef __cplusplus
}
#endif

#endif