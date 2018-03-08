/* Use ./TestAudio/audioExtract.py to create audioData.h - this converts a wave file to an array of doubles in C.
 * Copy audioData.h into same folder as this test then build and run - the program should give F0 of the signal in
 * audioData.h */

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <assert.h>

#include "warble_complex.h"

#define SAMPLES 4410

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

#define CHECK(a) if(!a) return -1

int testComplex() {
	return 0;
}

int test1khz() {
	const double sampleRate = 44100;
	double powerRMS = 500; // 90 dBspl
	float signalFrequency = 1000;
	double powerPeak = powerRMS * sqrt(2);

	double audio[SAMPLES];

	for (int s = 0; s < SAMPLES; s++) {
		double t = s * (1 / (double)sampleRate);
		audio[s] = sin(2 * M_PI * signalFrequency * t) * (powerPeak);
	}

	double out[1] = {0};

	double freqs[1] = { 1000 };

	warble_generalized_goertzel(audio, SAMPLES, sampleRate, freqs, 1,out);

	printf("found %.f Hz (%.2f)\n", signalFrequency, out[0]);

	CHECK(abs(out[0] - powerRMS) < 0.1);

	return 0;
}

int main(int argc, char** argv) {
	if (testComplex() != 0) {
		return -1;
	}
	if (test1khz() != 0) {
		return -1;
	}
	return 0;
}
