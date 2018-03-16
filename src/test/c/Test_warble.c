/* Use ./TestAudio/audioExtract.py to create audioData.h - this converts a wave file to an array of doubles in C.
 * Copy audioData.h into same folder as this test then build and run - the program should give F0 of the signal in
 * audioData.h */
#ifdef _DEBUG
#define _CRTDBG_MAP_ALLOC
#include <stdlib.h>
#ifdef _WIN32
#include <crtdbg.h>
#endif
#endif

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <assert.h>
#include <stdio.h>

#include "warble.h"
#include "warble_complex.h"
#include "minunit.h"

#define SAMPLES 4410

#define MULT 1.0594630943591

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

	mu_assert_double_eq(signal_rms, out[0], 0.1);
}

MU_TEST(testGenerateSignal) {
	double word_length = 0.0872; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int16_t triggers[2] = {9, 25};
	char payload[] = "parrot";

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (uint8_t)strlen(payload), triggers, 2);

	size_t windowSize = warble_generate_window_size(&cfg);
	double* signal = malloc(sizeof(double) * windowSize);
	memset(signal, 0, sizeof(double) * windowSize);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, payload, signal);

	// Check frequencies
	double rms[32];

	// Analyze first trigger
	warble_generalized_goertzel(signal, cfg.word_length, cfg.sampleRate, cfg.frequencies, WARBLE_PITCH_COUNT, rms);

	mu_assert_int_eq(triggers[0], warble_get_highest_index(rms, 0, WARBLE_PITCH_ROOT));

	// Analyze second trigger
	warble_generalized_goertzel(&(signal[cfg.word_length]), cfg.word_length, cfg.sampleRate, cfg.frequencies, WARBLE_PITCH_COUNT, rms);

	mu_assert_int_eq(triggers[1], warble_get_highest_index(rms, WARBLE_PITCH_ROOT, WARBLE_PITCH_COUNT));

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
	int16_t triggers[2] = { 9, 25 };
	// Send Ipfs adress
	// import base58
	// payload = map(ord, base58.b58decode("QmXjkFQjnD8i8ntmwehoAHBfJEApETx8ebScyVzAHqgjpD"))
	char payload[] = {18, 32, 139, 163, 206, 2, 52, 26, 139, 93, 119, 147, 39, 46, 108, 4, 31, 36, 156,
		95, 247, 186, 174, 163, 181, 224, 193, 42, 212, 156, 50, 83, 138, 114};
	int blankBefore = (int)(44100 * 0.55);
	int blankAfter = (int)(44100 * 0.6);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (uint8_t)sizeof(payload), triggers, 2);

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);
	
	// Encode message
	unsigned char* words = malloc(sizeof(unsigned char) * cfg.block_length + 1);
	memset(words, 0, sizeof(unsigned char) * cfg.block_length + 1);
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
	int16_t triggers[2] = { 9, 25 };
	char payload[] = "!0BSduvwxyz";
	int blankBefore = (int)(44100 * 0.13);
	int blankAfter = (int)(44100 * 0.2);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (uint8_t)strlen(payload), triggers, 2);

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, payload, &(signal[blankBefore]));
	int offset;
	// Check with all possible offset in the wave (detect window index errors)
	for(offset = 0; offset < cfg.window_length; offset++) {
		int i;
		for(i=offset; i < signal_size - cfg.window_length; i+=cfg.window_length) {
			if (warble_feed(&cfg, &(signal[i]), i) == WARBLE_FEED_MESSAGE_COMPLETE) {
				break;
			}
		}
		mu_assert_string_eq(payload, cfg.parsed);
		if(strlen(cfg.parsed) == 0) {
			break;
		}
	}
	free(signal);
	warble_free(&cfg);
}

