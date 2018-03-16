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

#include "warble.h"
#include "math.h"
#include <stdlib.h>
#include <string.h>
#include "warble_complex.h"
#include "correct/reed-solomon.h"

#define WARBLE_2PI 6.283185307179586
#define PITCH_SIGNAL_TO_NOISE_TRIGGER 3 // Accept pitch if pitch power is less than 3dB lower than signal level

// In order to fix at least 20% of error, the maximum message length attached to a reed solomon(255,32) code is 10 Bytes for a 8 distance (can correct up to 2 Bytes)
#define WARBLE_RS_P 10
#define WARBLE_RS_DISTANCE 8

// Pseudo random generator
int warble_rand(int64_t* next) {
	*next = *next * 1103515245 + 12345;
	return (unsigned int)(*next / 65536) % 32768;
}

// randomly swap specified integer
void warble_fisher_yates_shuffle_index(int n, int* index) {
	int i;
	int64_t rnd_cache = n;
	for (i = n - 1; i > 0; i--) {
		index[n - 1 - i] = warble_rand(&rnd_cache) % (i + 1);
	}
}

void warble_generalized_goertzel(const double* signal, int32_t s_length,double sampleRate,const double* freqs, int32_t f_length, double* outFreqsPower) {
	int32_t id_freq;
	// Fix frequency using the sampleRate of the signal
	double samplingRateFactor = s_length / sampleRate;
	// Computation via second-order system
	for(id_freq = 0; id_freq < f_length; id_freq++) {
		// for a single frequency :
		// precompute the constants
		double pik_term = 2 * M_PI *(freqs[id_freq] * samplingRateFactor) / (s_length);
		double cc_real = cos(pik_term);
		double cos_pik_term2 = cos(pik_term) * 2;
		warblecomplex cc = CX_EXP(NEW_CX(pik_term, 0));
		// state variables
		double s0 = 0.;
		double s1 = 0.;
		double s2 = 0.;
		int32_t ind;
		// 'main' loop
		// number of iterations is (by one) less than the length of signal
		for(ind=0; ind < s_length - 1; ind++) {
			s0 = signal[ind] + cos_pik_term2 * s1 - s2;
			s2 = s1;
			s1 = s0;
		}
		// final computations
		s0 = signal[s_length - 1] + cos_pik_term2 * s1 - s2;

		// complex multiplication substituting the last iteration
		// and correcting the phase for (potentially) non - integer valued
		// frequencies at the same time
		warblecomplex parta = CX_SUB(NEW_CX(s0, 0), CX_MUL(NEW_CX(s1, 0), cc));
		warblecomplex partb = CX_EXP(NEW_CX(pik_term * (s_length - 1.), 0));
		warblecomplex y = CX_MUL(parta , partb);
		// Compute RMS
		outFreqsPower[id_freq] = sqrt((y.r * y.r + y.i * y.i ) * 2) / s_length;
	}
}

double warble_compute_rms(const double* signal, int32_t s_length) {
	double sum = 0;
	int32_t i;
	for(i=0; i < s_length; i++) {
		sum += signal[i] * signal[i];
	}
	return sqrt(sum / s_length);
}

// Used by Java binding
warble* warble_create() {
	warble* this = (warble*)malloc(sizeof(warble));
	return this;
}

int32_t warble_reed_solomon_distance(int32_t length) {
	return (int32_t)max(4, min(WARBLE_RS_DISTANCE, pow(2, round(log(length / 3.) / log(2)))));
}

