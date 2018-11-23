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

#include "warble.h"
#include "warble_complex.h"
#include "minunit.h"
#include "correct/reed-solomon.h"

#define SAMPLES 4410

#define MULT 1.0594630943591

#define DEBUG_SCRIPT_PATH fopen("debug.py","w")

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

#define CHECK(a) if(!a) return -1

#define DEFAULT_SNR 20

MU_TEST(test1khz) {
	const double sampleRate = 44100;
	double powerRMS = 500; // 90 dBspl
	float signalFrequency = 1000;
	double powerPeak = powerRMS * sqrt(2);

	double audio[SAMPLES];
	int s;
	for (s = 0; s < SAMPLES; s++) {
		double t = s * (1 / (double)sampleRate);
		audio[s] = sin(2 * M_PI * signalFrequency * t) * (powerPeak);
	}

	double out[1] = {0};

	double freqs[1] = { 1000 };

	warble_generalized_goertzel(audio, SAMPLES, sampleRate, freqs, 1,out);

	double signal_rms = warble_compute_rms(audio, SAMPLES);

	mu_assert_double_eq(signal_rms, out[0], 0.1);
}

MU_TEST(testGenerateSignal) {
	double word_length = 0.0872; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int8_t payload[] = "parrot";

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, sizeof(payload), DEFAULT_SNR, NULL);

	size_t windowSize = warble_generate_window_size(&cfg);
	double* signal = malloc(sizeof(double) * windowSize);
	memset(signal, 0, sizeof(double) * windowSize);
    
    // Copy payload to words (larger size)
	int8_t* words = malloc(sizeof(int8_t) * cfg.block_length + 1);
	memset(words, 0, sizeof(int8_t) * cfg.block_length + 1);
	memcpy(words, payload, sizeof(payload));

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, signal);

	free(signal);

	warble_free(&cfg);
}


MU_TEST(testWriteSignal) {
	FILE *f = fopen("audioTest_44100_16bitsPCM.raw", "wb");
	if (f == NULL)
	{
		printf("Error opening file!\n");
		exit(1);
	}

	double word_length = 0.0872; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	// Send Ipfs address
	// Python code:
	// import base58
	// import struct
	// s = struct.Struct('b').unpack
	// payload = map(lambda v : s(v)[0], base58.b58decode("QmXjkFQjnD8i8ntmwehoAHBfJEApETx8ebScyVzAHqgjpD"))
	int8_t payload[] = {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36,
		-100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
	int blankBefore = (int)(44100 * 0.55);
	int blankAfter = (int)(44100 * 0.6);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (uint8_t)sizeof(payload), DEFAULT_SNR, NULL);

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);

	// Encode message
	int8_t* words = malloc(sizeof(int8_t) * cfg.block_length + 1);
	memset(words, 0, sizeof(int8_t) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, &(signal[blankBefore]));

	// Write signal
	int s;
	double factor = 32767. / (powerPeak * 4);
	for(s = 0; s < signal_size; s++) {
		int16_t sample = (int16_t)(signal[s] * factor);
		fwrite(&sample, sizeof(int16_t), 1 , f);
	}

	free(signal);
	free(words);
	warble_free(&cfg);

	fclose(f);
}

MU_TEST(testFeedSignal1) {
	double word_length = 0.078; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int8_t payload[] = "!0BSduvwxyz";
	int blankBefore = (int)(44100 * 0.13);
	int blankAfter = (int)(44100 * 0.2);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, sizeof(payload), DEFAULT_SNR, NULL);
	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);
    
    
    // Copy payload to words (larger size)
	int8_t* words = malloc(sizeof(int8_t) * cfg.block_length + 1);
	memset(words, 0, sizeof(int8_t) * cfg.block_length + 1);
	memcpy(words, payload, sizeof(payload));

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, &(signal[blankBefore]));
	int64_t offset;
	int res;
	// Check with all possible offset in the wave (detect window index errors)
    // cfg.window_length
	for(offset = 0; offset < 1; offset++) {
		int64_t i;
		for(i=offset; i < signal_size - cfg.window_length; i+=cfg.window_length) {
			res = warble_feed(&cfg, &(signal[i]), cfg.window_length, i);
			if (res == WARBLE_FEED_MESSAGE_COMPLETE) {
				break;
			}
		}
		mu_assert_string_eq(payload, cfg.parsed);
		if(res != WARBLE_FEED_MESSAGE_COMPLETE) {
			break;
		}
	}
	free(signal);
	warble_free(&cfg);

    if (cfg.verbose != NULL) {
        fclose(cfg.verbose);
    }
}

