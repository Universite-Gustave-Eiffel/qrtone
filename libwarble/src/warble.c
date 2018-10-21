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

// In order to fix at least 20% of error, the maximum message length attached to a reed solomon(255,32) code is 10 Bytes for a 8 distance (can correct up to 2 Bytes)
#define WARBLE_RS_P 10
#define WARBLE_RS_DISTANCE 8

#ifndef MIN
#define MIN(a,b) (((a)<(b))?(a):(b))
#endif

#ifndef MAX
#define MAX(a,b) (((a)>(b))?(a):(b))
#endif

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
		double pik_term = WARBLE_2PI *(freqs[id_freq] * samplingRateFactor) / (s_length);
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

int32_t warble_reed_solomon_distance(int32_t length) {
	return (int32_t)max(4, min(WARBLE_RS_DISTANCE, pow(2, round(log(length / 3.) / log(2)))));
}

void warble_init(warble* this, double sampleRate, double firstFrequency,
	double frequencyMultiplication,
	int32_t frequencyIncrement, double word_time,
	int32_t payloadSize, double snr_trigger, FILE* verbose)  {
    this->verbose = verbose;
	this->sampleRate = sampleRate;
    this->parsed_cursor = -1;
    this->triggerSampleIndexBegin = -1;
	this->payloadSize = payloadSize;
	this->snr_trigger = snr_trigger;
    this->cross_correlation_accuracy = 1;
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
	// Increase probability to have a window begining at 25% of the pitch (capture the lobe peak)
	this->window_length = (int32_t)((sampleRate * (word_time / 2.)));
    this->chirp_length = this->word_length;
    this->signal_cache_size = 2 * this->chirp_length;
	this->signal_cache = malloc(sizeof(double) * this->signal_cache_size);
    memset(this->signal_cache, 0, sizeof(double) * this->signal_cache_size);
    this->cross_correlation_cache_size = this->chirp_length * 2;
    this->cross_correlation_cache = malloc(sizeof(double) * this->cross_correlation_cache_size);
    memset(this->cross_correlation_cache, 0, sizeof(double) * this->cross_correlation_cache_size);
    this->trigger_cache = malloc(sizeof(double) * this->chirp_length);
	//this->paritySize =  wordSize - 1 - payloadSize / 2;
	this->parsed = malloc(sizeof(int8_t) * (this->block_length) + 1);
	memset(this->parsed, 0, sizeof(int8_t) * (this->block_length) + 1);
	// Precompute pitch frequencies
    int32_t i;
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
	// Generate log chirp into cache
    // low frequency to high frequency
	double f1_div_f0 = this->frequencies[WARBLE_PITCH_COUNT - 1] / this->frequencies[0];
    double chirp_time = (this->chirp_length / 2) / sampleRate;
	for (i = 0; i < this->chirp_length / 2; i++) {
		const double window = 0.5 * (1 - cos((WARBLE_2PI * i) / (this->chirp_length - 1)));
        this->trigger_cache[i] = window * sin(WARBLE_2PI * chirp_time / log(f1_div_f0) * this->frequencies[0] * (pow(f1_div_f0, ((double)i / sampleRate) / chirp_time) - 1.0));
	}
    // high frequency to low frequency
    f1_div_f0 = this->frequencies[0] / this->frequencies[WARBLE_PITCH_COUNT - 1];
    int32_t inext = this->chirp_length / 2;
    for (i = inext; i < this->chirp_length; i++) {
        const double window = 0.5 * (1 - cos((WARBLE_2PI * i) / (this->chirp_length - 1)));
        this->trigger_cache[i] = -1 * window * sin(WARBLE_2PI * chirp_time / log(f1_div_f0) * this->frequencies[WARBLE_PITCH_COUNT - 1] * (pow(f1_div_f0, ((double)(i - inext) / sampleRate) / chirp_time) - 1.0));
    }
    if (this->verbose != NULL) {
        fprintf(this->verbose, "sample_rate=%g\n", sampleRate);
        fprintf(this->verbose, "word_length=%d\n", this->word_length);
        fprintf(this->verbose, "chirp_length=%d\n", this->chirp_length);
        fprintf(this->verbose, "ccorrelation=[]\n");
        fprintf(this->verbose, "index=[]\n");
    }
}

