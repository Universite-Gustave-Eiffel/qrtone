/* Use ./TestAudio/audioExtract.py to create audioData.h - this converts a wave file to an array of doubles in C.
 * Copy audioData.h into same folder as this test then build and run - the program should give F0 of the signal in
 * audioData.h */
#define _USE_MATH_DEFINES

#include "kiss_fft.h"
#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <assert.h>

#define SAMPLES 22050

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))


int test1khz() {
	const int sampleRate = 44100;
	const int window = 4410;
	double powerRMS = 500; // 90 dBspl
	float signalFrequency = 1000;
	double powerPeak = powerRMS * sqrt(2);

	kiss_fft_cpx audio[SAMPLES];

	for (int s = 0; s < SAMPLES; s++) {
		double t = s * (1 / (double)sampleRate);
		audio[s].r = sin(2 * M_PI * signalFrequency * t) * (powerPeak);
		audio[s].i = 0;
	}

	kiss_fft_cfg* cfg = kiss_fft_alloc(window, 0, NULL, NULL);

	kiss_fft_cpx fft_out[SAMPLES];

	kiss_fft(cfg, audio, fft_out);

	int freqBand = (int)round(signalFrequency / (sampleRate / (double)window));

	double rms = (2. / window * sqrt((fft_out[freqBand].r * fft_out[freqBand].r + fft_out[freqBand].i * fft_out[freqBand].i))) / sqrt(2);

	kiss_fft_free(cfg);

	return abs(rms - powerRMS) < 0.1 ? 0 : -1;
}

int main(int argc, char** argv) {
	if (test1khz() != 0) {
		return -1;
	}
	return 0;
}
