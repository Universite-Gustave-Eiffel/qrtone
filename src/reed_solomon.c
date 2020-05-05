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

#ifdef _DEBUG
#define _CRTDBG_MAP_ALLOC
#ifdef _WIN32
#include <crtdbg.h>
#endif
#endif

#include "reed_solomon.h"
#include <stdlib.h>
#include <string.h>

// @link https://github.com/zxing/zxing/tree/master/core/src/main/java/com/google/zxing/common/reedsolomon


/**
 * @file reed_solomon.c
 * @author Nicolas Fortin @NicolasCumu
 * @author Sean Owen (java version)
 * @author David Olivier (java version)
 * @date 24/03/2020
 * @brief Implementation of Reed-Solomon ECC
 * Reference algorithm is the ZXing QR-Code Apache License source code.
 */

void ecc_generic_gf_poly_copy(ecc_generic_gf_poly_t* self, ecc_generic_gf_poly_t* other) {
    self->coefficients = malloc(sizeof(int32_t) * other->coefficients_length);
    memcpy(self->coefficients, other->coefficients, other->coefficients_length * sizeof(int32_t));
    self->coefficients_length = other->coefficients_length;
}

/**
 * GenericGFPoly constructor
 * @ref com.google.zxing.common.reedsolomon
 * @author Sean Owen (java version)
 * @author David Olivier (java version)
 */
 void ecc_generic_gf_poly_init(ecc_generic_gf_poly_t* self, int32_t* coefficients, int32_t coefficients_length) {
    if (coefficients_length == 0) {
      return;
    }
    if (coefficients_length > 1 && coefficients[0] == 0) {
      // Leading term must be non-zero for anything except the constant polynomial "0"
      int32_t firstNonZero = 1;
      while (firstNonZero < coefficients_length && coefficients[firstNonZero] == 0) {
        firstNonZero++;
      }
      if (firstNonZero == coefficients_length) {
          self->coefficients = malloc(sizeof(int32_t) * 1);
          self->coefficients_length = 1;
          self->coefficients[0] = 0;
      } else {
        self->coefficients_length = coefficients_length - firstNonZero;
        self->coefficients = malloc(sizeof(int32_t) * self->coefficients_length);
        memcpy(self->coefficients, coefficients + firstNonZero, sizeof(int32_t) * self->coefficients_length);
      }
    } else {
        self->coefficients = malloc(sizeof(int32_t) * coefficients_length);
        self->coefficients_length = coefficients_length;
        memcpy(self->coefficients, coefficients, sizeof(int32_t) * coefficients_length);
    }	 
 }

 int32_t ecc_generic_gf_poly_multiply_by_monomial(ecc_generic_gf_poly_t* self, ecc_generic_gf_t* field, int32_t degree, int32_t coefficient, ecc_generic_gf_poly_t* result) {
     if (degree < 0) {
         return ECC_ILLEGAL_ARGUMENT;
     }
     if (coefficient == 0) {
         int32_t zero[1] = { 0 };
         ecc_generic_gf_poly_init(result, zero, 1);
         return ECC_NO_ERRORS;
     }
     int32_t product_length = self->coefficients_length + degree;
     int32_t* product = malloc(sizeof(int32_t) * product_length);
     memset(product, 0, sizeof(int32_t) * product_length);
     int32_t i;
     for (i = 0; i < self->coefficients_length; i++) {
         product[i] = ecc_generic_gf_multiply(field, self->coefficients[i], coefficient);
     }
     ecc_generic_gf_poly_init(result, product, product_length);
     free(product);
     return ECC_NO_ERRORS;
 }

 int32_t ecc_generic_gf_poly_divide(ecc_generic_gf_poly_t* self, ecc_generic_gf_t* field, ecc_generic_gf_poly_t* other, ecc_generic_gf_poly_t* result) {
    if (ecc_generic_gf_poly_is_zero(other)) {
         return ECC_DIVIDE_BY_ZERO;
    }
    ecc_generic_gf_poly_t remainder;
    ecc_generic_gf_poly_copy(&remainder, self);
    
    int32_t denominatorLeadingTerm = ecc_generic_gf_poly_get_coefficient(other, ecc_generic_gf_poly_get_degree(other));
    int32_t inverseDenominatorLeadingTerm = ecc_generic_gf_inverse(field, denominatorLeadingTerm);

    ecc_generic_gf_poly_t new_remainder;

    while (ecc_generic_gf_poly_get_degree(&remainder) >= ecc_generic_gf_poly_get_degree(other) &&
        !ecc_generic_gf_poly_is_zero(&remainder)) {
        int32_t degree_difference = ecc_generic_gf_poly_get_degree(&remainder) - ecc_generic_gf_poly_get_degree(other);
        int32_t scale = ecc_generic_gf_multiply(field, ecc_generic_gf_poly_get_coefficient(&remainder, 
            ecc_generic_gf_poly_get_degree(&remainder)), inverseDenominatorLeadingTerm);
        ecc_generic_gf_poly_t term;
        ecc_generic_gf_poly_multiply_by_monomial(other, field, degree_difference, scale, &term);
        ecc_generic_gf_poly_add_or_substract(&remainder, &term, &new_remainder);
        ecc_generic_gf_poly_free(&term);
        ecc_generic_gf_poly_free(&remainder);
        ecc_generic_gf_poly_copy(&remainder, &new_remainder);
        ecc_generic_gf_poly_free(&new_remainder);
    }
    ecc_generic_gf_poly_copy(result, &remainder);
    ecc_generic_gf_poly_free(&remainder);
    return ECC_NO_ERRORS;
 }

 void ecc_generic_gf_poly_free(ecc_generic_gf_poly_t* self) {
     free(self->coefficients);
 }

 void ecc_generic_gf_init(ecc_generic_gf_t* self, int32_t primitive, int32_t size, int32_t b) {
     self->primitive = primitive;
     self->size = size;
     self->generator_base = b;
     self->exp_table = malloc(sizeof(int32_t) * size);
     self->log_table = malloc(sizeof(int32_t) * size);
     memset(self->log_table, 0, sizeof(int32_t) * size);
     int32_t x = 1;
     int32_t i;
     for (i = 0; i < size; i++) {
         self->exp_table[i] = x;
         x *= 2; // we're assuming the generator alpha is 2
         if (x >= size) {
             x ^= primitive;
             x &= size - 1;
         }
     }
     for (i = 0; i < size - 1; i++) {
         self->log_table[self->exp_table[i]] = i;
     }
     // logTable[0] == 0 but self should never be used
     int32_t zero[1] = { 0 };
     ecc_generic_gf_poly_init(&(self->zero), zero, 1);
     int32_t one[1] = { 1 };
     ecc_generic_gf_poly_init(&(self->one), one, 1);
 }

 void ecc_generic_gf_free(ecc_generic_gf_t* self) {
     free(self->exp_table);
     free(self->log_table);
     ecc_generic_gf_poly_free(&(self->zero));
     ecc_generic_gf_poly_free(&(self->one));
 }

 int32_t ecc_generic_gf_build_monomial(ecc_generic_gf_poly_t* poly, int32_t degree, int32_t coefficient) {
     if (degree < 0) {
         return ECC_ILLEGAL_ARGUMENT;
     }
     if (coefficient == 0) {
         int32_t zero[1] = { 0 };
         ecc_generic_gf_poly_init(poly, zero, 1);
         return ECC_NO_ERRORS;
     }
     int32_t* coefficients = malloc(sizeof(int32_t) * ((size_t)degree + 1));
     memset(coefficients, 0, sizeof(int32_t) * ((size_t)degree + 1));
     coefficients[0] = coefficient;
     ecc_generic_gf_poly_init(poly, coefficients, degree + 1);
     free(coefficients);
     return ECC_NO_ERRORS;
 }

 void ecc_generic_gf_poly_multiply(ecc_generic_gf_poly_t* self, ecc_generic_gf_t* field, int32_t scalar, ecc_generic_gf_poly_t* result) {
     if (scalar == 0) {
         int32_t zero[1] = { 0 };
         ecc_generic_gf_poly_init(result, zero, 1);
         return;
     }
     if (scalar == 1) {
         ecc_generic_gf_poly_copy(result, self);
         return;
     }
     int32_t i;
     int32_t* product = malloc(sizeof(int32_t) * self->coefficients_length);
     for (i = 0; i < self->coefficients_length; i++) {
         product[i] = ecc_generic_gf_multiply(field, self->coefficients[i], scalar);
     }
     ecc_generic_gf_poly_init(result, product, self->coefficients_length);
     free(product);
 }

 int32_t ecc_generic_gf_multiply(ecc_generic_gf_t* self, int32_t a, int32_t b) {
     if (a == 0 || b == 0) {
         return 0;
     }
     return self->exp_table[(self->log_table[a] + self->log_table[b]) % (self->size - 1)];
 }

 int32_t ecc_generic_gf_poly_get_coefficient(ecc_generic_gf_poly_t* self, int32_t degree) {
     return self->coefficients[self->coefficients_length - 1 - degree];
 }

 int32_t ecc_generic_gf_poly_get_degree(ecc_generic_gf_poly_t* self) {
     return self->coefficients_length - 1;
 }

 int32_t ecc_generic_gf_add_or_substract(int32_t a, int32_t b) {
     return a ^ b;
 }

 void ecc_generic_gf_poly_add_or_substract(ecc_generic_gf_poly_t* self, ecc_generic_gf_poly_t* other, ecc_generic_gf_poly_t* result) {
     if (ecc_generic_gf_poly_is_zero(self)) {
         ecc_generic_gf_poly_copy(result, other);
         return;
     }
     if (ecc_generic_gf_poly_is_zero(other)) {
         ecc_generic_gf_poly_copy(result, self);
         return;
     }

     int32_t* smaller_coefficients = self->coefficients;
     int32_t smaller_coefficients_length = self->coefficients_length;
     int32_t* larger_coefficients = other->coefficients;
     int32_t larger_coefficients_length = other->coefficients_length;
     if (self->coefficients_length > other->coefficients_length) {
         smaller_coefficients = other->coefficients;
         larger_coefficients = self->coefficients;
         smaller_coefficients_length = other->coefficients_length;
         larger_coefficients_length = self->coefficients_length;
     }

     int32_t* sum_diff = malloc(sizeof(int32_t) * larger_coefficients_length);
     memset(sum_diff, 0, sizeof(int32_t) * larger_coefficients_length);
     int32_t length_diff = larger_coefficients_length - smaller_coefficients_length;

     // Copy high-order terms only found in higher-degree polynomial's coefficients
     memcpy(sum_diff, larger_coefficients, length_diff * sizeof(int32_t));

     int32_t i;
     for (i = length_diff; i < larger_coefficients_length; i++) {
         sum_diff[i] = ecc_generic_gf_add_or_substract(smaller_coefficients[i - length_diff], larger_coefficients[i]);
     }

     ecc_generic_gf_poly_init(result, sum_diff, larger_coefficients_length);
     free(sum_diff);
 }

 int32_t ecc_generic_gf_poly_is_zero(ecc_generic_gf_poly_t* self) {
     return self->coefficients[0] == 0;
 }

 void ecc_generic_gf_poly_multiply_other(ecc_generic_gf_poly_t* self, ecc_generic_gf_t* field, ecc_generic_gf_poly_t* other, ecc_generic_gf_poly_t* result) {
    if (ecc_generic_gf_poly_is_zero(self) || ecc_generic_gf_poly_is_zero(other)) {
        int32_t zero[1] = { 0 };
        ecc_generic_gf_poly_init(result, zero, 1);
        return;
    }
    int32_t product_length = self->coefficients_length + other->coefficients_length - 1;
    int32_t* product = malloc(sizeof(int32_t) * product_length);
    memset(product, 0, sizeof(int32_t) * product_length);
    int32_t i;
    int32_t j;
    for (i = 0; i < self->coefficients_length; i++) {
        for (j = 0; j < other->coefficients_length; j++) {
            product[i + j] = ecc_generic_gf_add_or_substract(product[i + j], ecc_generic_gf_multiply(field, self->coefficients[i], other->coefficients[j]));
        }
    }
    ecc_generic_gf_poly_init(result, product, product_length);
    free(product);
 }

 int32_t ecc_generic_gf_inverse(ecc_generic_gf_t* self, int32_t a) {
     return self->exp_table[self->size - self->log_table[a] - 1];
 }

 int32_t ecc_generic_gf_poly_evaluate_at(ecc_generic_gf_poly_t* self, ecc_generic_gf_t* field, int32_t a) {
     if (a == 0) {
         // Just return the x^0 coefficient
         return ecc_generic_gf_poly_get_coefficient(self, 0);
     }
     if (a == 1) {
         // Just the sum of the coefficients
         int32_t result = 0;
         int32_t i;
         for (i = 0; i < self->coefficients_length; i++) {
             result = ecc_generic_gf_add_or_substract(result, self->coefficients[i]);
         }
         return result;
     }
     int32_t result = self->coefficients[0];
     int32_t i;
     for (i = 1; i < self->coefficients_length; i++) {
         result = ecc_generic_gf_add_or_substract(ecc_generic_gf_multiply(field, a, result), self->coefficients[i]);
     }
     return result;
 }

 void ecc_reed_solomon_encoder_add(ecc_reed_solomon_encoder_t* self, ecc_generic_gf_poly_t* el) {
     ecc_reed_solomon_cached_generator_t* last = self->cached_generators;
     self->cached_generators = malloc(sizeof(ecc_reed_solomon_cached_generator_t));
     self->cached_generators->index = last->index + 1;
     self->cached_generators->value = el;
     self->cached_generators->previous = last;
 }

 void ecc_reed_solomon_encoder_free(ecc_reed_solomon_encoder_t* self) {
     ecc_generic_gf_free(&(self->field));
     ecc_reed_solomon_cached_generator_t* previous = self->cached_generators;
     while (previous != NULL) {
         ecc_generic_gf_poly_free(previous->value);
         free(previous->value);
         ecc_reed_solomon_cached_generator_t* to_free = previous;
         previous = previous->previous;
         free(to_free);
     }
 }

 ecc_generic_gf_poly_t* ecc_reed_solomon_encoder_get(ecc_reed_solomon_encoder_t* self, int32_t index) {
     ecc_reed_solomon_cached_generator_t* previous = self->cached_generators;
     while (previous != NULL) {
         if (previous->index == index) {
             return previous->value;
         } else {
             previous = previous->previous;
         }
     }
     return NULL;
 }

 void ecc_reed_solomon_encoder_init(ecc_reed_solomon_encoder_t* self, int32_t primitive, int32_t size, int32_t b) {
     ecc_generic_gf_init(&(self->field), primitive, size, b);
     self->cached_generators = malloc(sizeof(ecc_reed_solomon_cached_generator_t));
     self->cached_generators->index = 0;
     self->cached_generators->previous = NULL;
     int32_t one[1] = { 1 };
     self->cached_generators->value = malloc(sizeof(ecc_generic_gf_poly_t));
     ecc_generic_gf_poly_init(self->cached_generators->value, one, 1);
 }

 ecc_generic_gf_poly_t* ecc_reed_solomon_encoder_build_generator(ecc_reed_solomon_encoder_t* self, int32_t degree) {
    if (degree >= self->cached_generators->index + 1) {
        ecc_generic_gf_poly_t* last_generator = self->cached_generators->value;
        int32_t d;
        for (d = self->cached_generators->index + 1; d <= degree; d++) {
            ecc_generic_gf_poly_t* next_generator = malloc(sizeof(ecc_generic_gf_poly_t));
            ecc_generic_gf_poly_t gen;
            int32_t* data = malloc(sizeof(int32_t) * 2);
            data[0] = 1;
            data[1] = self->field.exp_table[d - 1 + self->field.generator_base];
            ecc_generic_gf_poly_init(&gen, data, 2);
            free(data);
            ecc_generic_gf_poly_multiply_other(last_generator, &(self->field), &gen, next_generator);
            ecc_generic_gf_poly_free(&gen);
            ecc_reed_solomon_encoder_add(self, next_generator);
            last_generator = next_generator;
        }
    }
    return ecc_reed_solomon_encoder_get(self, degree);
 }

 void ecc_reed_solomon_encoder_encode(ecc_reed_solomon_encoder_t* self, int32_t* to_encode, int32_t to_encode_length, int32_t ec_bytes) {
     int32_t data_bytes = to_encode_length - ec_bytes;
     ecc_generic_gf_poly_t* generator = ecc_reed_solomon_encoder_build_generator(self, ec_bytes);
     int32_t* info_coefficients = malloc(sizeof(int32_t) * data_bytes);
     memcpy(info_coefficients, to_encode, data_bytes * sizeof(int32_t));
     ecc_generic_gf_poly_t info;
     ecc_generic_gf_poly_init(&info, info_coefficients, data_bytes);
     free(info_coefficients);
     ecc_generic_gf_poly_t monomial_result;
     ecc_generic_gf_poly_multiply_by_monomial(&info,&(self->field), ec_bytes, 1, &monomial_result);
     ecc_generic_gf_poly_free(&info);
     ecc_generic_gf_poly_t remainder;
     ecc_generic_gf_poly_divide(&monomial_result, &(self->field), generator, &remainder);
     ecc_generic_gf_poly_free(&monomial_result);
     int32_t num_zero_coefficients = ec_bytes - remainder.coefficients_length;
     int32_t i;
     for (i = 0; i < num_zero_coefficients; i++) {
         to_encode[data_bytes + i] = 0;
     }
     memcpy(to_encode + data_bytes + num_zero_coefficients, remainder.coefficients, remainder.coefficients_length * sizeof(int32_t));
     ecc_generic_gf_poly_free(&remainder);
 }



 int32_t ecc_reed_solomon_decoder_run_euclidean_algorithm(ecc_generic_gf_t* field, ecc_generic_gf_poly_t* a, ecc_generic_gf_poly_t* b, int32_t r_degree,
     ecc_generic_gf_poly_t* sigma, ecc_generic_gf_poly_t* omega) {
     int32_t ret = ECC_NO_ERRORS;
     // Assume a's degree is >= b's
     if (ecc_generic_gf_poly_get_degree(a) < ecc_generic_gf_poly_get_degree(b)) {
         ecc_generic_gf_poly_t* temp = a;
         a = b;
         b = temp;
     }

     ecc_generic_gf_poly_t r_last;
     ecc_generic_gf_poly_copy(&r_last, a);
     ecc_generic_gf_poly_t r;
     ecc_generic_gf_poly_copy(&r, b);
     ecc_generic_gf_poly_t t_last;
     ecc_generic_gf_poly_copy(&t_last, &(field->zero));
     ecc_generic_gf_poly_t t;
     ecc_generic_gf_poly_copy(&t, &(field->one));

     // Run Euclidean algorithm until r's degree is less than R/2
     while (ret == ECC_NO_ERRORS && ecc_generic_gf_poly_get_degree(&r) >= r_degree / 2) {
         ecc_generic_gf_poly_t r_last_last;
         ecc_generic_gf_poly_copy(&r_last_last, &r_last);
         ecc_generic_gf_poly_t t_last_last;
         ecc_generic_gf_poly_copy(&t_last_last, &t_last);

         ecc_generic_gf_poly_free(&r_last);
         ecc_generic_gf_poly_copy(&r_last, &r);
         ecc_generic_gf_poly_free(&t_last);
         ecc_generic_gf_poly_copy(&t_last, &t);

         if (ecc_generic_gf_poly_is_zero(&r_last)) {
             // Oops, Euclidean algorithm already terminated?
             ret = ECC_REED_SOLOMON_ERROR;
             ecc_generic_gf_poly_free(&t);
             ecc_generic_gf_poly_free(&r);
         }
         else {
             ecc_generic_gf_poly_free(&r);
             ecc_generic_gf_poly_copy(&r, &r_last_last);
             ecc_generic_gf_poly_t q;
             ecc_generic_gf_poly_copy(&q, &(field->zero));

             int32_t denominator_leading_term = ecc_generic_gf_poly_get_coefficient(&r_last, ecc_generic_gf_poly_get_degree(&r_last));
             int32_t dlt_inverse = ecc_generic_gf_inverse(field, denominator_leading_term);
             while (ecc_generic_gf_poly_get_degree(&r) >= ecc_generic_gf_poly_get_degree(&r_last) && !ecc_generic_gf_poly_is_zero(&r)) {
                 int32_t degree_diff = ecc_generic_gf_poly_get_degree(&r) - ecc_generic_gf_poly_get_degree(&r_last);
                 int32_t scale = ecc_generic_gf_multiply(field, ecc_generic_gf_poly_get_coefficient(&r, ecc_generic_gf_poly_get_degree(&r)), dlt_inverse);
                 ecc_generic_gf_poly_t other;
                 ecc_generic_gf_build_monomial(&other, degree_diff, scale);
                 ecc_generic_gf_poly_t new_value;
                 ecc_generic_gf_poly_add_or_substract(&q, &other, &new_value);
                 ecc_generic_gf_poly_free(&other);
                 ecc_generic_gf_poly_free(&q);
                 ecc_generic_gf_poly_copy(&q, &new_value);
                 ecc_generic_gf_poly_free(&new_value);
                 ecc_generic_gf_poly_multiply_by_monomial(&r_last, field, degree_diff, scale, &other);
                 ecc_generic_gf_poly_add_or_substract(&r, &other, &new_value);
                 ecc_generic_gf_poly_free(&other);
                 ecc_generic_gf_poly_free(&r);
                 ecc_generic_gf_poly_copy(&r, &new_value);
                 ecc_generic_gf_poly_free(&new_value);
             }
             ecc_generic_gf_poly_t result;
             ecc_generic_gf_poly_multiply_other(&q, field, &t_last, &result);
             ecc_generic_gf_poly_free(&t);
             ecc_generic_gf_poly_add_or_substract(&result, &t_last_last, &t);
             ecc_generic_gf_poly_free(&result);

             if (ecc_generic_gf_poly_get_degree(&r) >= ecc_generic_gf_poly_get_degree(&r_last)) {
                 ret = ECC_ILLEGAL_STATE_EXCEPTION;
                 // Division algorithm failed to reduce polynomial?
             }
             ecc_generic_gf_poly_free(&q);
         }
         ecc_generic_gf_poly_free(&t_last_last);
         ecc_generic_gf_poly_free(&r_last_last);
     }

     if (ret == ECC_NO_ERRORS) {
         int32_t sigma_tilde_at_zero = ecc_generic_gf_poly_get_coefficient(&t, 0);
         if (sigma_tilde_at_zero == 0) {
             ret = ECC_REED_SOLOMON_ERROR;
         }
         int32_t inverse = ecc_generic_gf_inverse(field, sigma_tilde_at_zero);
         ecc_generic_gf_poly_multiply(&t, field, inverse, sigma);
         ecc_generic_gf_poly_multiply(&r, field, inverse, omega);
     }

     ecc_generic_gf_poly_free(&t);
     ecc_generic_gf_poly_free(&t_last);
     ecc_generic_gf_poly_free(&r);
     ecc_generic_gf_poly_free(&r_last);
     return ret;
 }

 int32_t ecc_reed_solomon_decoder_find_error_locations(ecc_generic_gf_poly_t* error_locator, ecc_generic_gf_t* field, int32_t* result) {
     int32_t ret = ECC_NO_ERRORS;
     int32_t num_errors = ecc_generic_gf_poly_get_degree(error_locator);
     if (num_errors == 1) { //Shortcut
         result[0] = ecc_generic_gf_poly_get_coefficient(error_locator, 1);
         ret = ECC_NO_ERRORS;
     }
     else {
         int32_t e = 0;
         int32_t i;
         for (i = 0; i < field->size && e < num_errors; i++) {
             if (ecc_generic_gf_poly_evaluate_at(error_locator,field, i) == 0) {
                 result[e] = ecc_generic_gf_inverse(field, i);
                 e++;
             }
         }
         if (e != num_errors) {
             ret = ECC_REED_SOLOMON_ERROR;
         }
     }
     return ret;
 }

 void ecc_reed_solomon_decoder_find_error_magnitudes(ecc_generic_gf_poly_t* error_locator, ecc_generic_gf_t* field, int32_t* error_locations, int32_t error_locations_length, int32_t* result) {
     int32_t s = error_locations_length;
     int32_t i;
     for (i = 0; i < s; i++) {
         int32_t xi_inverse = ecc_generic_gf_inverse(field, error_locations[i]);
         int32_t denominator = 1;
         int32_t j;
         for (j = 0; j < s; j++) {
             if (i != j) {
                 denominator = ecc_generic_gf_multiply(field, denominator,
                     ecc_generic_gf_add_or_substract(1, ecc_generic_gf_multiply(field, error_locations[j], xi_inverse)));
             }
         }
         result[i] = ecc_generic_gf_multiply(field, ecc_generic_gf_poly_evaluate_at(error_locator, field, xi_inverse), ecc_generic_gf_inverse(field, denominator));
         if (field->generator_base != 0) {
             result[i] = ecc_generic_gf_multiply(field, result[i], xi_inverse);
         }
     }
 }

 int32_t ecc_reed_solomon_decoder_decode(ecc_generic_gf_t* field, int32_t* to_decode, int32_t to_decode_length, int32_t ec_bytes, int32_t* fixedErrors) {
     int32_t ret = ECC_NO_ERRORS;
     ecc_generic_gf_poly_t poly;
     ecc_generic_gf_poly_init(&poly, to_decode, to_decode_length);
     int32_t syndrome_coefficients_length = ec_bytes;
     int32_t* syndrome_coefficients = malloc(sizeof(int32_t) * syndrome_coefficients_length);
     memset(syndrome_coefficients, 0, sizeof(int32_t) * syndrome_coefficients_length);
     int32_t no_error = 1;
     int32_t i;
     int32_t number_of_errors = 0;
     for (i = 0; i < ec_bytes; i++) {
         int32_t eval = ecc_generic_gf_poly_evaluate_at(&poly, field, field->exp_table[i + field->generator_base]);
         syndrome_coefficients[syndrome_coefficients_length - 1 - i] = eval;
         if (eval != 0) {
             no_error = 0;
         }
     }
     if (no_error == 0) {
         ecc_generic_gf_poly_t syndrome;
         ecc_generic_gf_poly_init(&syndrome, syndrome_coefficients, syndrome_coefficients_length);
         ecc_generic_gf_poly_t sigma;
         ecc_generic_gf_poly_t omega;
         ecc_generic_gf_poly_t mono;
         ecc_generic_gf_build_monomial(&mono, ec_bytes, 1);
         ret = ecc_reed_solomon_decoder_run_euclidean_algorithm(field, &mono, &syndrome, ec_bytes, &sigma, &omega);
         ecc_generic_gf_poly_free(&mono);
         if (ret == ECC_NO_ERRORS) {
             number_of_errors = ecc_generic_gf_poly_get_degree(&sigma);
             int32_t* error_locations = malloc(sizeof(int32_t) * number_of_errors);
             int32_t* error_magnitude = malloc(sizeof(int32_t) * number_of_errors);
             ret = ecc_reed_solomon_decoder_find_error_locations(&sigma, field, error_locations);
             ecc_generic_gf_poly_free(&sigma);
             if (ret == ECC_NO_ERRORS) {
                 ecc_reed_solomon_decoder_find_error_magnitudes(&omega, field, error_locations, number_of_errors, error_magnitude);
                 ecc_generic_gf_poly_free(&omega);
                 for (i = 0; i < number_of_errors && ret == ECC_NO_ERRORS; i++) {
                     int32_t position = to_decode_length - 1 - field->log_table[error_locations[i]];
                     if (position < 0) {
                         ret = ECC_REED_SOLOMON_ERROR; // Bad error location
                     }
                     to_decode[position] = ecc_generic_gf_add_or_substract(to_decode[position], error_magnitude[i]);
                 }
             }
             free(error_locations);
             free(error_magnitude);
         }
         ecc_generic_gf_poly_free(&syndrome);
     }
     free(syndrome_coefficients);
     ecc_generic_gf_poly_free(&poly);
     if(ret == ECC_NO_ERRORS && fixedErrors != NULL) {
         *fixedErrors += number_of_errors;
     }
     return ret;
 }




