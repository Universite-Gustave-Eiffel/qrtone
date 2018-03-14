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
#define PERCENT_CODE_ERROR 12.0

// Pseudo random generator
int warble_rand(int64_t* next) {
	*next = *next * 1103515245 + 12345;
	return (unsigned int)(*next / 65536) % 32768;
}

// randomly swap specified integer
void warble_fisher_yates_shuffle_index(int n, int* index) {
	int i;
	int cache;
	int64_t rnd_cache = n;
	for(i = n - 1; i > 0; i--) {
		int j = warble_rand(&rnd_cache) % (i + 1);
		cache = index[j];
		index[j] = index[i];
		index[i] = cache;
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

warble* warble_create() {
	warble* this = (warble*)malloc(sizeof(warble));
	return this;
}

void warble_init(warble* this, double sampleRate, double firstFrequency,
	double frequencyMultiplication,
	int16_t frequencyIncrement, double word_time,
	int32_t payloadSize, int16_t* frequenciesIndexTriggers, int16_t frequenciesIndexTriggersCount)  {
	this->sampleRate = sampleRate;
	this->payloadSize = payloadSize;
	int16_t distance = payloadSize + (int16_t)pow(2, round(log(payloadSize / PERCENT_CODE_ERROR) / log(2)));
	this->block_length = this->payloadSize + distance;
	this->word_length = (int32_t)(sampleRate * word_time);
	this->window_length = (int32_t)(this->word_length / 2);
	this->frequenciesIndexTriggersCount = frequenciesIndexTriggersCount;
	this->frequenciesIndexTriggers = malloc(sizeof(int16_t) * frequenciesIndexTriggersCount);
	memcpy(this->frequenciesIndexTriggers, frequenciesIndexTriggers, sizeof(int16_t) * frequenciesIndexTriggersCount);
	this->triggerSampleIndex = -1;
	//this->paritySize =  wordSize - 1 - payloadSize / 2;
	this->parsed = malloc(sizeof(unsigned char) * (this->block_length) + 1);
	memset(this->parsed, 0, sizeof(unsigned char) * (this->block_length) + 1);
	this->frequencies = malloc(sizeof(double) * WARBLE_PITCH_COUNT);
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
	free(warble->frequencies);
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

unsigned char spectrumToChar(warble *warble, double* rms) {
	int f0index = warble_get_highest_index(rms, 0, WARBLE_PITCH_ROOT);
	int f1index = warble_get_highest_index(rms, WARBLE_PITCH_ROOT, WARBLE_PITCH_COUNT) - WARBLE_PITCH_ROOT;
	return  (unsigned char)(f1index * WARBLE_PITCH_ROOT + f0index);
}

int16_t warble_feed(warble *warble, double* signal, int64_t sample_index) {
	if(warble->triggerSampleIndex < 0 || sample_index - warble->triggerSampleIndex <= warble->word_length) {
		// Looking for start of pitch
		double triggerRMS;
		if(warble_is_triggered(warble, signal, warble->window_length, 0, &triggerRMS)) {
			// Accept pitch for first gate when the power is at maximum level
			if(warble->triggerSampleIndex < 0 || warble->triggerSampleRMS < triggerRMS) {
				warble->triggerSampleIndex = sample_index;
				warble->triggerSampleRMS = triggerRMS;
			}
		}
	} else {
		int wordIndex = (int)((sample_index - warble->triggerSampleIndex + warble->window_length / 2) / (double)warble->word_length);
		if(wordIndex > warble->payloadSize + warble->frequenciesIndexTriggersCount) {
			warble->triggerSampleIndex = -1;
			return 0; // we have an issue here
		}

		int64_t startPitch = warble->triggerSampleIndex + warble->word_length * wordIndex;
		int64_t endPitch = startPitch + warble->window_length;

		int windowStartDiff = (int)(startPitch - sample_index);

		if(windowStartDiff > 0 && windowStartDiff < warble->window_length && sample_index + warble->window_length <= endPitch) {
			if(wordIndex < warble->frequenciesIndexTriggersCount) {
				// Still in trigger pitch
				if (!warble_is_triggered(warble, &(signal[windowStartDiff]), warble->window_length - windowStartDiff, wordIndex, NULL)) {
					// Fail to recognize expected pitch
					// Quit pitch, and wait for a new fist trigger
					warble->triggerSampleIndex = -1;
				}		
			} else {
				double rms[WARBLE_PITCH_COUNT];
				warble_generalized_goertzel(&(signal[windowStartDiff]), warble->window_length - windowStartDiff, warble->sampleRate, warble->frequencies, WARBLE_PITCH_COUNT, rms);

				warble->parsed[wordIndex - warble->frequenciesIndexTriggersCount] = spectrumToChar(warble, rms);
				if(wordIndex == warble->payloadSize + warble->frequenciesIndexTriggersCount - 1) {
					warble->triggerSampleIndex = -1;
					return 1;
				}
			}
		}	
	}
	return 0;
}

size_t warble_feed_window_size(warble *warble) {
	return 0;
}

size_t warble_generate_window_size(warble *warble) {
	return (warble->frequenciesIndexTriggersCount + warble->payloadSize) * warble->word_length;
}

warble_generate_pitch(double* signal_out, int32_t length, double sample_rate, double frequency, double power_peak) {
	int32_t i;
	double t_step = 1 / sample_rate;
	for(i=0; i < length; i++) {
		signal_out[i] += sin(i * t_step * WARBLE_2PI * frequency) * power_peak;
	}
}

void warble_reed_encode_solomon(warble *warble, unsigned char* msg, size_t msg_length, unsigned char* words) {
	size_t min_distance = warble->block_length - warble->payloadSize;
	correct_reed_solomon *rs = correct_reed_solomon_create(
		correct_rs_primitive_polynomial_ccsds, 1, 1, min_distance);
	int* indices = malloc(warble->block_length * sizeof(int));
	size_t block_length = msg_length + min_distance;
	size_t pad_length = warble->payloadSize - msg_length;
	ssize_t res = correct_reed_solomon_encode(rs, msg, msg_length, words);
	correct_reed_solomon_destroy(rs);
}

void warble_reed_decode_solomon(warble *warble, unsigned char* payload, unsigned char* words) {

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

	for(i=0; i < warble->payloadSize; i++) {
		int col = words[i] % WARBLE_PITCH_ROOT;
		int row = words[i] / WARBLE_PITCH_ROOT;
		double freq1 = warble->frequencies[col];
		double freq2 = warble->frequencies[WARBLE_PITCH_ROOT + row];
		warble_generate_pitch(&(signal_out[s]), warble->word_length, warble->sampleRate, freq1, power_peak);
		warble_generate_pitch(&(signal_out[s]), warble->word_length, warble->sampleRate, freq2, power_peak);
		s += warble->word_length;
	}
}

void warble_GetPayload(warble *warble, unsigned char * payload) {
	// Use Reed-Solomon Forward Error Correction

	// Copy corrected payload
	
}