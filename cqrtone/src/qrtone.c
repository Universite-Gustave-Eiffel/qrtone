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
        memcpy(this->coefficients, coefficients + firstNonZero, sizeof(int32_t) * this->coefficients_length);
      }
    } else {
        this->coefficients = malloc(sizeof(int32_t) * coefficients_length);
        this->coefficients_length = coefficients_length;
        memcpy(this->coefficients, coefficients, sizeof(int32_t) * coefficients_length);
    }	 
 }

 int qrtone_generic_gf_poly_multiply_by_monomial(generic_gf_poly_t* this, generic_gf_t* field, int32_t degree, int32_t coefficient, generic_gf_poly_t* result) {
     if (degree < 0) {
         return QRTONE_ILLEGAL_ARGUMENT;
     }
     if (coefficient == 0) {
         int32_t zero[1] = { 0 };
         qrtone_generic_gf_poly_init(result, zero, 1);
         return QRTONE_NO_ERRORS;
     }
     int32_t product_length = this->coefficients_length + degree;
     int32_t* product = malloc(sizeof(int32_t) * product_length);
     memset(product, 0, sizeof(int32_t) * product_length);
     int32_t i;
     for (i = 0; i < this->coefficients_length; i++) {
         product[i] = qrtone_generic_gf_multiply(field, this->coefficients[i], coefficient);
     }
     qrtone_generic_gf_poly_init(result, product, product_length);
     free(product);
     return QRTONE_NO_ERRORS;
 }

 int qrtone_generic_gf_poly_divide(generic_gf_poly_t* this, generic_gf_t* field, generic_gf_poly_t* other, generic_gf_poly_t* result) {
    if (qrtone_generic_gf_poly_is_zero(other)) {
         return QRTONE_DIVIDE_BY_ZERO;
    }
    generic_gf_poly_t remainder;
    qrtone_generic_gf_poly_copy(&remainder, this);
    
    int32_t denominatorLeadingTerm = qrtone_generic_gf_poly_get_coefficient(other, qrtone_generic_gf_poly_get_degree(other));
    int32_t inverseDenominatorLeadingTerm = qrtone_generic_gf_inverse(field, denominatorLeadingTerm);

    generic_gf_poly_t new_remainder;

    while (qrtone_generic_gf_poly_get_degree(&remainder) >= qrtone_generic_gf_poly_get_degree(other) &&
        !qrtone_generic_gf_poly_is_zero(&remainder)) {
        int32_t degree_difference = qrtone_generic_gf_poly_get_degree(&remainder) - qrtone_generic_gf_poly_get_degree(other);
        int32_t scale = qrtone_generic_gf_multiply(field, qrtone_generic_gf_poly_get_coefficient(&remainder, 
            qrtone_generic_gf_poly_get_degree(&remainder)), inverseDenominatorLeadingTerm);
        generic_gf_poly_t term;
        qrtone_generic_gf_poly_multiply_by_monomial(other, field, degree_difference, scale, &term);
        qrtone_generic_gf_poly_add_or_substract(&remainder, &term, &new_remainder);
        qrtone_generic_gf_poly_free(&term);
        qrtone_generic_gf_poly_free(&remainder);
        qrtone_generic_gf_poly_copy(&remainder, &new_remainder);
        qrtone_generic_gf_poly_free(&new_remainder);
    }
    qrtone_generic_gf_poly_copy(result, &remainder);
    qrtone_generic_gf_poly_free(&remainder);
    return QRTONE_NO_ERRORS;
 }

 void qrtone_generic_gf_poly_copy(generic_gf_poly_t* this, generic_gf_poly_t* other) {
     this->coefficients = malloc(sizeof(int32_t) * other->coefficients_length);
     memcpy(this->coefficients, other->coefficients, other->coefficients_length * sizeof(int32_t));
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

 int qrtone_generic_gf_build_monomial(generic_gf_poly_t* poly, int32_t degree, int32_t coefficient) {
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

 int32_t qrtone_generic_gf_poly_get_degree(generic_gf_poly_t* this) {
     return this->coefficients_length - 1;
 }

 int32_t qrtone_generic_gf_add_or_substract(int32_t a, int32_t b) {
     return a ^ b;
 }

 void qrtone_generic_gf_poly_add_or_substract(generic_gf_poly_t* this, generic_gf_poly_t* other, generic_gf_poly_t* result) {
     if (qrtone_generic_gf_poly_is_zero(this)) {
         qrtone_generic_gf_poly_copy(result, other);
     }
     if (qrtone_generic_gf_poly_is_zero(other)) {
         qrtone_generic_gf_poly_copy(result, this);
     }

     int32_t* smaller_coefficients = this->coefficients;
     int32_t smaller_coefficients_length = this->coefficients_length;
     int32_t* larger_coefficients = other->coefficients;
     int32_t larger_coefficients_length = other->coefficients_length;
     if (this->coefficients_length > other->coefficients_length) {
         smaller_coefficients = other->coefficients;
         larger_coefficients = this->coefficients;
         smaller_coefficients_length = other->coefficients_length;
         larger_coefficients_length = this->coefficients_length;
     }

     int32_t* sum_diff = malloc(sizeof(int32_t) * larger_coefficients_length);
     memset(sum_diff, 0, sizeof(int32_t) * larger_coefficients_length);
     int32_t length_diff = larger_coefficients_length - smaller_coefficients_length;

     // Copy high-order terms only found in higher-degree polynomial's coefficients
     memcpy(sum_diff, larger_coefficients, length_diff * sizeof(int32_t));

     int32_t i;
     for (i = length_diff; i < larger_coefficients_length; i++) {
         sum_diff[i] = qrtone_generic_gf_add_or_substract(smaller_coefficients[i - length_diff], larger_coefficients[i]);
     }

     qrtone_generic_gf_poly_init(result, sum_diff, larger_coefficients_length);
     free(sum_diff);
 }

 int qrtone_generic_gf_poly_is_zero(generic_gf_poly_t* this) {
     return this->coefficients[0] == 0;
 }

 void qrtone_generic_gf_poly_multiply_other(generic_gf_poly_t* this, generic_gf_t* field, generic_gf_poly_t* other, generic_gf_poly_t* result) {
    if (qrtone_generic_gf_poly_is_zero(this) || qrtone_generic_gf_poly_is_zero(other)) {
        int32_t zero[1] = { 0 };
        qrtone_generic_gf_poly_init(result, zero, 1);
        return;
    }
    int32_t product_length = this->coefficients_length + other->coefficients_length - 1;
    int32_t* product = malloc(sizeof(int32_t) * product_length);
    memset(product, 0, sizeof(int32_t) * product_length);
    int32_t i;
    int32_t j;
    for (i = 0; i < this->coefficients_length; i++) {
        for (j = 0; j < other->coefficients_length; j++) {
            product[i + j] = qrtone_generic_gf_add_or_substract(product[i + j], qrtone_generic_gf_multiply(field, this->coefficients[i], other->coefficients[j]));
        }
    }
    qrtone_generic_gf_poly_init(result, product, product_length);
    free(product);
 }

 int32_t qrtone_generic_gf_inverse(generic_gf_t* this, int32_t a) {
     return this->exp_table[this->size - this->log_table[a] - 1];
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

 void qrtone_reed_solomon_encoder_add(reed_solomon_encoder_t* this, generic_gf_poly_t* el) {
     reed_solomon_cached_generator_t* last = this->cached_generators;
     this->cached_generators = malloc(sizeof(reed_solomon_cached_generator_t));
     this->cached_generators->index = last->index + 1;
     this->cached_generators->value = el;
     this->cached_generators->previous = last;
 }

 void qrtone_reed_solomon_encoder_free(reed_solomon_encoder_t* this) {
     qrtone_generic_gf_free(&(this->field));
     reed_solomon_cached_generator_t* previous = this->cached_generators;
     while (previous != NULL) {
         qrtone_generic_gf_poly_free(previous->value);
         free(previous->value);
         reed_solomon_cached_generator_t* to_free = previous;
         previous = previous->previous;
         free(to_free);
     }
 }

 generic_gf_poly_t* qrtone_reed_solomon_encoder_get(reed_solomon_encoder_t* this, int32_t index) {
     reed_solomon_cached_generator_t* previous = this->cached_generators;
     while (previous != NULL) {
         if (previous->index == index) {
             return previous->value;
         } else {
             previous = previous->previous;
         }
     }
     return NULL;
 }

 void qrtone_reed_solomon_encoder_init(reed_solomon_encoder_t* this, int32_t primitive, int32_t size, int32_t b) {
     qrtone_generic_gf_init(&(this->field), primitive, size, b);
     this->cached_generators = malloc(sizeof(reed_solomon_cached_generator_t));
     this->cached_generators->index = 0;
     this->cached_generators->previous = NULL;
     int one[1] = { 1 };
     this->cached_generators->value = malloc(sizeof(generic_gf_poly_t));
     qrtone_generic_gf_poly_init(this->cached_generators->value, one, 1);
 }

 generic_gf_poly_t* qrtone_reed_solomon_encoder_build_generator(reed_solomon_encoder_t* this, int32_t degree) {
    if (degree >= this->cached_generators->index + 1) {
        generic_gf_poly_t* last_generator = this->cached_generators->value;
        int32_t d;
        for (d = this->cached_generators->index + 1; d <= degree; d++) {
            generic_gf_poly_t* next_generator = malloc(sizeof(generic_gf_poly_t));
            generic_gf_poly_t gen;
            int32_t* data = malloc(sizeof(int32_t) * 2);
            data[0] = 1;
            data[1] = this->field.exp_table[d - 1 + this->field.generator_base];
            qrtone_generic_gf_poly_init(&gen, data, 2);
            free(data);
            qrtone_generic_gf_poly_multiply_other(last_generator, &(this->field), &gen, next_generator);
            qrtone_generic_gf_poly_free(&gen);
            qrtone_reed_solomon_encoder_add(this, next_generator);
            last_generator = next_generator;
        }
    }
    return qrtone_reed_solomon_encoder_get(this, degree);
 }

 void qrtone_reed_solomon_encoder_encode(reed_solomon_encoder_t* this, int32_t* to_encode, int32_t to_encode_length, int32_t ec_bytes) {
     int32_t data_bytes = to_encode_length - ec_bytes;
     generic_gf_poly_t* generator = qrtone_reed_solomon_encoder_build_generator(this, ec_bytes);
     int32_t* info_coefficients = malloc(sizeof(int32_t) * data_bytes);
     memcpy(info_coefficients, to_encode, data_bytes * sizeof(int32_t));
     generic_gf_poly_t info;
     qrtone_generic_gf_poly_init(&info, info_coefficients, data_bytes);
     free(info_coefficients);
     generic_gf_poly_t monomial_result;
     qrtone_generic_gf_poly_multiply_by_monomial(&info,&(this->field), ec_bytes, 1, &monomial_result);
     qrtone_generic_gf_poly_free(&info);
     generic_gf_poly_t remainder;
     qrtone_generic_gf_poly_divide(&monomial_result, &(this->field), generator, &remainder);
     qrtone_generic_gf_poly_free(&monomial_result);
     int32_t num_zero_coefficients = ec_bytes - remainder.coefficients_length;
     int32_t i;
     for (i = 0; i < num_zero_coefficients; i++) {
         to_encode[data_bytes + i] = 0;
     }
     memcpy(to_encode + data_bytes + num_zero_coefficients, remainder.coefficients, remainder.coefficients_length * sizeof(int32_t));
     qrtone_generic_gf_poly_free(&remainder);
 }



 int qrtone_reed_solomon_decoder_run_euclidean_algorithm(generic_gf_t* field, generic_gf_poly_t* a, generic_gf_poly_t* b, int32_t r_degree,
     generic_gf_poly_t* sigma, generic_gf_poly_t* omega) {
     int32_t ret = QRTONE_NO_ERRORS;
     // Assume a's degree is >= b's
     if (qrtone_generic_gf_poly_get_degree(a) < qrtone_generic_gf_poly_get_degree(b)) {
         generic_gf_poly_t* temp = a;
         a = b;
         b = temp;
     }

     generic_gf_poly_t r_last;
     qrtone_generic_gf_poly_copy(&r_last, a);
     generic_gf_poly_t r;
     qrtone_generic_gf_poly_copy(&r, b);
     generic_gf_poly_t t_last;
     qrtone_generic_gf_poly_copy(&t_last, &(field->zero));
     generic_gf_poly_t t;
     qrtone_generic_gf_poly_copy(&t, &(field->one));

     // Run Euclidean algorithm until r's degree is less than R/2
     while (ret == QRTONE_NO_ERRORS && qrtone_generic_gf_poly_get_degree(&r) >= r_degree / 2) {
         generic_gf_poly_t r_last_last;
         qrtone_generic_gf_poly_copy(&r_last_last, &r_last);
         generic_gf_poly_t t_last_last;
         qrtone_generic_gf_poly_copy(&t_last_last, &t_last);

         qrtone_generic_gf_poly_free(&r_last);
         qrtone_generic_gf_poly_copy(&r_last, &r);
         qrtone_generic_gf_poly_free(&t_last);
         qrtone_generic_gf_poly_copy(&t_last, &t);

         if (qrtone_generic_gf_poly_is_zero(&r_last)) {
             // Oops, Euclidean algorithm already terminated?
             ret = QRTONE_REED_SOLOMON_ERROR;
         }
         else {
             qrtone_generic_gf_poly_free(&r);
             qrtone_generic_gf_poly_copy(&r, &r_last_last);
             generic_gf_poly_t q;
             qrtone_generic_gf_poly_copy(&q, &(field->zero));

             int32_t denominator_leading_term = qrtone_generic_gf_poly_get_coefficient(&r_last, qrtone_generic_gf_poly_get_degree(&r_last));
             int32_t dlt_inverse = qrtone_generic_gf_inverse(field, denominator_leading_term);
             while (qrtone_generic_gf_poly_get_degree(&r) >= qrtone_generic_gf_poly_get_degree(&r_last) && !qrtone_generic_gf_poly_is_zero(&r)) {
                 int32_t degree_diff = qrtone_generic_gf_poly_get_degree(&r) - qrtone_generic_gf_poly_get_degree(&r_last);
                 int32_t scale = qrtone_generic_gf_multiply(field, qrtone_generic_gf_poly_get_coefficient(&r, qrtone_generic_gf_poly_get_degree(&r)), dlt_inverse);
                 generic_gf_poly_t other;
                 qrtone_generic_gf_build_monomial(&other, degree_diff, scale);
                 generic_gf_poly_t new_value;
                 qrtone_generic_gf_poly_add_or_substract(&q, &other, &new_value);
                 qrtone_generic_gf_poly_free(&other);
                 qrtone_generic_gf_poly_free(&q);
                 qrtone_generic_gf_poly_copy(&q, &new_value);
                 qrtone_generic_gf_poly_free(&new_value);
                 qrtone_generic_gf_poly_multiply_by_monomial(&r_last, field, degree_diff, scale, &other);
                 qrtone_generic_gf_poly_add_or_substract(&r, &other, &new_value);
                 qrtone_generic_gf_poly_copy(&r, &new_value);
                 qrtone_generic_gf_poly_free(&new_value);
             }
             generic_gf_poly_t result;
             qrtone_generic_gf_poly_multiply_other(&q, field, &t_last, &result);
             qrtone_generic_gf_poly_free(&t);
             qrtone_generic_gf_poly_add_or_substract(&result, &t_last_last, &t);
             qrtone_generic_gf_poly_free(&result);

             if (qrtone_generic_gf_poly_get_degree(&r) >= qrtone_generic_gf_poly_get_degree(&r_last)) {
                 ret = QRTONE_ILLEGAL_STATE_EXCEPTION;
                 // Division algorithm failed to reduce polynomial?
             }
             qrtone_generic_gf_poly_free(&q);
         }
         qrtone_generic_gf_poly_free(&t_last_last);
         qrtone_generic_gf_poly_free(&r_last_last);
     }

     if (ret == QRTONE_NO_ERRORS) {
         int32_t sigma_tilde_at_zero = qrtone_generic_gf_poly_get_coefficient(&t, 0);
         if (sigma_tilde_at_zero == 0) {
             ret = QRTONE_REED_SOLOMON_ERROR;
         }
         int32_t inverse = qrtone_generic_gf_inverse(field, sigma_tilde_at_zero);
         qrtone_generic_gf_poly_multiply(&t, field, inverse, sigma);
         qrtone_generic_gf_poly_multiply(&r, field, inverse, omega);
     }

     qrtone_generic_gf_poly_free(&t);
     qrtone_generic_gf_poly_free(&t_last);
     qrtone_generic_gf_poly_free(&r);
     qrtone_generic_gf_poly_free(&r_last);
     return ret;
 }

 int qrtone_reed_solomon_decoder_find_error_locations(generic_gf_poly_t* error_locator, generic_gf_t* field, int32_t* result) {
     int32_t ret = QRTONE_NO_ERRORS;
     int num_errors = qrtone_generic_gf_poly_get_degree(error_locator);
     if (num_errors == 1) { //Shortcut
         result[0] = qrtone_generic_gf_poly_get_coefficient(error_locator, 1);
         ret = QRTONE_NO_ERRORS;
     }
     else {
         int32_t e = 0;
         int32_t i;
         for (i = 0; i < field->size && e < num_errors; i++) {
             if (qrtone_generic_gf_poly_evaluate_at(error_locator,field, i) == 0) {
                 result[e] = qrtone_generic_gf_inverse(field, i);
                 e++;
             }
         }
         if (e != num_errors) {
             ret = QRTONE_REED_SOLOMON_ERROR;
         }
     }
     return ret;
 }

 void qrtone_reed_solomon_decoder_find_error_magnitudes(generic_gf_poly_t* error_locator, generic_gf_t* field, int32_t* error_locations, int32_t error_locations_length, int32_t* result) {
     int32_t s = error_locations_length;
     int32_t i;
     for (i = 0; i < s; i++) {
         int32_t xi_inverse = qrtone_generic_gf_inverse(field, error_locations[i]);
         int32_t denominator = 1;
         int32_t j;
         for (j = 0; j < s; j++) {
             if (i != j) {
                 denominator = qrtone_generic_gf_multiply(field, denominator,
                     qrtone_generic_gf_add_or_substract(1, qrtone_generic_gf_multiply(field, error_locations[j], xi_inverse)));
             }
         }
         result[i] = qrtone_generic_gf_multiply(field, qrtone_generic_gf_poly_evaluate_at(error_locator, field, xi_inverse), qrtone_generic_gf_inverse(field, denominator));
         if (field->generator_base != 0) {
             result[i] = qrtone_generic_gf_multiply(field, result[i], xi_inverse);
         }
     }
 }

 int32_t qrtone_reed_solomon_decoder_decode(generic_gf_t* field, int32_t* to_decode, int32_t to_decode_length, int32_t ec_bytes) {
     int32_t ret = QRTONE_NO_ERRORS;
     generic_gf_poly_t poly;
     qrtone_generic_gf_poly_init(&poly, to_decode, to_decode_length);
     int32_t syndrome_coefficients_length = ec_bytes;
     int32_t* syndrome_coefficients = malloc(sizeof(int32_t) * syndrome_coefficients_length);
     memset(syndrome_coefficients, 0, sizeof(int32_t) * syndrome_coefficients_length);
     int32_t no_error = 1;
     int32_t i;
     int32_t number_of_errors = 0;
     for (i = 0; i < ec_bytes; i++) {
         int32_t eval = qrtone_generic_gf_poly_evaluate_at(&poly, field, field->exp_table[i + field->generator_base]);
         syndrome_coefficients[syndrome_coefficients_length - 1 - i] = eval;
         if (eval != 0) {
             no_error = 0;
         }
     }
     if (no_error == 0) {
         generic_gf_poly_t syndrome;
         qrtone_generic_gf_poly_init(&syndrome, syndrome_coefficients, syndrome_coefficients_length);
         generic_gf_poly_t sigma;
         generic_gf_poly_t omega;
         generic_gf_poly_t mono;
         qrtone_generic_gf_build_monomial(&mono, ec_bytes, 1);
         ret = qrtone_reed_solomon_decoder_run_euclidean_algorithm(field, &mono, &syndrome, ec_bytes, &sigma, &omega);
         qrtone_generic_gf_poly_free(&mono);
         if (ret == QRTONE_NO_ERRORS) {
             number_of_errors = qrtone_generic_gf_poly_get_degree(&sigma);
             int32_t* error_locations = malloc(sizeof(int32_t) * number_of_errors);
             int32_t* error_magnitude = malloc(sizeof(int32_t) * number_of_errors);
             ret = qrtone_reed_solomon_decoder_find_error_locations(&sigma, field, error_locations);
             qrtone_generic_gf_poly_free(&sigma);
             if (ret == QRTONE_NO_ERRORS) {
                 qrtone_reed_solomon_decoder_find_error_magnitudes(&omega, field, error_locations, number_of_errors, error_magnitude);
                 qrtone_generic_gf_poly_free(&omega);
                 for (i = 0; i < number_of_errors && ret == QRTONE_NO_ERRORS; i++) {
                     int32_t position = to_decode_length - 1 - field->log_table[error_locations[i]];
                     if (position < 0) {
                         ret = QRTONE_REED_SOLOMON_ERROR; // Bad error location
                     }
                     to_decode[position] = qrtone_generic_gf_add_or_substract(to_decode[position], error_magnitude[i]);
                 }
             }
             free(error_locations);
             free(error_magnitude);
         }
         qrtone_generic_gf_poly_free(&syndrome);
     }
     free(syndrome_coefficients);
     qrtone_generic_gf_poly_free(&poly);
     return ret;
 }




