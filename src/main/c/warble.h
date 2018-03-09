/*
* BSD 3-Clause License
*
* Copyright (c) 2018, Ifsttar
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

#ifndef WARBLE_H
#define WARBLE_H

#ifdef __cplusplus
extern "C" {
#endif
	
#include <stdint.h>


#define WARBLE_PITCH_COUNT 32  // Number of used frequency bands
#define WARBLE_PITCH_ROOT 16   // column and rows that make a char

/**
* @struct  Warble
* @breif	Object to encapsulate the parameters for the generation and recognition of pitch sequence.
*/
typedef struct _warble {
	// Inputs
	double firstFrequency;          /**< Frequency of word 0 */
	double frequencyMultiplication; /**< Increment factor between each word, 0 if usage of frequencyIncrement */
	int16_t frequencyIncrement;     /**< Increment between each word, 0 if usage of frequencyMultiplication */
	int16_t payloadSize;            /**< Number of payload words */
	int16_t frequenciesIndexTriggersCount;       /**< Number of pitch that trigger the sequence of words */
	int16_t* frequenciesIndexTriggers;             /**< Word index that trigger the sequence of words */
	double sampleRate;				/**< Sample rate of audio in Hz */
	// Algorithm data
	unsigned char* parsed;          /**< parsed words of length wordTriggerCount+payloadSize+paritySize */
	double* frequencies;            /**< Computed pitch frequencies length is 32 */
	int64_t triggerSampleIndex;     /**< Sample index of first trigger */
	int32_t word_length;			/** pitch length in samples*/
} warble;

/**
* Goertzel algorithm - Compute the RMS power of the selected frequencies for the provided audio signals.
* http://asp.eurasipjournals.com/content/pdf/1687-6180-2012-56.pdf
* ipfs://QmdAMfyq71Fm72Rt5u1qtWM7teReGAHmceAtDN5SG4Pt22
* Sysel and Rajmic:Goertzel algorithm generalized to non-integer multiples of fundamental frequency. EURASIP Journal on Advances in Signal Processing 2012 2012:56.
* @param signal Audio signal
* @param s_length Audio signal array size
* @param sampleRate Sampling rate in Hz
* @param freqs Array of frequency search in Hz
* @param f_length Size of freqs
* @param[out] outFreqsPower Rms power, must be allocated with the same size of freqs
*/
void warble_generalized_goertzel(const double* signal, int32_t s_length, double sampleRate, const double* freqs, int32_t f_length, double* outFreqsPower);

/*
 * Compute RMS of the provided signal
 */
double warble_compute_rms(const double* signal, int32_t s_length);

/*
 * Initialize warble configuration object
 */
void warble_init(warble* warble, double sampleRate, double firstFrequency,
	double frequencyMultiplication,
	int16_t frequencyIncrement, double word_time,
	int16_t payloadSize, int16_t* frequenciesIndexTriggers, int16_t frequenciesIndexTriggersCount);

/**
* Free buffer in object
* @param warble Object to free
*/
void warble_free(warble *warble);

/**
* Analyse the provided spectrum
* @param warble Object
* @return 1 if the message can be collected using warble_GetPayload
*/
int16_t warble_feed(warble *warble, double* rms, int rms_size, double sample_rate, int64_t sample_index);

/**
 * Return the expected window size as input of warble_feed
* @param warble Object
 */
size_t warble_feed_window_size(warble *warble);

/**
* Return the expected window size output of warble_generate_signal
* @param warble Object
*/
size_t warble_generate_window_size(warble *warble);

/**
 * Generate an audio signal for the provided words and configuration
* @param warble Object
 */
void warble_generate_signal(warble *warble, unsigned char* words, double* signal_out);

/**
* @param warble Object
* @param payload
*/
void warble_GetPayload(warble *warble, unsigned char * payload);

#ifdef __cplusplus
}
#endif

#endif