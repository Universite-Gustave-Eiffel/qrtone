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
 * @file qrtone.c
 * @author Nicolas Fortin @NicolasCumu
 * @date 24/03/2020
 * @brief QRTone C99 implementation. The size of the code is reduced at the expense of the speed of execution. ex no crc tables
 */
#ifdef _DEBUG
#define _CRTDBG_MAP_ALLOC
#ifdef _WIN32
#include <crtdbg.h>
#endif
#endif

#include <stdlib.h>
#include <string.h>
#include "reed_solomon.h"
#include "qrtone.h"
#include "math.h"

#define QRTONE_2PI 6.283185307179586


#ifdef max
#undef max
#endif
#ifdef min
#undef min
#endif
#define max(a, b) ((a) > (b) ? (a) : (b))
#define min(a, b) ((a) < (b) ? (a) : (b))

#ifndef M_PI
#define M_PI 3.1415926535897932385
#endif


struct _qrtonecomplex NEW_CX(double r, double i) {
    return (qrtonecomplex) { r, i };
}

struct _qrtonecomplex CX_ADD(const qrtonecomplex c1, const qrtonecomplex c2) {
    return (qrtonecomplex) { c1.r + c2.r, c1.i + c2.i };
}

struct _qrtonecomplex CX_SUB(const qrtonecomplex c1, const qrtonecomplex c2) {
    return (qrtonecomplex) { c1.r - c2.r, c1.i - c2.i };
}

struct _qrtonecomplex CX_MUL(const qrtonecomplex c1, const qrtonecomplex c2) {
    return (qrtonecomplex) { c1.r* c2.r - c1.i * c2.i, c1.r* c2.i + c1.i * c2.r };
}

struct _qrtonecomplex CX_EXP(const qrtonecomplex c1) {
    return (qrtonecomplex) { cos(c1.r), -sin(c1.r) };
}

void qrtone_crc8_init(qrtone_crc8_t* this) {
    this->crc8 = 0;
}

void qrtone_crc8_add(qrtone_crc8_t* this, const int8_t data) {
    int32_t crc = 0;
    int32_t accumulator = (this->crc8 ^ data) & 0x0FF;
    int32_t j;
    for (j = 0; j < 8; j++) {
        if (((accumulator ^ crc) & 0x01) == 0x01) {
            crc = ((crc ^ 0x18) >> 1) | 0x80;
        }
        else {
            crc = crc >> 1;
        }
        accumulator = accumulator >> 1;
    }
    this->crc8 = crc;
}

void qrtone_crc8_add_array(qrtone_crc8_t* this, const int8_t* data, const int32_t data_length) {
    int32_t i;
    for (i = 0; i < data_length; i++) {
        qrtone_crc8_add(this, data[i]);
    }
}

uint8_t qrtone_crc8_get(qrtone_crc8_t* this) {
    return this->crc8 & 0xFF;
}

void qrtone_crc16_init(qrtone_crc16_t* this) {
    this->crc16 = 0;
}

void qrtone_crc16_add_array(qrtone_crc16_t* this, const int8_t* data, const int32_t data_length) {
    int32_t i;
    uint16_t crcXor;
    uint16_t c;
    uint8_t j;
    for (i = 0; i < data_length; i++) {
        c = (this->crc16 ^ data[i]) & 0x00FF;
        crcXor = 0;
        for (j = 0; j < 8; j++) {
            if (((crcXor ^ c) & 0x0001) != 0) {
                crcXor = (crcXor >> 1) ^ 0xA001;
            }
            else {
                crcXor = crcXor >> 1;
            }
            c = c >> 1;
        }
        this->crc16 = this->crc16 >> 8 ^ crcXor;
    }
}

void qrtone_goertzel_init(qrtone_goertzel_t* this, double sample_rate, double frequency, int32_t window_size) {
        this->sample_rate = sample_rate;
        this->window_size = window_size;
        // Fix frequency using the sampleRate of the signal
        double samplingRateFactor = window_size / sample_rate;
        this->pik_term = QRTONE_2PI * (frequency * samplingRateFactor) / window_size;
        this->cos_pik_term2 = cos(this->pik_term) * 2.0;
        qrtone_goertzel_reset(this);
}

void qrtone_goertzel_reset(qrtone_goertzel_t* this) {
    this->s0 = 0;
    this->s1 = 0;
    this->s2 = 0;
    this->processed_samples = 0;
    this->last_sample = 0;
}

void qrtone_goertzel_process_samples(qrtone_goertzel_t* this, float* samples,int32_t samples_len) {
    if (this->processed_samples + samples_len <= this->window_size) {
        int32_t size;
        if (this->processed_samples + samples_len == this->window_size) {
            size = samples_len - 1;
            this->last_sample = samples[size];
        } else {
            size = samples_len;
        }
        int32_t i;
        for (i = 0; i < size; i++) {
            this->s0 = samples[i] + this->cos_pik_term2 * this->s1 - this->s2;
            this->s2 = this->s1;
            this->s1 = this->s0;
        }
        this->processed_samples += samples_len;
    }
}

double qrtone_goertzel_compute_rms(qrtone_goertzel_t* this) {
    // final computations
    this->s0 = this->last_sample + this->cos_pik_term2 * this->s1 - this->s2;

    qrtonecomplex cc = CX_EXP(NEW_CX(this->pik_term, 0));
    // complex multiplication substituting the last iteration
    // and correcting the phase for (potentially) non - integer valued
    // frequencies at the same time
    qrtonecomplex parta = CX_SUB(NEW_CX(this->s0, 0), CX_MUL(NEW_CX(this->s1, 0), cc));
    qrtonecomplex partb = CX_EXP(NEW_CX(this->pik_term * (this->window_size - 1.), 0));
    qrtonecomplex y = CX_MUL(parta, partb);
    // phase = atan2(y.i, y.r);
    // Compute RMS
    qrtone_goertzel_reset(this);
    return sqrt((y.r * y.r + y.i * y.i) * 2) / this->window_size;
}
