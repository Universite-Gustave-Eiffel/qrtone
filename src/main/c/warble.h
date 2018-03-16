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

enum WARBLE_FEED_RESULT { WARBLE_FEED_ERROR = -1, WARBLE_FEED_IDLE = 0, WARBLE_FEED_MESSAGE_COMPLETE = 1, WARBLE_FEED_DETECT_PITCH = 2 };

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
	double frequencies[WARBLE_PITCH_COUNT];            /**< Computed pitch frequencies length is WARBLE_PITCH_COUNT */
	int64_t triggerSampleIndex;     /**< Best Sample index of first trigger */
	int64_t triggerSampleIndexBegin;/**< Sample index begining of first trigger */
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
* @param sample_rate Sampling rate in Hz
* @param frequencies Array of frequency search in Hz
* @param f_length Size of frequencies
* @param[out] rms Rms power, must be allocated with the same size of frequencies
*/
void warble_generalized_goertzel(const double* signal, int32_t s_length, double sample_rate, const double* frequencies, int32_t f_length, double* rms);

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
 *  Initialize warble configuration object
 *  @param sample_rate Sampling rate of the signal
 *  @param first_frequency lowest frequency
 *	@param frequency_multiplication Increment factor between each word, 0 if usage of frequency_increment
 *  @param frequency_increment Increment between each word, 0 if usage of frequency_multiplication 
 *  @param word_time Pitch time length. Higher value is more robust but decrease bandwidth. Default is 0.05
 *  @param message_size Payload size in char.
 *  @param frequencies_index_triggers Frequency index (0-n) that triggers a series of pitch
 *  @param frequencies_index_triggers_length size of array frequencies_index_triggers
 */
void warble_init(warble* this, double sample_rate, double first_frequency,
	double frequency_multiplication,
	int16_t frequency_increment, double word_time,
	int32_t message_size, int16_t* frequencies_index_triggers, int16_t frequencies_index_triggers_length);

/**
* Free buffer in object
* @param warble Object to free
*/
void warble_free(warble *warble);

/**
* Analyse the provided spectrum
* @param warble Object
* @param signal Audio signal with cfg.sampleRate and the length of cfg.window_length
* @param sample_index Audio sample index of signal[0]. So that sample_index / cfg.sampleRate = time elapsed since beginning of feed. 
* @return 1 if the message can be collected using warble_GetPayload
*/
enum WARBLE_FEED_RESULT warble_feed(warble *warble, double* signal, int64_t sample_index);

/**
* Return the expected window size output of warble_generate_signal
* @param warble Object
*/
int32_t warble_generate_window_size(warble *warble);

/**
 * Generate an audio signal for the provided words and configuration
* @param warble Object
 */
void warble_generate_signal(warble *warble,double powerPeak, unsigned char* words, double* signal_out);

/*
 * Encode and interleave using reed solomon algorithm
 */
void warble_reed_encode_solomon(warble *warble, unsigned char* msg, unsigned char* block);

/*
 * deinterleave and decode using reed solomon algorithm
 * @return A positive number if decoded succeed
 */
int warble_reed_decode_solomon(warble *warble, unsigned char* words, unsigned char* msg);

void warble_swap_chars(char* input_string, int32_t* index, int32_t n);

void warble_unswap_chars(char* input_string, int32_t* index, int32_t n);

/**
 * Generate random numbers for the fisher yates shuffling
 */
void warble_fisher_yates_shuffle_index(int n, int* index);

int warble_rand(int64_t* next);

void warble_char_to_frequencies(warble *warble, uint8_t c, double* f0, double* f1);

int warble_get_highest_index(double* rms, const int from, const int to);

#ifdef __cplusplus
}
#endif

#endif