void warble_free(warble *warble) {
	free(warble->trigger_cache);
	free(warble->parsed);
	free(warble->signal_cache);
	free(warble->shuffleIndex);
    free(warble->cross_correlation_cache);
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
    warble->parsed_cursor = -1;
    memset(warble->cross_correlation_cache, 0, sizeof(double) * warble->cross_correlation_cache_size);
}

uint8_t spectrumToChar(double* rms) {
	int f0index = warble_get_highest_index(rms, 0, WARBLE_PITCH_ROOT);
	int f1index = warble_get_highest_index(rms, WARBLE_PITCH_ROOT, WARBLE_PITCH_COUNT) - WARBLE_PITCH_ROOT;
	return  (uint8_t)((f1index * WARBLE_PITCH_ROOT + f0index));
}

void swapdouble(double* a, double* b) {
    double t = *a;
    *a = *b;
    *b = t;
}

int32_t partition(double* arr, int32_t low, int32_t high)
{
    double pivot = arr[high];
    int32_t i = (low - 1);

    for (int j = low; j <= high - 1; j++)
    {
        if (arr[j] <= pivot)
        {
            i++;
            swapdouble(&(arr[i]), &(arr[j]));
        }
    }
    swapdouble(&(arr[i + 1]), &(arr[high]));
    return (i + 1);
}


// Iterative quick sort
void quick_sort(double* arr, int32_t l, int32_t h)
{
    int32_t* stack = malloc(sizeof(int32_t) * (h - l + 1));
     
    int32_t top = -1;
    stack[++top] = l;
    stack[++top] = h;
    while (top >= 0)
    {
        h = stack[top--];
        l = stack[top--];
        int32_t p = partition(arr, l, h);
        if (p - 1 > l)
        {
            stack[++top] = l;
            stack[++top] = p - 1;
        }
        if (p + 1 < h)
        {
            stack[++top] = p + 1;
            stack[++top] = h;
        }
    }
    free(stack);
}

double median_value(double* cross_correlation, int32_t arsize) {
    double* sorted = malloc(sizeof(double) * arsize);
    int32_t i;
    for (i = 0; i < arsize; i++) {
        sorted[i] = fabs(cross_correlation[i]);
    }
    quick_sort(sorted, 0, arsize);
    double retval;
    if (arsize % 2 == 0) {
        retval = ((sorted[arsize / 2] + sorted[arsize / 2 - 1]) / 2.0);
    } else {
        retval = sorted[arsize / 2];
    }
    free(sorted);
    return retval;
}

double avg_value(double* cross_correlation, int32_t arsize) {
    int32_t i;
    double sum = 0;
    for (i = 0; i < arsize; i++) {
        sum += fabs(cross_correlation[i]);
    }
    return sum / arsize;
}