MU_TEST(testWithSolomonShort) {
	double word_length = 0.078; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int8_t payload[] = "!0BSduvwxyz";
	int8_t* decoded_payload = malloc(sizeof(payload));
	memset(decoded_payload, 0, sizeof(payload));

	int blankBefore = (int)(44100 * 0.13);
	int blankAfter = (int)(44100 * 0.2);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, sizeof(payload), DEFAULT_SNR, NULL);

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);

	// Encode message
	int8_t* words = malloc(sizeof(int8_t) * cfg.block_length + 1);
	memset(words, 0, sizeof(int8_t) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, &(signal[blankBefore]));

	int i;
	for (i = 0; i < signal_size - cfg.window_length; i += cfg.window_length) {
		if (warble_feed(&cfg, &(signal[i]),cfg.window_length, i) == WARBLE_FEED_MESSAGE_COMPLETE) {
			// Decode parsed words
			warble_reed_decode_solomon(&cfg, cfg.parsed, decoded_payload);
			break;
		}
	}


	mu_assert_string_eq(payload, decoded_payload);

	free(decoded_payload);
	free(words);
	free(signal);
	warble_free(&cfg);
}
MU_TEST(testInterleave) {
	int8_t expected[] = "dermatoglyphicsdermatoglyphics";
	int8_t payload[] = "dermatoglyphicsdermatoglyphics";
	// Compute index shuffling of messages
	int shuffleIndex[30];
	int i;
	for (i = 0; i < 30; i++) {
		shuffleIndex[i] = i;
	}
	warble_fisher_yates_shuffle_index((int)sizeof(payload), shuffleIndex);
	warble_swap_chars(payload, shuffleIndex, sizeof(payload));
	warble_unswap_chars(payload, shuffleIndex, sizeof(payload));
	mu_assert_string_eq(expected, payload);
}


MU_TEST(testWithSolomonLong) {
	double word_length = 0.05; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int8_t payload[] = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse volutpat.";
	int8_t* decoded_payload = malloc(sizeof(payload));
	memset(decoded_payload, 0, sizeof(payload));

	int blankBefore = (int)(44100 * 0.13);
	int blankAfter = (int)(44100 * 0.2);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, sizeof(payload), DEFAULT_SNR, NULL);

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);

	// Encode message
	int8_t* words = malloc(sizeof(int8_t) * cfg.block_length + 1);
	memset(words, 0, sizeof(int8_t) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, &(signal[blankBefore]));

	int64_t i;
	for (i = 0; i < signal_size - cfg.window_length; i += cfg.window_length) {
		if (warble_feed(&cfg, &(signal[i]), cfg.window_length, i) == WARBLE_FEED_MESSAGE_COMPLETE) {
			// Decode parsed words
			mu_assert_string_eq(words, cfg.parsed);
			warble_reed_decode_solomon(&cfg, cfg.parsed, decoded_payload);
			break;
		}
	}


	mu_assert_string_eq(payload, decoded_payload);

	free(decoded_payload);
	free(words);
	free(signal);
	warble_free(&cfg);
}