MU_TEST(testWithSolomonShort) {
	double word_length = 0.078; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int16_t triggers[2] = { 9, 25 };
	char payload[] = "!0BSduvwxyz";
	char* decoded_payload = malloc(sizeof(unsigned char) * strlen(payload) + 1);
	memset(decoded_payload, 0, sizeof(unsigned char) * strlen(payload) + 1);

	int blankBefore = (int)(44100 * 0.13);
	int blankAfter = (int)(44100 * 0.2);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (int32_t)strlen(payload), triggers, 2);

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);

	// Encode message
	unsigned char* words = malloc(sizeof(unsigned char) * cfg.block_length + 1);
	memset(words, 0, sizeof(unsigned char) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, &(signal[blankBefore]));

	int i;
	for (i = 0; i < signal_size - cfg.window_length; i += cfg.window_length) {
		if (warble_feed(&cfg, &(signal[i]), i) == WARBLE_FEED_MESSAGE_COMPLETE) {
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
	char expected[] = "dermatoglyphicsdermatoglyphics";
	char payload[] = "dermatoglyphicsdermatoglyphics";
	// Compute index shuffling of messages
	int shuffleIndex[30];
	int i;
	for (i = 0; i < 30; i++) {
		shuffleIndex[i] = i;
	}
	warble_fisher_yates_shuffle_index((int)strlen(payload), shuffleIndex);
	warble_swap_chars(payload, shuffleIndex, strlen(payload));
	warble_unswap_chars(payload, shuffleIndex, strlen(payload));
	mu_assert_string_eq(expected, payload);
}


MU_TEST(testWithSolomonLong) {
	double word_length = 0.05; // pitch length in seconds
	warble cfg;
	int sample_rate = 44100;
	double powerRMS = 500;
	double powerPeak = powerRMS * sqrt(2);
	int16_t triggers[2] = { 9, 25 };
	char payload[] = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse volutpat.";
	char* decoded_payload = malloc(sizeof(unsigned char) * strlen(payload) + 1);
	memset(decoded_payload, 0, sizeof(unsigned char) * strlen(payload) + 1);

	int blankBefore = (int)(44100 * 0.13);
	int blankAfter = (int)(44100 * 0.2);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (int32_t)strlen(payload), triggers, 2);

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);

	// Encode message
	unsigned char* words = malloc(sizeof(unsigned char) * cfg.block_length + 1);
	memset(words, 0, sizeof(unsigned char) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, &(signal[blankBefore]));

	int i;
	for (i = 0; i < signal_size - cfg.window_length; i += cfg.window_length) {
		if (warble_feed(&cfg, &(signal[i]), i) == WARBLE_FEED_MESSAGE_COMPLETE) {
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
	int16_t triggers[2] = { 9, 25 };
	char payload[] = "CONCENTRATIONNAIRE";
	char* decoded_payload = malloc(sizeof(unsigned char) * strlen(payload) + 1);
	memset(decoded_payload, 0, sizeof(unsigned char) * strlen(payload) + 1);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (int32_t)strlen(payload), triggers, 2);
	
	// Encode message
	unsigned char* words = malloc(sizeof(unsigned char) * cfg.block_length + 1);
	memset(words, 0, sizeof(unsigned char) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Erase 4 words
	int i;
	int64_t seed = 1337;
	for(i = 0; i < 4; i++) {
		words[warble_rand(&seed) % cfg.block_length] = (unsigned char)(warble_rand(&seed) % 255);
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
	int16_t triggers[2] = { 9, 25 };
	char payload[] = "dermatoglyphics";
	char* decoded_payload = malloc(sizeof(unsigned char) * strlen(payload) + 1);
	memset(decoded_payload, 0, sizeof(unsigned char) * strlen(payload) + 1);

	int blankBefore = (int)(44100 * 0.13);
	int blankAfter = (int)(44100 * 0.2);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (int32_t)strlen(payload), triggers, 2);

	size_t signal_size = warble_generate_window_size(&cfg) + blankBefore + blankAfter;
	double* signal = malloc(sizeof(double) * signal_size);
	memset(signal, 0, sizeof(double) * signal_size);

	// Encode message
	unsigned char* words = malloc(sizeof(unsigned char) * cfg.block_length + 1);
	memset(words, 0, sizeof(unsigned char) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, payload, words);

	// Replaces zeroes with pitchs
	warble_generate_signal(&cfg, powerPeak, words, &(signal[blankBefore]));

	// Replace some samples with noise
	int e;
	int64_t seed = 1337;
	int offset_error = cfg.frequenciesIndexTriggersCount * cfg.word_length + blankBefore;
	for(e = 0; e < 4; e++) {
		int start = offset_error + (int)((warble_rand(&seed) / 32768.) * (signal_size - offset_error - cfg.word_length));
		int s;
		//printf("Erase signal from %d to %d (%d max)\n", start, start + cfg.word_length, signal_size);
		for(s = start; s < start + cfg.word_length; s++) {
			signal[s] = (warble_rand(&seed) / 32768.) * powerPeak;
		}
	}

	int i;
	for (i = 0; i < signal_size - cfg.window_length; i += cfg.window_length) {
		if (warble_feed(&cfg, &(signal[i]), i) == WARBLE_FEED_MESSAGE_COMPLETE) {
			// Decode and fix parsed words
			mu_assert(warble_reed_decode_solomon(&cfg, cfg.parsed, decoded_payload) >= 0, "Can't fix error with reed solomon");
			break;
		}
	}


	mu_assert_string_eq(payload, decoded_payload);

	free(decoded_payload);
	free(words);
	free(signal);
	warble_free(&cfg);
}

MU_TEST(testDecodingRealAudio1) {

	char expected_payload[] = { 18, 32, 139, 163, 206, 2, 52, 26, 139, 93, 119, 147, 39, 46, 108, 4, 31, 36, 156,
		95, 247, 186, 174, 163, 181, 224, 193, 42, 212, 156, 50, 83, 138, 114 };
	FILE *f = fopen("audioTest_44100_16bitsPCM_0.0872s_1760.raw", "rb");
	mu_check(f != NULL);

	// obtain file size:
	fseek(f, 0, SEEK_END);
	size_t file_length = ftell(f);
	rewind(f);

	size_t buffer_length = sizeof(int16_t) * 1024;
	char buffer[sizeof(int16_t) * 1024];
	// Allocate memory to contain the signal
	size_t signal_size = (file_length / sizeof(int16_t));
	double* signal = malloc(sizeof(double) * signal_size);
	size_t i;
	size_t s = 0;
	for(i = 0; i < file_length;) {
		size_t res = fread(buffer, sizeof(char), min(buffer_length, file_length - i), f);
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
	int payload_len = sizeof(expected_payload);
	int16_t triggers[2] = { 9, 25 };
	int8_t* decoded_payload = malloc(sizeof(unsigned char) * payload_len + 1);
	memset(decoded_payload, 0, sizeof(unsigned char) * payload_len + 1);

	warble_init(&cfg, sample_rate, 1760, MULT, 0, word_length, (int32_t)payload_len, triggers, 2);


	// Encode test message
	int8_t* words = malloc(sizeof(unsigned char) * cfg.block_length + 1);
	memset(words, 0, sizeof(unsigned char) * cfg.block_length + 1);
	warble_reed_encode_solomon(&cfg, expected_payload, words);

	int j;
	double fexp0, fexp1, f0, f1;
	for (i = 0; i < signal_size - cfg.window_length; i += cfg.window_length) {
		int res = warble_feed(&cfg, &(signal[i]), i);
		if (res == WARBLE_FEED_MESSAGE_COMPLETE) {
			// End, check frequencies
			for(j=0; j<cfg.block_length;j++) {
				warble_char_to_frequencies(&cfg, words[j], &fexp0, &fexp1);
				warble_char_to_frequencies(&cfg, cfg.parsed[j], &f0, &f1);
				mu_assert_double_eq(fexp0, f0, 0.1);
				mu_assert_double_eq(fexp1, f1, 0.1);
			}
			// Decode and fix parsed words
			mu_assert(warble_reed_decode_solomon(&cfg, cfg.parsed, decoded_payload) >= 0, "Can't fix error with reed solomon");
			break;
		}
	}

	free(decoded_payload);
	free(signal);
	free(words);
	warble_free(&cfg);
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