void warble_init(warble* this, double sampleRate, double firstFrequency,
	double frequencyMultiplication,
	int16_t frequencyIncrement, double word_time,
	int32_t payloadSize, int16_t* frequenciesIndexTriggers, int16_t frequenciesIndexTriggersCount)  {
	this->sampleRate = sampleRate;
	this->payloadSize = payloadSize;
	this->distance = warble_reed_solomon_distance(this->payloadSize);
	if(this->payloadSize > WARBLE_RS_P && this->distance % WARBLE_RS_P > 0) {
		// The last cutted message is smaller than WARBLE_RS_P
		// So we adapt the last cutted fec in order to reduce the global length
		this->distance_last = warble_reed_solomon_distance(this->distance % WARBLE_RS_P);
		int remain_chars = this->payloadSize % WARBLE_RS_P;
		this->block_length = (WARBLE_RS_P + this->distance) * (this->payloadSize / WARBLE_RS_P) + remain_chars + this->distance_last;
		this->rs_message_length = WARBLE_RS_P;
	} else {
		// Cutted message length is the same. Or it is not cutted
		this->distance_last = this->distance;
		this->block_length = (this->payloadSize + this->distance) * max(1, this->payloadSize / WARBLE_RS_P);
		this->rs_message_length = this->payloadSize > WARBLE_RS_P ? WARBLE_RS_P : this->payloadSize;
	}
	this->word_length = (int32_t)(sampleRate * word_time);
	// Increase probability to have a window begining at 1/3 of the pitch (capture the lob peak)
	this->window_length = (int32_t)((sampleRate * (word_time / 3.)));
	this->frequenciesIndexTriggersCount = frequenciesIndexTriggersCount;
	this->frequenciesIndexTriggers = malloc(sizeof(int16_t) * frequenciesIndexTriggersCount);
	memcpy(this->frequenciesIndexTriggers, frequenciesIndexTriggers, sizeof(int16_t) * frequenciesIndexTriggersCount);
	this->triggerSampleIndex = -1;
	//this->paritySize =  wordSize - 1 - payloadSize / 2;
	this->parsed = malloc(sizeof(unsigned char) * (this->block_length) + 1);
	memset(this->parsed, 0, sizeof(unsigned char) * (this->block_length) + 1);
	int i;
	// Precompute pitch frequencies
	for(i = 0; i < WARBLE_PITCH_COUNT; i++) {
		if(frequencyIncrement != 0) {
			this->frequencies[i] = firstFrequency + i * frequencyIncrement;
		} else {
			this->frequencies[i] = firstFrequency * pow(frequencyMultiplication, i);
		}
	}
	// Compute index shuffling of messages
	this->shuffleIndex = malloc(sizeof(int) * this->block_length);
	for(i = 0; i < this->block_length; i++) {
		this->shuffleIndex[i] = i;
	}
	warble_fisher_yates_shuffle_index(this->block_length, this->shuffleIndex);
}

void warble_free(warble *warble) {
	free(warble->frequenciesIndexTriggers);
    free(warble->parsed);
	free(warble->shuffleIndex);
}

int warble_is_triggered(warble *warble, const double* signal,int signal_length, int32_t wordIndex,double* triggerRMS) {
	double trigger[] = {warble->frequencies[warble->frequenciesIndexTriggers[wordIndex]] };
	double rms[1] = { 0 };
	double signalRMS = warble_compute_rms(signal, warble->window_length);
	warble_generalized_goertzel(signal, signal_length, warble->sampleRate, trigger, 1, rms);
	const double splSignal = 20 * log10(signalRMS);
	const double splPitch = 20 * log10(rms[0]);
	if(triggerRMS) {
		*triggerRMS = rms[0];
	}
	return splSignal - splPitch < PITCH_SIGNAL_TO_NOISE_TRIGGER;
}

int warble_get_highest_index(double* rms, const int from, const int to) {
	int highest = from;
	int i;
	for(i=from + 1; i < to; i++) {
		if(rms[i] > rms[highest]) {
			highest = i;
		}
	}
	return highest;
}

void warble_clean_parse(warble* warble) {
	warble->triggerSampleIndexBegin = -1;
	warble->triggerSampleRMS = -1;
}

unsigned char spectrumToChar(warble *warble, double* rms) {
	int f0index = warble_get_highest_index(rms, 0, WARBLE_PITCH_ROOT);
	int f1index = warble_get_highest_index(rms, WARBLE_PITCH_ROOT, WARBLE_PITCH_COUNT) - WARBLE_PITCH_ROOT;
	return  (unsigned char)(f1index * WARBLE_PITCH_ROOT + f0index);
}

