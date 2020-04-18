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

enum QRTONE_ECC_LEVEL { QRTONE_ECC_L = 0, QRTONE_ECC_M = 1, QRTONE_ECC_Q = 2, QRTONE_ECC_H = 3};

typedef struct _qrtone_t qrtone_t;

///////////////////////
// INITIALIZATION
///////////////////////

qrtone_t* qrtone_new(void);

void qrtone_init(qrtone_t* this, double sample_rate);

void qrtone_free(qrtone_t* this);

////////////////////////
// Receive payload
////////////////////////

int32_t qrtone_get_maximum_length(qrtone_t* this);

int8_t qrtone_push_samples(qrtone_t* this, float* samples, int32_t samples_length);

int8_t* qrtone_get_payload(qrtone_t* this);

int32_t qrtone_get_payload_length(qrtone_t* this);

int32_t qrtone_get_fixed_errors(qrtone_t* this);

///////////////////////////
// Send payload
///////////////////////////

void qrtone_get_samples(qrtone_t* this, float* samples, int32_t samples_length, int32_t offset, float power);

int32_t qrtone_set_payload(qrtone_t* this, int8_t* payload, uint8_t payload_length);

int32_t qrtone_set_payload_ext(qrtone_t* this, int8_t* payload, uint8_t payload_length, int8_t ecc_level, int8_t add_crc);

#ifdef __cplusplus
}
#endif

#endif