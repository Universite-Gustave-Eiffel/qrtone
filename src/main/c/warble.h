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
	int32_t payloadSize;            /**< Number of payload words */
	int16_t frequenciesIndexTriggersCount;       /**< Number of pitch that trigger the sequence of words */
	int16_t* frequenciesIndexTriggers;             /**< Word index that trigger the sequence of words */
	double sampleRate;				/**< Sample rate of audio in Hz */
	// Algorithm data
	int32_t block_length;           /**< Number of words (payload+forward correction codes) */
	int32_t distance;               /**< Distance for reed-solomon error code */
	int32_t rs_message_length;      /**< Length of message attached to distance*/
	int32_t distance_last;          /**< Distance for reed-solomon error code on the last cutted message piece*/

	unsigned char* parsed;          /**< parsed words of length wordTriggerCount+payloadSize+paritySize */
	int32_t* shuffleIndex;		    /**< Shuffle index, used to (de)shuffle words sent/received after/before reed solomon */
	double* frequencies;            /**< Computed pitch frequencies length is WARBLE_PITCH_COUNT */
	int64_t triggerSampleIndex;     /**< Sample index of first trigger */
	double triggerSampleRMS;		/**< Highest RMS of first trigger */
	int32_t word_length;			/** pitch length in samples*/
	int32_t window_length;			/** Window length of the signal provided to warble_feed **/
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

/**
 * Convert spectrum to character
 * @param warble configuration
 * @param rms root mean square values of warble->frequencies
 */
unsigned char spectrumToChar(warble *warble, double* rms);

/*
 * Initialize warble configuration object
 *  @param firstFrequency lowest frequency
 *	@param frequencyMultiplication; /**< Increment factor between each word, 0 if usage of frequencyIncrement
 *  @param frequencyIncrement;     /**< Increment between each word, 0 if usage of frequencyMultiplication 
 *  @param message_size Payload size provided to warble_reed_encode_solomon.
 */
void warble_init(warble* this, double sampleRate, double firstFrequency,
	double frequencyMultiplication,
	int16_t frequencyIncrement, double word_time,
	int32_t message_size, int16_t* frequenciesIndexTriggers, int16_t frequenciesIndexTriggersCount);

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
int16_t warble_feed(warble *warble, double* signal, int64_t sample_index);

/**
* Return the expected window size output of warble_generate_signal
* @param warble Object
*/
size_t warble_generate_window_size(warble *warble);

/**
 * Generate an audio signal for the provided words and configuration
* @param warble Object
 */
void warble_generate_signal(warble *warble,double powerPeak, unsigned char* words, double* signal_out);

/*
 * Chuffle and encode using reed salomon algorithm
 */
void warble_reed_encode_solomon(warble *warble, unsigned char* msg, unsigned char* block);

/*
 * decode and reassemble using reed salomon algorithm
 */
void warble_reed_decode_solomon(warble *warble, unsigned char* words, unsigned char* msg);

/**
* @param warble Object
* @param payload
*/
void warble_GetPayload(warble *warble, unsigned char * payload);

void warble_swap_chars(unsigned char* input_string, int32_t* index, size_t n);

void warble_unswap_chars(char* input_string, int32_t* index, size_t n);
/**
 * Generate random numbers for the fisher yates shuffling
 */
void warble_fisher_yates_shuffle_index(int n, int* index);

#ifdef __cplusplus
}
#endif

#endif