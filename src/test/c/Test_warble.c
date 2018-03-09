/* Use ./TestAudio/audioExtract.py to create audioData.h - this converts a wave file to an array of doubles in C.
 * Copy audioData.h into same folder as this test then build and run - the program should give F0 of the signal in
 * audioData.h */

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <assert.h>

#include "warble.h"
#include "warble_complex.h"
#include "minunit.h"

#define SAMPLES 4410

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

#define CHECK(a) if(!a) return -1

MU_TEST(test1khz) {
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

	double signal_rms = warble_compute_rms(audio, SAMPLES);

	mu_assert_double_eq(powerRMS, out[0], 0.1);

	mu_assert_double_eq(powerRMS, signal_rms, 0.1);
}


MU_TEST_SUITE(test_suite) {
	MU_RUN_TEST(test1khz);
}

int main(int argc, char** argv) {
	MU_RUN_SUITE(test_suite);
	MU_REPORT();
	return minunit_fail > 0 ? -1 : 0;
}