enum WARBLE_FEED_RESULT warble_feed(warble *warble, double* signal, int64_t sample_index) {
	// If we are still on the first trigger time
	int wordIndex = warble->triggerSampleIndexBegin < 0 ? -1 : (int)((sample_index - warble->triggerSampleIndexBegin + (warble->window_length / 2)) / (double)warble->word_length);
	if(wordIndex < 0 || wordIndex == 0) {
		// Looking for start of pitch
		double triggerRMS;
		if(warble_is_triggered(warble, signal, warble->window_length, 0, &triggerRMS)) {
			// Accept pitch for first gate when the power is at maximum level
			if(warble->triggerSampleIndexBegin < 0 || warble->triggerSampleRMS < triggerRMS) {
				if(warble->triggerSampleIndexBegin < 0) {
					warble->triggerSampleIndexBegin = sample_index;
				}
				warble->triggerSampleIndex = sample_index;
				warble->triggerSampleRMS = triggerRMS;
				return WARBLE_FEED_DETECT_PITCH;
			}
		}
	} else {
		if(wordIndex > warble->block_length + warble->frequenciesIndexTriggersCount) {
			warble_clean_parse(warble);
			return WARBLE_FEED_ERROR; // we have an issue here
		}
		// Target pitch contain the pitch peak
		int64_t targetPitch = warble->triggerSampleIndex + warble->word_length * wordIndex + (warble->window_length / 2);

		// If the peak is contain in this window
		if(targetPitch >= sample_index && targetPitch < sample_index + warble->window_length) {
			double rms[WARBLE_PITCH_COUNT];
			if(wordIndex < warble->frequenciesIndexTriggersCount) {
				// Still in trigger pitch
				warble_generalized_goertzel(signal, warble->window_length, warble->sampleRate, warble->frequencies, WARBLE_PITCH_COUNT, rms);
				if (warble_get_highest_index(rms, WARBLE_PITCH_ROOT, WARBLE_PITCH_COUNT) != warble->frequenciesIndexTriggers[wordIndex]) {
					// Fail to recognize expected pitch
					// Quit pitch, and wait for a new fist trigger
					warble_clean_parse(warble);
					return WARBLE_FEED_ERROR;
				}		
			} else {
				warble_generalized_goertzel(signal, warble->window_length, warble->sampleRate, warble->frequencies, WARBLE_PITCH_COUNT, rms);
				warble->parsed[wordIndex - warble->frequenciesIndexTriggersCount] = spectrumToChar(warble, rms);
				if(wordIndex == warble->block_length + warble->frequenciesIndexTriggersCount - 1) {
					warble_clean_parse(warble);
					return WARBLE_FEED_MESSAGE_COMPLETE;
				}
				return WARBLE_FEED_DETECT_PITCH;
			}
		}	
	}
	return WARBLE_FEED_IDLE;
}

int32_t warble_feed_window_size(warble *warble) {
	return warble->window_length;
}

int32_t warble_generate_window_size(warble *warble) {
	return (warble->frequenciesIndexTriggersCount + warble->block_length) * warble->word_length;
}

void warble_generate_pitch(double* signal_out, int32_t length, double sample_rate, double frequency, double power_peak) {
	int32_t i;
	double t_step = 1 / sample_rate;
	for(i=0; i < length; i++) {
		// Hann windowing function
		// TODO https://en.wikipedia.org/wiki/Window_function#Flat_top_window
		const double window = 0.5 * (1 - cos((2 * M_PI * i) / (length - 1)));
		signal_out[i] += sin(i * t_step * WARBLE_2PI * frequency) * power_peak * window;
	}
}

void warble_swap_chars(char* input_string, int32_t* index, int32_t n) {
	int32_t i;
	for (i = n - 1; i > 0; i--)
	{
		int v = index[n - 1 - i];
		char tmp = input_string[i];
		input_string[i] = input_string[v];
		input_string[v] = tmp;
	}
}

void warble_unswap_chars(char* input_string, int32_t* index, int32_t n) {
	int32_t i;
	for (i = 1; i < n; i++) {
		int v = index[n - i - 1];
		char tmp = input_string[i];
		input_string[i] = input_string[v];
		input_string[v] = tmp;
	}
}


