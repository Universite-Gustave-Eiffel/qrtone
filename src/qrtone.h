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
 * @file qrtone.h
 * @author Nicolas Fortin @NicolasCumu
 * @date 24/03/2020
 * @brief Api of QRTone library
 * Usage
 * Audio to Message:
 * 1. Declare instance of qrtone_t with qrtone_new
 * 2. Init with qrtone_init
 * 3. Get maximal expected window length with qrtone_get_maximum_length
 * 4. Push window samples with qrtone_push_samples
 * 5. When qrtone_push_samples return 1 then retrieve payload with qrtone_get_payload and qrtone_get_payload_length
 * Message to Audio
 * 1. Declare instance of qrtone_t with qrtone_new
 * 2. Init with qrtone_init
 * 3. Set message with qrtone_set_payload or qrtone_set_payload_ext
 * 4. Retrieve audio samples to send with qrtone_get_samples
 */

#ifndef QRTONE_H
#define QRTONE_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

/**
 * Error correction level parameter
 *  L ecc level 7% error correction level
 *  M ecc level 15% error correction level
 *  Q ecc level 25% error correction level
 *  H ecc level 30% error correction level
 */
enum QRTONE_ECC_LEVEL { QRTONE_ECC_L = 0, QRTONE_ECC_M = 1, QRTONE_ECC_Q = 2, QRTONE_ECC_H = 3};

/**
 * @brief Main QRTone structure
 */
typedef struct _qrtone_t qrtone_t;

///////////////////////
// INITIALIZATION
///////////////////////

/**
 * Allocation memory for a qrtone_t instance.
 * @return A pointer to the qrtone structure.
 */
qrtone_t* qrtone_new(void);

/**
 * Initialization of the internal attributes of a qrtone_t instance. Must only be called once.
 * @param qrtone A pointer to the qrtone structure.
 * @param sample_rate Sample rate in Hz.
 */
void qrtone_init(qrtone_t* qrtone, double sample_rate);

/**
 * Free allocated memory for a qrtone_t instance.
 * @param qrtone A pointer to the initialized qrtone structure.
 */
void qrtone_free(qrtone_t* qrtone);

////////////////////////
// Receive payload
////////////////////////

/**
 * Compute the maximum samples_length to feed with the method `qrtone_push_samples`
 * @param qrtone A pointer to the initialized qrtone structure.
 * @return The maximum samples_length to feed with the method `qrtone_push_samples`
 */
int32_t qrtone_get_maximum_length(qrtone_t* qrtone);

/**
 * Process audio samples in order to find payload in tones.
 * @param qrtone A pointer to the initialized qrtone structure.
 * @param samples Audio samples array in float. All tests have been done with values between -1 and 1.
 * @param samples_length Size Audio samples array. The size should be inferior or equal to `qrtone_get_maximum_length`.
 * @return 1 if a payload has been received, 0 otherwise.
 */
int8_t qrtone_push_samples(qrtone_t* qrtone, float* samples, int32_t samples_length);

/**
 * Fetch stored payload. Call this function only when `qrtone_push_samples` return 1.
 * @param qrtone A pointer to the initialized qrtone structure.
 * @return int8_t array of the size provided by `qrtone_get_payload_length`. QRTone is responsible for freeing this array.
 */
int8_t* qrtone_get_payload(qrtone_t* qrtone);

/**
 * Get stored payload length. Call this function only when `qrtone_push_samples` return 1.
 * @param qrtone A pointer to the initialized qrtone structure.
 * @return int8_t stored payload length.
 */
int32_t qrtone_get_payload_length(qrtone_t* qrtone);

/**
 * When there is not enough signal/noise ratio, some bytes could be error corrected by Reed-Solomon code.
 * @param qrtone A pointer to the initialized qrtone structure.
 * @return Number of fixed
 */

int32_t qrtone_get_fixed_errors(qrtone_t* qrtone);

/**
 * Function callback called while awaiting a message. It can be usefull in order to display if the microphone is working.
 * @ptr Pointer provided when calling qrtone_tone_set_level_callback.
 * @global_level Leq of signal. Expressed in dBFS (https://en.wikipedia.org/wiki/DBFS)
 * @first_tone_level Level of first tone frequency. Expressed in dBFS.
 * @second_tone_level Level of second tone frequency. Expressed in dBFS.
 */
typedef void (*qrtone_level_callback_t)(void *ptr, float global_level, float first_tone_level, float second_tone_level);

/**
 * @brief Set callback method called while awaiting a message.
 * 
 * @param qrtone A pointer to the initialized qrtone structure.
 * @param data ptr to use when calling the callback method
 * @param lvl_callback Pointer to the method to call
 */
void qrtone_set_level_callback(qrtone_t* self, void* data, qrtone_level_callback_t lvl_callback);
///////////////////////////
// Send payload
///////////////////////////

/**
 * Set the message to send. With QRTONE_ECC_Q ECC level and with a CRC code.
 * @param qrtone A pointer to the initialized qrtone structure.
 * @param payload Byte array to send.
 * @param payload Byte array length. Should be less than 255 bytes.
 * @return The number of audio samples to send.
 */
int32_t qrtone_set_payload(qrtone_t* qrtone, int8_t* payload, uint8_t payload_length);

/**
 * Set the message to send, with additional parameters.
 * @param qrtone A pointer to the initialized qrtone structure.
 * @param payload Byte array to send.
 * @param payload Byte array length. Should be less than 255 bytes.
 * @param ecc_level Error correction level `QRTONE_ECC_LEVEL`. Error correction level add robustness at the cost of tone length.
 * @param add_crc If 1 ,add a crc16 code in order to check if the message has not been altered on the receiver side.
 * @return The number of audio samples to send.
 */
int32_t qrtone_set_payload_ext(qrtone_t* qrtone, int8_t* payload, uint8_t payload_length, int8_t ecc_level, int8_t add_crc);

/**
 * Populate the provided array with audio samples
 * @param qrtone A pointer to the initialized qrtone structure.
 * @param samples Pre-allocated array of samples_length length
 * @param samples_length array length. samples_length + offset should be equal or inferior than the total number of audio samples.
 * @param offset Cursor of the audio samples. Must be positive.
 * @param power Amplitude of the audio tones.
 */
void qrtone_get_samples(qrtone_t* qrtone, float* samples, int32_t samples_length, int32_t offset, float power);

#ifdef __cplusplus
}
#endif

#endif