enum WARBLE_FEED_RESULT warble_feed(warble *warble, double* signal, int32_t signal_length, int64_t sample_index) {
    if (signal_length > warble->window_length) {
        return WARBLE_FEED_ERROR;
    }    
    // Move signal cache samples
    //memcpy(warble->signal_cache, &warble->signal_cache[signal_length], sizeof(double) * (warble->signal_cache_size - signal_length));
    memmove(warble->signal_cache, &(warble->signal_cache[signal_length]), sizeof(double) * (warble->signal_cache_size - signal_length));
    // Copy window to signal cache
    memcpy(&(warble->signal_cache[warble->signal_cache_size - signal_length]), signal, sizeof(double) * signal_length);
    int64_t signal_cache_end_index = sample_index + signal_length;
    // If we are still on the first trigger time
    if (warble->triggerSampleIndexBegin < 0 && sample_index >= warble->chirp_length) {
        // Move cross-correlation samples
        memmove(warble->cross_correlation_cache, &(warble->cross_correlation_cache[signal_length]), sizeof(double) * (warble->cross_correlation_cache_size - signal_length));
        int32_t i;
        int32_t j;
        // Compute max value of signal cache for normalize
        double max = -1e12;
        for (i = 0; i < warble->signal_cache_size; i++) {
            max = MAX(max, fabs(warble->signal_cache[i]));
        }
        if (max == 0) {
            max = 1;
        }
        // Compute cross-correlation for the new samples
        for (i = 0; i < signal_length; i++) {
            double sumproduct = 0;
            // correlation from cached signal
            for (j = 0; j < warble->chirp_length; j += warble->cross_correlation_accuracy) {
                sumproduct += (warble->signal_cache[warble->signal_cache_size - signal_length - warble->chirp_length + i + j] / max) * warble->trigger_cache[j];
            }
            warble->cross_correlation_cache[warble->cross_correlation_cache_size - signal_length + i] = sumproduct / warble->word_length;
        }
        if (warble->verbose != NULL) {
            fprintf(warble->verbose, "ccorrelation+=[%g", warble->cross_correlation_cache[i]);
            for (i = warble->cross_correlation_cache_size - signal_length + 1; i < warble->cross_correlation_cache_size; i++) {
                fprintf(warble->verbose, ",%g", warble->cross_correlation_cache[i]);
            }
            fprintf(warble->verbose, "]\n");
        }
        // Looking for triggering pitch
        // Compare median value with max value
        int32_t maxCorrelationIndex = warble_get_highest_index(warble->cross_correlation_cache, 0, warble->cross_correlation_cache_size);
        double maxCorrelation = warble->cross_correlation_cache[maxCorrelationIndex];
        // Compute median value of absolute correlation        
        double median_ccorrelation = avg_value(warble->cross_correlation_cache, warble->cross_correlation_cache_size - warble->chirp_length);
        if (median_ccorrelation > 0 && 20 * log10(maxCorrelation / median_ccorrelation) > warble->snr_trigger) {
            int64_t chirpLocation = signal_cache_end_index - warble->cross_correlation_cache_size - warble->chirp_length + maxCorrelationIndex;
            // The chirp must be totally included into the signal cache to be accepted
            if (chirpLocation >= signal_cache_end_index - warble->signal_cache_size && chirpLocation + warble->chirp_length <= signal_cache_end_index) {
                // Found trigger pitch
                warble->triggerSampleIndexBegin = chirpLocation;
                warble->parsed_cursor = -1;
                // Compute normalizing constant
                // inequality between expected pitch frequencies leq will be reduced thanks to normalizing constant
                double rms[WARBLE_PITCH_COUNT];
                double rmsChirp[WARBLE_PITCH_COUNT];
                int32_t cache_begin_index = (int32_t)(warble->triggerSampleIndexBegin - (signal_cache_end_index - warble->signal_cache_size));
                int32_t cache_length = MIN(warble->chirp_length, warble->signal_cache_size - cache_begin_index);
                warble_generalized_goertzel(warble->trigger_cache, warble->chirp_length, warble->sampleRate, warble->frequencies, WARBLE_PITCH_COUNT, rmsChirp);
                warble_generalized_goertzel(&(warble->signal_cache[cache_begin_index]), cache_length, warble->sampleRate, warble->frequencies, WARBLE_PITCH_COUNT, rms);
                double maxrms = rms[warble_get_highest_index(rms, 0, WARBLE_PITCH_COUNT)];
                double maxChirp = rmsChirp[warble_get_highest_index(rmsChirp, 0, WARBLE_PITCH_COUNT)];
                for (i = 0; i < WARBLE_PITCH_COUNT; i++) {
                    const double originalChirp = maxChirp / rmsChirp[i];
                    warble->spectrum_normalizing_constant[i] = 1 / ((rms[i] * originalChirp) / maxrms);
                }
            }
        }
    }
    if(warble->triggerSampleIndexBegin > 0) {
		// Target pitch contain the pitch peak ( 0.25 to start from hanning filter lobe)
        // Compute absolute position
        int64_t targetPitch = warble->triggerSampleIndexBegin + warble->chirp_length + (warble->parsed_cursor + 1) * warble->word_length;

		// If the peak is fully contained in cached signal
        // Compute relative position of target pitch with begining of cache array
        int32_t cache_begin_index = (int32_t)(targetPitch - (signal_cache_end_index - warble->signal_cache_size));
        int32_t cache_length = warble->word_length;
        int32_t i;
		if(cache_begin_index >= 0 && cache_begin_index + cache_length <= warble->signal_cache_size) {
            warble->parsed_cursor++;
			double rms[WARBLE_PITCH_COUNT];
			warble_generalized_goertzel(&(warble->signal_cache[cache_begin_index]), cache_length, warble->sampleRate, warble->frequencies, WARBLE_PITCH_COUNT, rms);
            for (i = 0; i < WARBLE_PITCH_COUNT; i++) {
                rms[i] = rms[i] * warble->spectrum_normalizing_constant[i];
            }
			warble->parsed[warble->parsed_cursor] = spectrumToChar(rms);
			if(warble->parsed_cursor == warble->block_length - 1) {
				warble_clean_parse(warble);
				return WARBLE_FEED_MESSAGE_COMPLETE;
			}
			return WARBLE_FEED_DETECT_PITCH;			
		}
	}
	return WARBLE_FEED_IDLE;
}