void warble_reed_encode_solomon(warble *warble, unsigned char* msg, unsigned char* words) {
	// Split message if its size > WARBLE_RS_P
	int msg_cursor;
	int block_cursor = 0;
	int remaining = warble->payloadSize % warble->rs_message_length;
	correct_reed_solomon *rs = correct_reed_solomon_create(
		correct_rs_primitive_polynomial_ccsds, 1, 1, warble->distance);
	for(msg_cursor = 0; msg_cursor < warble->payloadSize - remaining; msg_cursor += warble->rs_message_length) {
		int32_t res = (int32_t)correct_reed_solomon_encode(rs, &(msg[msg_cursor]), warble->rs_message_length, &(words[block_cursor]));
		block_cursor += warble->rs_message_length + warble->distance;
	}
	correct_reed_solomon_destroy(rs);
	// Forward error correction on the remaining message chars
	if(remaining > 0) {
		rs = correct_reed_solomon_create(
			correct_rs_primitive_polynomial_ccsds, 1, 1, warble->distance_last);
		int32_t res = (int32_t)correct_reed_solomon_encode(rs, &(msg[warble->payloadSize - remaining]), remaining, &(words[warble->block_length - remaining - warble->distance_last]));
		correct_reed_solomon_destroy(rs);
	}
	if(warble->payloadSize > warble->rs_message_length) {
		// Interleave message in order to spread consecutive errors on multiple reed solomon messages (increase robustness)
		warble_swap_chars(words, warble->shuffleIndex, warble->block_length);
	}
}

int warble_reed_decode_solomon(warble *warble, unsigned char* words, unsigned char* msg) {
	int32_t res;
	int msg_cursor;
	int block_cursor = 0;
	int remaining = warble->payloadSize % warble->rs_message_length;
	if (warble->payloadSize > warble->rs_message_length) {
		// Deinterleave message
		warble_unswap_chars(words, warble->shuffleIndex, warble->block_length);
	}
	correct_reed_solomon *rs = correct_reed_solomon_create(
		correct_rs_primitive_polynomial_ccsds, 1, 1, warble->distance);
	for (msg_cursor = 0; msg_cursor < warble->payloadSize - remaining; msg_cursor += warble->rs_message_length) {
		res = (int)correct_reed_solomon_decode(rs, &(words[block_cursor]), warble->rs_message_length+ warble->distance, &(msg[msg_cursor]));
		if(res < 0) {
			correct_reed_solomon_destroy(rs);
			return res;
		}
		block_cursor += warble->rs_message_length + warble->distance;
	}
	correct_reed_solomon_destroy(rs);
	// Forward error correction on the remaining message chars
	if (remaining > 0) {
		rs = correct_reed_solomon_create(
			correct_rs_primitive_polynomial_ccsds, 1, 1, warble->distance_last);
		res = (int32_t)correct_reed_solomon_decode(rs, &(words[warble->block_length - remaining - warble->distance_last]), remaining + warble->distance_last, &(msg[warble->payloadSize - remaining]));
		correct_reed_solomon_destroy(rs);
		return res;
	}
	return res;
}

void warble_char_to_frequencies(warble *warble, uint8_t c, double* f0, double* f1) {
	*f0 = warble->frequencies[c % WARBLE_PITCH_ROOT];
	*f1 = warble->frequencies[WARBLE_PITCH_ROOT + c / WARBLE_PITCH_ROOT];
}

void warble_generate_signal(warble *warble,double power_peak, unsigned char* words, double* signal_out) {
	int s = 0;
	int i;
	// Triggers signal
	for(i=0; i<warble->frequenciesIndexTriggersCount; i++) {
		warble_generate_pitch(&(signal_out[s]), warble->word_length, warble->sampleRate, warble->frequencies[warble->frequenciesIndexTriggers[i]], power_peak);
		s += warble->word_length;
	}

	// Other pitchs

	for(i=0; i < warble->block_length; i++) {
		double freq1,freq2;
		warble_char_to_frequencies(warble, words[i], &freq1, &freq2);
		warble_generate_pitch(&(signal_out[s]), warble->word_length, warble->sampleRate, freq1, power_peak);
		warble_generate_pitch(&(signal_out[s]), warble->word_length, warble->sampleRate, freq2, power_peak);
		s += warble->word_length;
	}
}