MU_TEST(testWithSolomonError) {
	double word_length = 0.05; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	int8_t payload[] = "CONCENTRATIONNAIRE";
	int8_t* decoded_payload = malloc(sizeof(payload));
	memset(decoded_payload, 0, sizeof(payload));

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, sizeof(payload), DEFAULT_SNR, NULL);

	// Encode message
	int8_t* words = malloc(sizeof(int8_t) * cfg.block_length + 1);
	memset(words, 0, sizeof(int8_t) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Erase 4 words
	int i;
	int64_t seed = 1337;
	for(i = 0; i < 4; i++) {
		words[warble_rand(&seed) % cfg.block_length] = (int8_t)(warble_rand(&seed) % 255);
	}

	mu_assert(warble_reed_decode_solomon(&cfg, words, decoded_payload) >= 0, "Can't fix error with reed solomon");

	mu_assert_string_eq(payload, decoded_payload);

	free(decoded_payload);
	free(words);
	warble_free(&cfg);
}

MU_TEST(testWithSolomonErrorInSignal) {
	double word_length = 0.05; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int8_t payload[] = "dermatoglyphics";
    int payloadsize = sizeof(payload);
	int8_t* decoded_payload = malloc(payloadsize);
	memset(decoded_payload, 0, payloadsize);

	int blankBefore = (int)(44100 * 0.13);
	int blankAfter = (int)(44100 * 0.2);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, payloadsize, DEFAULT_SNR, NULL); // DEBUG_SCRIPT_PATH

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);

	// Encode message
	int8_t* words = malloc(sizeof(int8_t) * cfg.block_length + 1);
	memset(words, 0, sizeof(int8_t) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, &(signal[blankBefore]));

	// Replace some samples with noise
	int e;
	int64_t seed = 15401375096237;
	int offset_error = cfg.chirp_length + blankBefore;
	for(e = 0; e < 3; e++) {
        int word_error = (warble_rand(&seed) / 32768.) * payloadsize;
		int start = offset_error + word_error * cfg.word_length;
		int s;
		for(s = start; s < start + cfg.word_length; s++) {
			signal[s] = (warble_rand(&seed) / 32768.) * powerPeak;
		}
	}

	int64_t i;
	for (i = 0; i < signal_size - cfg.window_length; i += cfg.window_length) {
        int code = warble_feed(&cfg, &(signal[i]), cfg.window_length, i);
		if (code == WARBLE_FEED_MESSAGE_COMPLETE) {
			// Decode and fix parsed words
			mu_assert(warble_reed_decode_solomon(&cfg, cfg.parsed, decoded_payload) >= 0, "Can't fix error with reed solomon");
			break;
        } else if (code == WARBLE_FEED_DETECT_PITCH) {
            mu_assert_int_eq(blankBefore, cfg.triggerSampleIndexBegin);
        }
	}

    mu_assert_string_eq(payload, decoded_payload);

	free(decoded_payload);
	free(words);
	free(signal);
	warble_free(&cfg);

    if (cfg.verbose != NULL) {
        fclose(cfg.verbose);
    }
}

MU_TEST(testReedSolomon) {
	uint8_t block[18];
	uint8_t message[10];
	uint8_t message_decoded[10];
	int i;
	int64_t next = 1;
	for(i = 0; i < sizeof(message); i++) {
		message[i] = warble_rand(&next) & 255;
		//printf("message[%d]=%d\n", i, message[i]);
	}
	correct_reed_solomon *rs = correct_reed_solomon_create(
    correct_rs_primitive_polynomial_ccsds, 1, 1, sizeof(block) - sizeof(message));
	correct_reed_solomon_encode(rs, message, sizeof(message), block);
	correct_reed_solomon_decode(rs, block, sizeof(block), message_decoded);
	correct_reed_solomon_destroy(rs);
}