int32_t warble_feed_window_size(warble *warble) {
	return warble->window_length;
}

int32_t warble_generate_window_size(warble *warble) {
    // Window size is chirp + message
	return warble->chirp_length + warble->block_length * warble->word_length;
}

void warble_generate_pitch(double* signal_out, int32_t length, double sample_rate, double frequency, double power_peak) {
	int32_t i;
	double t_step = 1 / sample_rate;
	for(i=0; i < length; i++) {
		const double window = 0.5 * (1 - cos((WARBLE_2PI * i) / (length - 1)));
		signal_out[i] += sin(i * t_step * WARBLE_2PI * frequency) * power_peak * window;
	}
}

void warble_swap_chars(uint8_t* input_string, int32_t* index, int32_t n) {
	int32_t i;
	for (i = n - 1; i > 0; i--)
	{
		int v = index[n - 1 - i];
		char tmp = input_string[i];
		input_string[i] = input_string[v];
		input_string[v] = tmp;
	}
}

void warble_unswap_chars(uint8_t* input_string, int32_t* index, int32_t n) {
	int32_t i;
	for (i = 1; i < n; i++) {
		int v = index[n - i - 1];
		int8_t tmp = input_string[i];
		input_string[i] = input_string[v];
		input_string[v] = tmp;
	}
}

void warble_print_chars(warble* this, uint8_t* block, int32_t block_size) {
    int32_t i;
    fprintf(this->verbose, "[");
    for (i = 0; i < block_size; i++)
    {
        if (i > 0) {
            fprintf(this->verbose, ",");
        }
        fprintf(this->verbose, "0x%x", block[i] & 0xff);
    }
    fprintf(this->verbose, "]\n");    
}

void warble_reed_encode_solomon(warble *warble, uint8_t* msg, uint8_t* block) {
	// Split message if its size > WARBLE_RS_P
	int msg_cursor;
	int block_cursor = 0;
	int remaining = warble->payloadSize % warble->rs_message_length;
	correct_reed_solomon *rs = correct_reed_solomon_create(
	correct_rs_primitive_polynomial_ccsds, 1, 1, warble->distance);
	for(msg_cursor = 0; msg_cursor < warble->payloadSize - remaining; msg_cursor += warble->rs_message_length) {
		correct_reed_solomon_encode(rs, &(msg[msg_cursor]), warble->rs_message_length, &(block[block_cursor]));
		block_cursor += warble->rs_message_length + warble->distance;
	}
	correct_reed_solomon_destroy(rs);
	// Forward error correction on the remaining message chars
	if(remaining > 0) {
		rs = correct_reed_solomon_create(
			correct_rs_primitive_polynomial_ccsds, 1, 1, warble->distance_last);
		correct_reed_solomon_encode(rs, &(msg[warble->payloadSize - remaining]), remaining, &(block[warble->block_length - remaining - warble->distance_last]));
		correct_reed_solomon_destroy(rs);
	}
	if(warble->payloadSize > warble->rs_message_length) {
        if (warble->verbose != NULL) {
            fprintf(warble->verbose, "# Swap message\n");
            warble_print_chars(warble, block, warble->block_length);
        }
		// Interleave message in order to spread consecutive errors on multiple reed solomon messages (increase robustness)
		warble_swap_chars(block, warble->shuffleIndex, warble->block_length);
        if (warble->verbose != NULL) {
            fprintf(warble->verbose, "# message swaped\n");
            warble_print_chars(warble, block, warble->block_length);
        }
	}
}

