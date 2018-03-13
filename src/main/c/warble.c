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

#define WARBLE_2PI 6.283185307179586
#define PITCH_SIGNAL_TO_NOISE_TRIGGER 3 // Accept pitch if pitch power is less than 3dB lower than signal level

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
	int16_t payloadSize, int16_t* frequenciesIndexTriggers, int16_t frequenciesIndexTriggersCount)  {
	this->sampleRate = sampleRate;
	this->payloadSize = payloadSize;
	this->word_length = (int32_t)(sampleRate * word_time);
	this->window_length = (int32_t)(this->word_length / 2);
	this->frequenciesIndexTriggersCount = frequenciesIndexTriggersCount;
	this->frequenciesIndexTriggers = malloc(sizeof(int16_t) * frequenciesIndexTriggersCount);
	memcpy(this->frequenciesIndexTriggers, frequenciesIndexTriggers, sizeof(int16_t) * frequenciesIndexTriggersCount);
	this->triggerSampleIndex = -1;
	//this->paritySize =  wordSize - 1 - payloadSize / 2;
	this->parsed = malloc(sizeof(unsigned char) * (payloadSize) + 1);
	this->parsed[payloadSize] = '\0';

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
}

void warble_free(warble *warble) {
	free(warble->frequenciesIndexTriggers);
    free(warble->parsed);
	free(warble->frequencies);
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

unsigned char spectrumToChar(warble *warble, double* rms) {
	int sortedIndex[3] = { -1, -1, -1 };
	int i;
	int j;
	// Sort rms by descending order
	for (int i = 0; i<WARBLE_PITCH_COUNT; i++) {
		for (int j = 0; j<3; j++) {
			if (sortedIndex[j] == -1 || rms[sortedIndex[j]] < rms[i]) {
				// Move values
				int k;
				for (k = j; k < 2; k++) {
					sortedIndex[k + 1] = sortedIndex[k];
				}
				sortedIndex[j] = i;
				break;
			}
		}
	}
	//if(20 * log10(rms[sortedIndex[1]]) - 20 * log10(rms[sortedIndex[2]]) > PITCH_SIGNAL_TO_NOISE_TRIGGER) {
	int f0index = min(sortedIndex[0], sortedIndex[1]);
	int f1index = max(sortedIndex[0], sortedIndex[1]) - WARBLE_PITCH_ROOT;
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