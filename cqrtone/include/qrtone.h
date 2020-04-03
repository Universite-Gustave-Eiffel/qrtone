/*
 * BSD 3-Clause License
 *
 * Copyright (c) Unit� Mixte de Recherche en Acoustique Environnementale (univ-gustave-eiffel)
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
 * @file qrtone.h
 * @author Nicolas Fortin @NicolasCumu
 * @date 24/03/2020
 * @brief Api of QRTone library
 * Usage:
 * 1. Declare struct instance of qrtone_t
 * TODO
 */

#ifndef QRTONE_H
#define QRTONE_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include "reed_solomon.h"

enum QRTONE_ECC_LEVEL { QRTONE_ECC_L = 0, QRTONE_ECC_M = 1, QRTONE_ECC_Q = 2, QRTONE_ECC_H = 3};

// DTMF 16*16 frequencies
#define QRTONE_NUM_FREQUENCIES 32

typedef struct _qrtone_crc8_t {
    int32_t crc8;
} qrtone_crc8_t;

typedef struct _qrtone_crc16_t {
    int32_t crc16;
} qrtone_crc16_t;

typedef struct _qrtone_goertzel_t {
    double s0;
    double s1;
    double s2;
    double cos_pik_term2;
    double pik_term;
    float last_sample;
    double sample_rate;
    int32_t window_size;
    int32_t processed_samples;
} qrtone_goertzel_t;

typedef struct _qrtone_percentile_t {
    double* q;
    double* dn;
    double* np;
    int32_t* n;
    int32_t count;
    int32_t marker_count;
} qrtone_percentile_t;

typedef struct _qrtone_array_t {
    float* values;
    int32_t values_length;
    int32_t cursor;
    int32_t inserted;
} qrtone_array_t;

typedef struct _qrtone_peak_finder_t {
    int8_t increase; // boolean increase state
    double old_val;
    int64_t old_index;
    int8_t added;
    double last_peak_value;
    int64_t last_peak_index;
    int32_t increase_count;
    int32_t decrease_count;
    int32_t min_increase_count;
    int32_t min_decrease_count;
} qrtone_peak_finder_t;

typedef struct _qrtone_header_t {
    uint8_t length; // payload length
    int8_t crc;
    int8_t ecc_level;
    int32_t payload_symbols_size;
    int32_t payload_byte_size;
    int32_t number_of_blocks;
    int32_t number_of_symbols;
} qrtone_header_t;

typedef struct _qrtone_trigger_analyzer_t {
    int32_t processed_window_alpha;
    int32_t processed_window_beta;
    int32_t window_offset;
    int32_t gate_length;
    qrtone_goertzel_t frequency_analyzers_alpha[2];
    qrtone_goertzel_t frequency_analyzers_beta[2];
    qrtone_percentile_t background_noise_evaluator;
    qrtone_array_t spl_history[2];
    qrtone_peak_finder_t peak_finder;
    int32_t window_analyze;
    int64_t total_processed;
    double frequencies[2];
    double sample_rate;
    double trigger_snr;
    int64_t first_tone_location;
} qrtone_trigger_analyzer_t;

typedef struct _qrtone_t {
    int8_t qr_tone_state;
    qrtone_goertzel_t frequency_analyzers[QRTONE_NUM_FREQUENCIES];
    int64_t first_tone_sample_index;
    int32_t word_length;
    int32_t gate_length;
    int32_t word_silence_length;
    double gate1_frequency;
    double gate2_frequency;
    double sample_rate;
    double frequencies[QRTONE_NUM_FREQUENCIES];
    qrtone_trigger_analyzer_t trigger_analyzer;
    int8_t* symbols_to_deliver;
    int32_t symbols_to_deliver_length;
    int8_t* symbols_cache;
    int32_t symbols_cache_length;
    qrtone_header_t header_cache;
    int64_t pushed_samples;
    int32_t symbol_index;
    int8_t* payload;
    int32_t fixed_errors;
    ecc_reed_solomon_encoder_t encoder;
} qrtone_t;

void qrtone_crc8_init(qrtone_crc8_t* this);

void qrtone_crc8_add(qrtone_crc8_t* this, const int8_t data);

uint8_t qrtone_crc8_get(qrtone_crc8_t* this);

void qrtone_crc8_add_array(qrtone_crc8_t* this, const int8_t* data, const int32_t data_length);

void qrtone_crc16_init(qrtone_crc16_t* this);

void qrtone_crc16_add_array(qrtone_crc16_t* this, const int8_t* data, const int32_t data_length);

void qrtone_goertzel_reset(qrtone_goertzel_t* this);

void qrtone_goertzel_init(qrtone_goertzel_t* this, double sample_rate, double frequency, int32_t window_size);

void qrtone_goertzel_process_samples(qrtone_goertzel_t* this, float* samples, int32_t samples_len);

double qrtone_goertzel_compute_rms(qrtone_goertzel_t* this);

void qrtone_percentile_free(qrtone_percentile_t* this);

double qrtone_percentile_result(qrtone_percentile_t* this);

void qrtone_percentile_add(qrtone_percentile_t* this, double data);

void qrtone_percentile_init_quantile(qrtone_percentile_t* this, double quant);

float qrtone_array_last(qrtone_array_t* this);

int32_t qrtone_array_size(qrtone_array_t* this);

void qrtone_array_clear(qrtone_array_t* this);

float qrtone_array_get(qrtone_array_t* this, int32_t index);

void qrtone_array_init(qrtone_array_t* this, int32_t length);

void qrtone_array_free(qrtone_array_t* this);

void qrtone_array_add(qrtone_array_t* this, float value);

void qrtone_peak_finder_init(qrtone_peak_finder_t* this);

int8_t qrtone_peak_finder_add(qrtone_peak_finder_t* this, int64_t index, float value);

void qrtone_hann_window(float* signal, int32_t signal_length, int32_t window_length, int32_t offset);

int64_t qrtone_find_peak_location(double p0, double p1, double p2, int64_t p1_location, int32_t window_length);

void qrtone_quadratic_interpolation(double p0, double p1, double p2, double* location, double* height, double* half_curvature);

void qrtone_interleave_symbols(int8_t* symbols, int32_t symbols_length, int32_t block_size);

void qrtone_deinterleave_symbols(int8_t* symbols, int32_t symbols_length, int32_t block_size);

void qrtone_init(qrtone_t* this, double sample_rate);

int32_t qrtone_set_payload(qrtone_t* this, int8_t* payload, uint8_t payload_length);

int32_t qrtone_set_payload_ext(qrtone_t* this, int8_t* payload, uint8_t payload_length, int8_t ecc_level, int8_t add_crc);

void qrtone_get_samples(qrtone_t* this, float* samples, int32_t samples_length, int32_t offset, float power);

void qrtone_header_init(qrtone_header_t* this, uint8_t length, int32_t block_symbols_size, int32_t block_ecc_symbols, int8_t crc);

void qrtone_header_encode(qrtone_header_t* this, int8_t* data);

int8_t qrtone_header_init_from_data(qrtone_header_t* this, int8_t* data);

int8_t* qrtone_symbols_to_payload(qrtone_t* this, int8_t* symbols, int32_t symbols_length, int32_t block_symbols_size, int32_t block_ecc_symbols, int8_t has_crc);

void qrtone_payload_to_symbols(qrtone_t* this, int8_t* payload, uint8_t payload_length, int32_t block_symbols_size, int32_t block_ecc_symbols, int8_t has_crc, int8_t* symbols);

void qrtone_free(qrtone_t* this);

#ifdef __cplusplus
}
#endif

#endif