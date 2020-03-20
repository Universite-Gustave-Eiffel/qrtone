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
 
#include "qrtone.h"
#include <stdlib.h>
#include <string.h>

/**
 * GenericGFPoly constructor
 * @ref com.google.zxing.common.reedsolomon
 * @author Sean Owen (java version)
 * @author David Olivier (java version)
 */
 void qrtone_generic_gf_poly_init(generic_gf_poly_t* this, int* coefficients, int coefficients_length) {
    if (coefficients_length == 0) {
      return;
    }
    if (coefficients_length > 1 && coefficients[0] == 0) {
      // Leading term must be non-zero for anything except the constant polynomial "0"
      int firstNonZero = 1;
      while (firstNonZero < coefficients_length && coefficients[firstNonZero] == 0) {
        firstNonZero++;
      }
      if (firstNonZero == coefficients_length) {
          this->coefficients = malloc(sizeof(int32_t) * 1);
          this->coefficients_length = 1;
          this->coefficients[0] = 0;
      } else {
        this->coefficients_length = coefficients_length - firstNonZero;
        this->coefficients = malloc(sizeof(int32_t) * this->coefficients_length);
        memcpy(this->coefficients + firstNonZero, coefficients, sizeof(int32_t) * this->coefficients_length);
      }
    } else {
        this->coefficients = malloc(sizeof(int32_t) * 1);
        this->coefficients_length = 1;
        this->coefficients[0] = coefficients[0];
    }	 
 }

 void qrtone_generic_gf_poly_copy(generic_gf_poly_t* this, generic_gf_poly_t* other) {
     this->coefficients = malloc(sizeof(int32_t) * other->coefficients_length);
     this->coefficients_length = other->coefficients_length;
 }

 void qrtone_generic_gf_poly_free(generic_gf_poly_t* this) {
     free(this->coefficients);
 }

 void qrtone_generic_gf_init(generic_gf_t* this, int32_t primitive, int32_t size, int32_t b) {
     this->primitive = primitive;
     this->size = size;
     this->generator_base = b;
     this->exp_table = malloc(sizeof(int32_t) * size);
     this->log_table = malloc(sizeof(int32_t) * size);
     memset(this->log_table, 0, sizeof(int32_t) * size);
     int32_t x = 1;
     int32_t i;
     for (i = 0; i < size; i++) {
         this->exp_table[i] = x;
         x *= 2; // we're assuming the generator alpha is 2
         if (x >= size) {
             x ^= primitive;
             x &= size - 1;
         }
     }
     for (i = 0; i < size - 1; i++) {
         this->log_table[this->exp_table[i]] = i;
     }
     // logTable[0] == 0 but this should never be used
     int zero[1] = { 0 };
     qrtone_generic_gf_poly_init(&(this->zero), zero, 1);
     int one[1] = { 1 };
     qrtone_generic_gf_poly_init(&(this->one), one, 1);
 }

 void qrtone_generic_gf_free(generic_gf_t* this) {
     free(this->exp_table);
     free(this->log_table);
     qrtone_generic_gf_poly_free(&(this->zero));
     qrtone_generic_gf_poly_free(&(this->one));
 }

 int qrtone_generic_gf_build_monomial(generic_gf_t* this, generic_gf_poly_t* poly, int32_t degree, int32_t coefficient) {
     if (degree < 0) {
         return QRTONE_ILLEGAL_ARGUMENT;
     }
     if (coefficient == 0) {
         int32_t zero[1] = { 0 };
         qrtone_generic_gf_poly_init(poly, zero, 1);
         return QRTONE_NO_ERRORS;
     }
     int32_t* coefficients = malloc(sizeof(int32_t) * ((size_t)degree + 1));
     memset(coefficients, 0, sizeof(int32_t) * ((size_t)degree + 1));
     coefficients[0] = coefficient;
     qrtone_generic_gf_poly_init(poly, coefficients, degree + 1);
     free(coefficients);
     return QRTONE_NO_ERRORS;
 }


 void qrtone_generic_gf_poly_multiply(generic_gf_poly_t* this, generic_gf_t* field, int32_t scalar, generic_gf_poly_t* result) {
     if (scalar == 0) {
         int32_t zero[1] = { 0 };
         qrtone_generic_gf_poly_init(result, zero, 1);
         return;
     }
     if (scalar == 1) {
         qrtone_generic_gf_poly_copy(result, this);
         return;
     }
     int32_t i;
     int32_t* product = malloc(sizeof(int32_t) * this->coefficients_length);
     for (i = 0; i < this->coefficients_length; i++) {
         product[i] = qrtone_generic_gf_multiply(field, this->coefficients[i], scalar);
     }
     qrtone_generic_gf_poly_init(result, product, this->coefficients_length);
     free(product);
 }

 int qrtone_generic_gf_multiply(generic_gf_t* this, int32_t a, int32_t b) {
     if (a == 0 || b == 0) {
         return 0;
     }
     return this->exp_table[(this->log_table[a] + this->log_table[b]) % (this->size - 1)];
 }

 int32_t qrtone_generic_gf_poly_get_coefficient(generic_gf_poly_t* this, int32_t degree) {
     return this->coefficients[this->coefficients_length - 1 - degree];
 }

 int32_t qrtone_generic_gf_add_or_substract(int32_t a, int32_t b) {
     return a ^ b;
 }

 int32_t qrtone_generic_gf_poly_evaluate_at(generic_gf_poly_t* this, generic_gf_t* field, int32_t a) {
     if (a == 0) {
         // Just return the x^0 coefficient
         return qrtone_generic_gf_poly_get_coefficient(this, 0);
     }
     if (a == 1) {
         // Just the sum of the coefficients
         int32_t result = 0;
         int32_t i;
         for (i = 0; i < this->coefficients_length; i++) {
             result = qrtone_generic_gf_add_or_substract(result, this->coefficients[i]);
         }
         return result;
     }
     int32_t result = this->coefficients[0];
     int32_t i;
     for (i = 1; i < this->coefficients_length; i++) {
         result = qrtone_generic_gf_add_or_substract(qrtone_generic_gf_multiply(field, a, result), this->coefficients[i]);
     }
     return result;
 }