MU_TEST(testDecodingRealAudio1) {

	int8_t expected_payload[] = { 18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36,
		-100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114 };
	FILE *f = fopen("audioTest_44100_16bitsPCM_0.0872s_1760.raw", "rb");
	mu_check(f != NULL);

	// obtain file size:
	fseek(f, 0, SEEK_END);
	size_t file_length = ftell(f);
	rewind(f);

	size_t buffer_length = sizeof(int16_t) * 1024;
	int8_t buffer[sizeof(int16_t) * 1024];
	// Allocate memory to contain the signal
	size_t signal_size = (file_length / sizeof(int16_t));
	double* signal = malloc(sizeof(double) * signal_size);
	size_t i;
	size_t s = 0;
	for(i = 0; i < file_length;) {
		size_t res = fread(buffer, sizeof(int8_t), min(buffer_length, file_length - i), f);
		mu_check(res % sizeof(int16_t) == 0);
		i += res;
		int cursor;
		for(cursor = 0; cursor < res; cursor+=sizeof(int16_t)) {
			int16_t spl;
			memcpy(&spl, &(buffer[cursor]), sizeof(int16_t));
			signal[s++] = spl;
		}
	}
	fclose(f);

	// Decode signal into payload


	double word_length = 0.0872; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	int32_t payload_len = sizeof(expected_payload);
	int8_t* decoded_payload = malloc(sizeof(int8_t) * payload_len + 1);
	memset(decoded_payload, 0, sizeof(int8_t) * payload_len + 1);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, payload_len, DEFAULT_SNR, NULL); // DEBUG_SCRIPT_PATH


	// Encode test message
	int8_t* words = malloc(sizeof(int8_t) * cfg.block_length + 1);
	memset(words, 0, sizeof(int8_t) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, expected_payload, words);

	int j;
	double fexp0, fexp1, f0, f1;
	int res = WARBLE_FEED_ERROR;
    int errorCount = 0;
	for (i = 0; i < signal_size - cfg.window_length; i += cfg.window_length) {
        res = warble_feed(&cfg, &(signal[i]), cfg.window_length, i);
		if (res == WARBLE_FEED_MESSAGE_COMPLETE) {
			// End, check frequencies
			for(j=0; j<cfg.block_length;j++) {
				warble_char_to_frequencies(&cfg, words[j], &fexp0, &fexp1);
				warble_char_to_frequencies(&cfg, cfg.parsed[j], &f0, &f1);
                if (fexp0 != f0) {
                    errorCount++;
                }
                if (fexp1 != f1) {
                    errorCount++;
                }
			}
			// Decode and fix parsed words
			mu_assert(warble_reed_decode_solomon(&cfg, cfg.parsed, decoded_payload) > 0, "Can't fix error with reed solomon");
			break;
		}
	}

	mu_assert_int_eq(WARBLE_FEED_MESSAGE_COMPLETE, res);


    for (j = 0; j<cfg.payloadSize; j++) {
        mu_assert_int_eq(expected_payload[j], decoded_payload[j]);
    }

	free(decoded_payload);
	free(signal);
	free(words);
	warble_free(&cfg);

    if (cfg.verbose != NULL) {
        fclose(cfg.verbose);
    }
}

MU_TEST_SUITE(test_suite) {

	MU_RUN_TEST(test1khz);
	MU_RUN_TEST(testGenerateSignal);
	MU_RUN_TEST(testFeedSignal1);
	//MU_RUN_TEST(testWriteSignal); // debug purpose
	MU_RUN_TEST(testWithSolomonShort);
	MU_RUN_TEST(testWithSolomonLong);
	MU_RUN_TEST(testInterleave);
	MU_RUN_TEST(testWithSolomonError);
	MU_RUN_TEST(testWithSolomonErrorInSignal);
	MU_RUN_TEST(testDecodingRealAudio1);
	MU_RUN_TEST(testReedSolomon);
}

int main(int argc, char** argv) {
	MU_RUN_SUITE(test_suite);
	MU_REPORT();
	#ifdef _WIN32
	#ifdef _DEBUG
		_CrtDumpMemoryLeaks(); //Display memory leaks
	#endif
	#endif
	return minunit_status == 1 || minunit_fail > 0 ? -1 : 0;
}