int warble_reed_decode_solomon(warble *warble, uint8_t* words, uint8_t* msg) {
	int32_t res = 0;
	int msg_cursor;
	int block_cursor = 0;
	int remaining = warble->payloadSize % warble->rs_message_length;
	if (warble->payloadSize > warble->rs_message_length) {
        if (warble->verbose != NULL) {
            fprintf(warble->verbose, "# unswap message\n");
            warble_print_chars(warble, words, warble->block_length);
        }

		// Deinterleave message
		warble_unswap_chars(words, warble->shuffleIndex, warble->block_length);

        if (warble->verbose != NULL) {
            fprintf(warble->verbose, "# message unswaped\n");
            warble_print_chars(warble, words, warble->block_length);
        }
	}
	correct_reed_solomon *rs = correct_reed_solomon_create(
		correct_rs_primitive_polynomial_ccsds, 1, 1, warble->distance);
	for (msg_cursor = 0; msg_cursor < warble->payloadSize - remaining; msg_cursor += warble->rs_message_length) {
		res = (int)correct_reed_solomon_decode(rs, &(words[block_cursor]), warble->rs_message_length + warble->distance, &(msg[msg_cursor]));
		if (res < 0) {
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

void warble_char_to_frequencies(warble *warble, int8_t c, double* f0, double* f1) {
	*f0 = warble->frequencies[(c & 255) % WARBLE_PITCH_ROOT];
	*f1 = warble->frequencies[WARBLE_PITCH_ROOT + (c & 255) / WARBLE_PITCH_ROOT];
}

void warble_generate_signal(warble *warble,double power_peak, uint8_t* words, double* signal_out) {
	int s = 0;
	// Trigger chirp
	int32_t i;
	for (i = 0; i < warble->chirp_length; i++) {
		signal_out[i] += warble->trigger_cache[i] * power_peak;
	}
	s += warble->chirp_length;

	// Other pitchs

	for(i=0; i < warble->block_length; i++) {
		double freq1,freq2;
		warble_char_to_frequencies(warble, words[i], &freq1, &freq2);
		warble_generate_pitch(&(signal_out[s]), warble->word_length, warble->sampleRate, freq1, power_peak);
		warble_generate_pitch(&(signal_out[s]), warble->word_length, warble->sampleRate, freq2, power_peak);
        if (warble->verbose != NULL) {
            fprintf(warble->verbose, "index.append((%d, [%g, %g], [%d, %d]))\n", s, freq1, freq2, (words[i] & 255) % WARBLE_PITCH_ROOT, WARBLE_PITCH_ROOT + (words[i] & 255) / WARBLE_PITCH_ROOT);
        }
		s += warble->word_length;
	}
}
// For java bindings

warble* warble_create() {
	warble* this = (warble*)malloc(sizeof(warble));
	return this;
}

int32_t warble_cfg_get_payloadSize(warble *warble) {
    return warble->payloadSize;
}

double warble_cfg_get_sampleRate(warble *warble) {
    return warble->sampleRate;
}
int32_t warble_cfg_get_block_length(warble *warble) {
    return warble->block_length;
}

int32_t warble_cfg_get_distance(warble *warble) {
    return warble->distance;
}
int32_t warble_cfg_get_rs_message_length(warble *warble) {
    return warble->rs_message_length;
}
int32_t warble_cfg_get_distance_last(warble *warble) {
    return warble->distance_last;
}
uint8_t* warble_cfg_get_parsed(warble *warble) {
    return warble->parsed;
}
int32_t* warble_cfg_get_shuffleIndex(warble *warble) {
    return warble->shuffleIndex;
}
double* warble_cfg_get_frequencies(warble *warble) {
    return warble->frequencies;
}

int64_t warble_cfg_get_triggerSampleIndexBegin(warble *warble) {
    return warble->triggerSampleIndexBegin;
}


int32_t warble_cfg_get_word_length(warble *warble) {
    return warble->word_length;
}

int32_t warble_cfg_get_window_length(warble *warble) {
    return warble->window_length;
}
