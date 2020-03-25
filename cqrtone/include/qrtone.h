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



#ifdef __cplusplus
}
#endif

#endif