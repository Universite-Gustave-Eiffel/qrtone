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
#include "warble_complex.h"

/**
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
void generalized_goertzel(const double* signal, int32_t s_length,double sampleRate,const double* freqs, int32_t f_length, double* outFreqsPower) {
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
		outFreqsPower[id_freq] = sqrt((y.r * y.r + y.i * y.i )) / s_length * sqrt(2);
	}
}



warble* warble_create(double firstFrequency,
 double frequencyMultiplication,
  int16_t frequencyIncrement,int16_t wordSize,
  int16_t payloadSize,int16_t wordTriggerCount, int16_t* wordTriggers)  {
    warble* this = (warble*) malloc(sizeof(warble));
    this->firstFrequency = firstFrequency;
	this->frequencyIncrement = frequencyIncrement;
	this->wordSize = wordSize;
	this->payloadSize = payloadSize;
	this->wordTriggerCount = wordTriggerCount;
	this->wordTriggers = wordTriggers;
	this->triggerSampleIndex = -1;
	this->paritySize =  wordSize - 1 - payloadSize / 2;
	this->parsed = malloc(sizeof(int16_t) * (wordTriggerCount + payloadSize + this->paritySize));
    return this;
}


/**
 * Free buffer in object
 * @param warble Object to free
 */
void warble_free(warble *warble) {
    free(warble->wordTriggers);
    free(warble->parsed);
    free(warble);
}

/**
 * Analyse the provided spectrum
 * @return 1 if the message can be collected using warble_GetPayload
 */
int16_t warble_feed(warble *warble, double* rms, int rmsSize, double sampleRate, int64_t sampleIndex) {

	return 0;
}

/**
 * @return payload of size warble->payloadSize
 */
int16_t* warble_GetPayload(warble *warble) {
	// Use Reed-Solomon Forward Error Correction

}