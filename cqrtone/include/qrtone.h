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

enum QRTONE_ERROR_CODES { QRTONE_NO_ERRORS = 0, QRTONE_ILLEGAL_ARGUMENT = 1};

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

void qrtone_generic_gf_poly_init(generic_gf_poly_t* this, int* coefficients, int coefficients_length);

void qrtone_generic_gf_init(generic_gf_t* this, int32_t primitive, int32_t size, int32_t b);

void qrtone_generic_gf_free(generic_gf_t* this);

void qrtone_generic_gf_poly_free(generic_gf_poly_t* this);

int qrtone_generic_gf_build_monomial(generic_gf_t* this, generic_gf_poly_t* poly, int32_t degree, int32_t coefficient);

void qrtone_generic_gf_poly_multiply(generic_gf_poly_t* this, generic_gf_t* field, int32_t scalar, generic_gf_poly_t* result);

/**
 * @return product of a and b in GF(size)
 */
int32_t qrtone_generic_gf_multiply(generic_gf_t* this, int32_t a, int32_t b);
















#ifdef __cplusplus
}
#endif

#endif