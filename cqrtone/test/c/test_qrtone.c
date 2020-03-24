/*
 * BSD 3-Clause License
 *
 * Copyright (c) Unité Mixte de Recherche en Acoustique Environnementale (univ-gustave-eiffel)
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


#ifdef _DEBUG
#define _CRTDBG_MAP_ALLOC
#ifdef _WIN32
#include <crtdbg.h>
#endif
#endif

#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <assert.h>
#include <stdio.h>

#include "qrtone.h"
#include "minunit.h"

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

#define CHECK(a) if(!a) return -1

#define SAMPLES 4410


MU_TEST(testCRC8) {
	int8_t data[] = { 0x0A, 0x0F, 0x08, 0x01, 0x05, 0x0B, 0x03 };

	qrtone_crc8_t crc;

	qrtone_crc8_init(&crc);

	qrtone_crc8_add_array(&crc, data, sizeof(data));

	mu_assert_int_eq(0xEA, qrtone_crc8_get(&crc));
}

MU_TEST(testCRC16) {
	int8_t data[] = { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J' };

	qrtone_crc16_t crc;

	qrtone_crc16_init(&crc);

	qrtone_crc16_add_array(&crc, data, sizeof(data));

	mu_assert_int_eq(0x0C9E, crc.crc16);
}

MU_TEST(test1khz) {
	const double sample_rate = 44100;
	double powerRMS = pow(10.0, -26.0 / 20.0); // -26 dBFS
	float signal_frequency = 1000;
	double powerPeak = powerRMS * sqrt(2);

	float audio[SAMPLES];
	int s;
	for (s = 0; s < SAMPLES; s++) {
		double t = s * (1 / (double)sample_rate);
		audio[s] = (float)(sin(2 * M_PI * signal_frequency * t) * (powerPeak));
	}

	qrtone_goertzel_t goertzel;

	qrtone_goertzel_init(&goertzel, sample_rate, signal_frequency, SAMPLES);

	qrtone_goertzel_process_samples(&goertzel, audio, SAMPLES);

	double signal_rms = qrtone_goertzel_compute_rms(&goertzel);

	mu_assert_double_eq(20 * log10(powerRMS), 20*log10(signal_rms), 0.01);
}

MU_TEST(test1khzIterative) {
	const double sample_rate = 44100;
	double powerRMS = pow(10.0, -26.0 / 20.0); // -26 dBFS
	float signal_frequency = 1000;
	double powerPeak = powerRMS * sqrt(2);

	float audio[SAMPLES];
	int s;
	for (s = 0; s < SAMPLES; s++) {
		double t = s * (1 / (double)sample_rate);
		audio[s] = (float)(sin(2 * M_PI * signal_frequency * t) * (powerPeak));
	}

	qrtone_goertzel_t goertzel;

	qrtone_goertzel_init(&goertzel, sample_rate, signal_frequency, SAMPLES);

	int32_t cursor = 0;

	while (cursor < SAMPLES) {
		int32_t window_size = min((rand() % 115) + 20, SAMPLES - cursor);
		qrtone_goertzel_process_samples(&goertzel, audio + cursor, window_size);
		cursor += window_size;
	}

	double signal_rms = qrtone_goertzel_compute_rms(&goertzel);

	mu_assert_double_eq(20 * log10(powerRMS), 20 * log10(signal_rms), 0.01);
}

MU_TEST_SUITE(test_suite) {
	MU_RUN_TEST(testCRC8);
	MU_RUN_TEST(testCRC16);
	MU_RUN_TEST(test1khz);
	MU_RUN_TEST(test1khzIterative);
}

int main(int argc, char** argv) {
	//_crtBreakAlloc = 13205;
	MU_RUN_SUITE(test_suite);
	MU_REPORT();
#ifdef _WIN32
#ifdef _DEBUG
	_CrtDumpMemoryLeaks(); //Display memory leaks
#endif
#endif
	return minunit_status == 1 || minunit_fail > 0 ? -1 : 0;
}
