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


#ifdef SIGN
#undef SIGN
#endif
#define SIGN(d) (d >= 0.0 ? 1 : -1)

#ifndef M_PI
#define M_PI 3.1415926535897932385
#endif

typedef struct _qrtonecomplex
{
    double r;
    double i;
} qrtonecomplex;


struct _qrtonecomplex NEW_CX(double r, double i) {
    qrtonecomplex ret;
    ret.r = r;
    ret.i = i;
    return ret;
}

struct _qrtonecomplex CX_ADD(const qrtonecomplex c1, const qrtonecomplex c2) {
    qrtonecomplex ret;
    ret.r = c1.r + c2.r;
    ret.i = c1.i + c2.i;
    return ret;
}

struct _qrtonecomplex CX_SUB(const qrtonecomplex c1, const qrtonecomplex c2) {
    qrtonecomplex ret;
    ret.r = c1.r - c2.r;
    ret.i = c1.i - c2.i;
    return ret;
}

struct _qrtonecomplex CX_MUL(const qrtonecomplex c1, const qrtonecomplex c2) {
    qrtonecomplex ret;
    ret.r = c1.r * c2.r - c1.i * c2.i;
    ret.i = c1.r * c2.i + c1.i * c2.r;
    return ret;
}

struct _qrtonecomplex CX_EXP(const qrtonecomplex c1) {
    qrtonecomplex ret;
    ret.r = cos(c1.r);
    ret.i = -sin(c1.r);
    return ret;
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

/**
 * Simple bubblesort, because bubblesort is efficient for small count, and count is likely to be small
 * https://github.com/absmall/p2
 *
 * @author Aaron Small
 */
void qrtone_percentile_sort(double* q, int32_t q_length) {
    double k;
    int32_t i = 0;
    int32_t j = 0;
    for (j = 1; j < q_length; j++) {
        k = q[j];
        i = j - 1;

        while (i >= 0 && q[i] > k) {
            q[i + 1] = q[i];
            i--;
        }
        q[i + 1] = k;
    }
}


/**
 * @author Aaron Small
 */
void qrtone_percentile_update_markers(qrtone_percentile_t* this) {
    qrtone_percentile_sort(this->dn, this->marker_count);

    int32_t i;
    /* Then entirely reset np markers, since the marker count changed */
    for (i = 0; i < this->marker_count; i++) {
        this->np[i] = ((size_t)this->marker_count - 1) * this->dn[i] + 1;
    }
}


/**
 * @author Aaron Small
 */
void qrtone_percentile_add_end_markers(qrtone_percentile_t* this) {
    this->marker_count = 2;
    this->q = malloc(sizeof(double) * this->marker_count);
    this->dn = malloc(sizeof(double) * this->marker_count);
    this->np = malloc(sizeof(double) * this->marker_count);
    this->n = malloc(sizeof(double) * this->marker_count);
    memset(this->q, 0, sizeof(double) * this->marker_count);
    memset(this->dn, 0, sizeof(double) * this->marker_count);
    memset(this->np, 0, sizeof(double) * this->marker_count);
    memset(this->n, 0, sizeof(double) * this->marker_count);
    this->dn[0] = 0.0;
    this->dn[1] = 1.0;
    qrtone_percentile_update_markers(this);
}


/**
 * @author Aaron Small
 */
void qrtone_percentile_init(qrtone_percentile_t* this) {
    this->count = 0;
    qrtone_percentile_add_end_markers(this);
}


/**
 * @author Aaron Small
 */
int32_t qrtone_percentile_allocate_markers(qrtone_percentile_t* this, int32_t count) {
    double* new_q = malloc(sizeof(double) * ((size_t)this->marker_count + (size_t)count));
    double* new_dn = malloc(sizeof(double) * ((size_t)this->marker_count + (size_t)count));
    double* new_np = malloc(sizeof(double) * ((size_t)this->marker_count + (size_t)count));
    int32_t* new_n = malloc(sizeof(int32_t) * ((size_t)this->marker_count + (size_t)count));

    memset(new_q + this->marker_count, 0, sizeof(double) * count);
    memset(new_dn + this->marker_count, 0, sizeof(double) * count);
    memset(new_np + this->marker_count, 0, sizeof(double) * count);
    memset(new_n + this->marker_count, 0, sizeof(int32_t) * count);

    memcpy(new_q, this->q, sizeof(double) * this->marker_count);
    memcpy(new_dn, this->dn, sizeof(double) * this->marker_count);
    memcpy(new_np, this->np, sizeof(double) * this->marker_count);
    memcpy(new_n, this->n, sizeof(int32_t) * this->marker_count);

    free(this->q);
    free(this->dn);
    free(this->np);
    free(this->n);

    this->q = new_q;
    this->dn = new_dn;
    this->np = new_np;
    this->n = new_n;

    this->marker_count += count;

    return this->marker_count - count;
}

/**
 * @author Aaron Small
 */
void qrtone_percentile_add_quantile(qrtone_percentile_t* this, double quant) {
    int32_t index = qrtone_percentile_allocate_markers(this, 3);

    /* Add in appropriate dn markers */
    this->dn[index] = quant / 2.0;
    this->dn[index + 1] = quant;
    this->dn[index + 2] = (1.0 + quant) / 2.0;

    qrtone_percentile_update_markers(this);
}

/**
 * P^2 algorithm as documented in "The P-Square Algorithm for Dynamic Calculation of Percentiles and Histograms
 * without Storing Observations," Communications of the ACM, October 1985 by R. Jain and I. Chlamtac.
 * Converted from Aaron Small C code under MIT License
 * https://github.com/absmall/p2
 *
 * @author Aaron Small
 */
void qrtone_percentile_init_quantile(qrtone_percentile_t* this, double quant) {
    if (quant >= 0 && quant <= 1) {
        this->count = 0;
        qrtone_percentile_add_end_markers(this);
        qrtone_percentile_add_quantile(this, quant);
    }
}

double qrtone_percentile_linear(qrtone_percentile_t* this, int32_t i, int32_t d) {
    return this->q[i] + d * (this->q[i + d] - this->q[i]) /((size_t)this->n[i + d] - this->n[i]);
}

double qrtone_percentile_parabolic(qrtone_percentile_t* this, int32_t i, int32_t d) {
    return this->q[i] + d / (double)((size_t)this->n[i + 1] - this->n[i - 1]) * (((size_t)this->n[i] - this->n[i - 1] + d) * (this->q[i + 1] - this->q[i]) /
        ((size_t)this->n[i + 1] - this->n[i]) + ((size_t)this->n[i + 1] - this->n[i] - d) * (this->q[i] - this->q[i - 1]) / ((size_t)this->n[i] - this->n[i - 1]));
}

void qrtone_percentile_add(qrtone_percentile_t* this, double data) {
    int32_t i;
    int32_t k = 0;
    double d;
    double newq;

    if (this->count >= this->marker_count) {
        this->count++;

        // B1
        if (data < this->q[0]) {
            this->q[0] = data;
            k = 1;
        }
        else if (data >= this->q[this->marker_count - 1]) {
            this->q[this->marker_count - 1] = data;
            k = this->marker_count - 1;
        }
        else {
            for (i = 1; i < this->marker_count; i++) {
                if (data < this->q[i]) {
                    k = i;
                    break;
                }
            }
        }

        // B2
        for (i = k; i < this->marker_count; i++) {
            this->n[i]++;
            this->np[i] = this->np[i] + this->dn[i];
        }
        for (i = 0; i < k; i++) {
            this->np[i] = this->np[i] + this->dn[i];
        }

        // B3
        for (i = 1; i < this->marker_count - 1; i++) {
            d = this->np[i] - this->n[i];
            if ((d >= 1.0 && this->n[i + 1] - this->n[i] > 1) || (d <= -1.0 && this->n[i - 1] - this->n[i] < -1)) {
                newq = qrtone_percentile_parabolic(this, i, SIGN(d));
                if (this->q[i - 1] < newq && newq < this->q[i + 1]) {
                    this->q[i] = newq;
                }
                else {
                    this->q[i] = qrtone_percentile_linear(this, i, SIGN(d));
                }
                this->n[i] += SIGN(d);
            }
        }
    }
    else {
        this->q[this->count] = data;
        this->count++;

        if (this->count == this->marker_count) {
            // We have enough to start the algorithm, initialize
            qrtone_percentile_sort(this->q, this->marker_count);

            for (i = 0; i < this->marker_count; i++) {
                this->n[i] = i + 1;
            }
        }
    }
}