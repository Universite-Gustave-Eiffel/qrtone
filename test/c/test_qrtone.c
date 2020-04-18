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

#define QRTONE_FLOAT_EPSILON 0.000001

#ifndef M_PI
#define M_PI 3.1415926535897932385
#endif

#define SAMPLES 2205

// sunspot data
static const int32_t years[] = { 1701,1702,1703,1704,1705,1706,1707,1708,1709,1710,1711,1712,1713,1714,1715,1716,1717,1718,1719,1720,1721,1722,1723,1724,1725,1726,1727,1728,1729,1730,1731,1732,1733,1734,1735,1736,1737,1738,1739,1740,1741,1742,1743,1744,1745,1746,1747,1748,1749,1750,1751,1752,1753,1754,1755,1756,1757,1758,1759,1760,1761,1762,1763,1764,1765,1766,1767,1768,1769,1770,1771,1772,1773,1774,1775,1776,1777,1778,1779,1780,1781,1782,1783,1784,1785,1786,1787,1788,1789,1790,1791,1792,1793,1794,1795,1796,1797,1798,1799,1800,1801,1802,1803,1804,1805,1806,1807,1808,1809,1810,1811,1812,1813,1814,1815,1816,1817,1818,1819,1820,1821,1822,1823,1824,1825,1826,1827,1828,1829,1830,1831,1832,1833,1834,1835,1836,1837,1838,1839,1840,1841,1842,1843,1844,1845,1846,1847,1848,1849,1850,1851,1852,1853,1854,1855,1856,1857,1858,1859,1860,1861,1862,1863,1864,1865,1866,1867,1868,1869,1870,1871,1872,1873,1874,1875,1876,1877,1878,1879,1880,1881,1882,1883,1884,1885,1886,1887,1888,1889,1890,1891,1892,1893,1894,1895,1896,1897,1898,1899,1900,1901,1902,1903,1904,1905,1906,1907,1908,1909,1910,1911,1912,1913,1914,1915,1916,1917,1918,1919,1920,1921,1922,1923,1924,1925,1926,1927,1928,1929,1930,1931,1932,1933,1934,1935,1936,1937,1938,1939,1940,1941,1942,1943,1944,1945,1946,1947,1948,1949,1950,1951,1952,1953,1954,1955,1956,1957,1958,1959,1960,1961,1962,1963,1964,1965,1966,1967,1968,1969,1970,1971,1972,1973,1974,1975,1976,1977,1978,1979,1980,1981,1982,1983,1984,1985,1986,1987,1988,1989,1990,1991,1992,1993,1994,1995,1996,1997,1998,1999,2000 };
static const double values[] = { 11.0,16.0,23.0,36.0,58.0,29.0,20.0,10.0,8.0,3.0,0.0,0.0,2.0,11.0,27.0,47.0,63.0,60.0,39.0,28.0,26.0,22.0,11.0,21.0,40.0,78.0,122.0,103.0,73.0,47.0,35.0,11.0,5.0,16.0,34.0,70.0,81.0,111.0,101.0,73.0,40.0,20.0,16.0,5.0,11.0,22.0,40.0,60.0,80.9,83.4,47.7,47.8,30.7,12.2,9.6,10.2,32.4,47.6,54.0,62.9,85.9,61.2,45.1,36.4,20.9,11.4,37.8,69.8,106.1,100.8,81.6,66.5,34.8,30.6,7.0,19.8,92.5,154.4,125.9,84.8,68.1,38.5,22.8,10.2,24.1,82.9,132.0,130.9,118.1,89.9,66.6,60.0,46.9,41.0,21.3,16.0,6.4,4.1,6.8,14.5,34.0,45.0,43.1,47.5,42.2,28.1,10.1,8.1,2.5,0.0,1.4,5.0,12.2,13.9,35.4,45.8,41.1,30.1,23.9,15.6,6.6,4.0,1.8,8.5,16.6,36.3,49.6,64.2,67.0,70.9,47.8,27.5,8.5,13.2,56.9,121.5,138.3,103.2,85.7,64.6,36.7,24.2,10.7,15.0,40.1,61.5,98.5,124.7,96.3,66.6,64.5,54.1,39.0,20.6,6.7,4.3,22.7,54.8,93.8,95.8,77.2,59.1,44.0,47.0,30.5,16.3,7.3,37.6,74.0,139.0,111.2,101.6,66.2,44.7,17.0,11.3,12.4,3.4,6.0,32.3,54.3,59.7,63.7,63.5,52.2,25.4,13.1,6.8,6.3,7.1,35.6,73.0,85.1,78.0,64.0,41.8,26.2,26.7,12.1,9.5,2.7,5.0,24.4,42.0,63.5,53.8,62.0,48.5,43.9,18.6,5.7,3.6,1.4,9.6,47.4,57.1,103.9,80.6,63.6,37.6,26.1,14.2,5.8,16.7,44.3,63.9,69.0,77.8,64.9,35.7,21.2,11.1,5.7,8.7,36.1,79.7,114.4,109.6,88.8,67.8,47.5,30.6,16.3,9.6,33.2,92.6,151.6,136.3,134.7,83.9,69.4,31.5,13.9,4.4,38.0,141.7,190.2,184.8,159.0,112.3,53.9,37.5,27.9,10.2,15.1,47.0,93.8,105.9,105.5,104.5,66.6,68.9,38.0,34.5,15.5,12.6,27.5,92.5,155.4,154.6,140.4,115.9,66.6,45.9,17.9,13.4,29.3,91.9,149.2,153.6,135.9,114.2,70.1,50.2,20.5,14.3,31.3,89.9,151.5,149.3 };

// Send Ipfs address
// Python code:
// import base58
// import struct
// s = struct.Struct('b').unpack
// payload = map(lambda v : s(v)[0], base58.b58decode("QmXjkFQjnD8i8ntmwehoAHBfJEApETx8ebScyVzAHqgjpD"))
int8_t IPFS_PAYLOAD[] = { 18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114 };

typedef struct _qrtone_crc8_t qrtone_crc8_t;

typedef struct _qrtone_crc16_t qrtone_crc16_t;

typedef struct _qrtone_goertzel_t qrtone_goertzel_t;

typedef struct _qrtone_percentile_t qrtone_percentile_t;

typedef struct _qrtone_array_t qrtone_array_t;

typedef struct _qrtone_peak_finder_t qrtone_peak_finder_t;

typedef struct _qrtone_header_t qrtone_header_t;

typedef struct _qrtone_trigger_analyzer_t qrtone_trigger_analyzer_t;

qrtone_crc8_t* qrtone_crc8_new(void);

void qrtone_crc8_init(qrtone_crc8_t * this);

void qrtone_crc8_add(qrtone_crc8_t * this, const int8_t data);

uint8_t qrtone_crc8_get(qrtone_crc8_t * this);

void qrtone_crc8_add_array(qrtone_crc8_t * this, const int8_t * data, const int32_t data_length);

qrtone_crc16_t* qrtone_crc16_new(void);

void qrtone_crc16_init(qrtone_crc16_t * this);

void qrtone_crc16_add_array(qrtone_crc16_t * this, const int8_t * data, const int32_t data_length);

int32_t qrtone_crc16_get(qrtone_crc16_t * this);

qrtone_goertzel_t* qrtone_goertzel_new(void);

void qrtone_goertzel_reset(qrtone_goertzel_t * this);

void qrtone_goertzel_init(qrtone_goertzel_t * this, double sample_rate, double frequency, int32_t window_size);

void qrtone_goertzel_process_samples(qrtone_goertzel_t * this, float* samples, int32_t samples_len);

double qrtone_goertzel_compute_rms(qrtone_goertzel_t * this);

qrtone_percentile_t* qrtone_percentile_new(void);

void qrtone_percentile_free(qrtone_percentile_t * this);

double qrtone_percentile_result(qrtone_percentile_t * this);

void qrtone_percentile_add(qrtone_percentile_t * this, double data);

void qrtone_percentile_init_quantile(qrtone_percentile_t * this, double quant);

qrtone_array_t* qrtone_array_new(void);

float qrtone_array_last(qrtone_array_t * this);

int32_t qrtone_array_size(qrtone_array_t * this);

void qrtone_array_clear(qrtone_array_t * this);

float qrtone_array_get(qrtone_array_t * this, int32_t index);

void qrtone_array_init(qrtone_array_t * this, int32_t length);

void qrtone_array_free(qrtone_array_t * this);

void qrtone_array_add(qrtone_array_t * this, float value);

void qrtone_peak_finder_init(qrtone_peak_finder_t * this, int32_t min_increase_count, int32_t min_decrease_count);

qrtone_peak_finder_t* qrtone_peak_finder_new(void);

int64_t qrtone_peak_finder_get_last_peak_index(qrtone_peak_finder_t * this);

int8_t qrtone_peak_finder_add(qrtone_peak_finder_t * this, int64_t index, float value);

void qrtone_hann_window(float* signal, int32_t signal_length, int32_t window_length, int32_t offset);

int64_t qrtone_find_peak_location(double p0, double p1, double p2, int64_t p1_location, int32_t window_length);

void qrtone_quadratic_interpolation(double p0, double p1, double p2, double* location, double* height, double* half_curvature);

void qrtone_interleave_symbols(int8_t * symbols, int32_t symbols_length, int32_t block_size);

void qrtone_deinterleave_symbols(int8_t * symbols, int32_t symbols_length, int32_t block_size);

void qrtone_generate_pitch(float* samples, int32_t samples_length, int32_t offset, double sample_rate, float frequency, double power_peak);

qrtone_header_t* qrtone_header_new(void);

void qrtone_header_init(qrtone_header_t * this, uint8_t length, int32_t block_symbols_size, int32_t block_ecc_symbols, int8_t crc, int8_t ecc_level);

void qrtone_header_encode(qrtone_header_t * this, int8_t * data);

int8_t qrtone_header_init_from_data(qrtone_header_t * this, int8_t * data);

uint8_t qrtone_header_get_length(qrtone_header_t* this);

int8_t qrtone_header_get_crc(qrtone_header_t* this);

int8_t qrtone_header_get_ecc_level(qrtone_header_t* this);

int32_t qrtone_header_get_payload_symbols_size(qrtone_header_t* this);

int32_t qrtone_header_get_payload_byte_size(qrtone_header_t* this);

int32_t qrtone_header_get_number_of_blocks(qrtone_header_t* this);

int32_t qrtone_header_get_number_of_symbols(qrtone_header_t* this);

int8_t* qrtone_symbols_to_payload(qrtone_t * this, int8_t * symbols, int32_t symbols_length, int32_t block_symbols_size, int32_t block_ecc_symbols, int8_t has_crc);

void qrtone_payload_to_symbols(qrtone_t * this, int8_t * payload, uint8_t payload_length, int32_t block_symbols_size, int32_t block_ecc_symbols, int8_t has_crc, int8_t * symbols);


MU_TEST(testCRC8) {
	int8_t data[] = { 0x0A, 0x0F, 0x08, 0x01, 0x05, 0x0B, 0x03 };

	qrtone_crc8_t* crc = qrtone_crc8_new();

	qrtone_crc8_init(crc);

	qrtone_crc8_add_array(crc, data, sizeof(data));

	mu_assert_int_eq(0xEA, qrtone_crc8_get(crc));

	free(crc);
}

MU_TEST(testCRC16) {
	int8_t data[] = { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J' };

	qrtone_crc16_t* crc = qrtone_crc16_new();

	qrtone_crc16_init(crc);

	qrtone_crc16_add_array(crc, data, sizeof(data));

	mu_assert_int_eq(0x0C9E, qrtone_crc16_get(crc));

	free(crc);
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

	qrtone_goertzel_t* goertzel = qrtone_goertzel_new();

	qrtone_goertzel_init(goertzel, sample_rate, signal_frequency, SAMPLES);

	qrtone_goertzel_process_samples(goertzel, audio, SAMPLES);

	double signal_rms = qrtone_goertzel_compute_rms(goertzel);

	mu_assert_double_eq(20 * log10(powerRMS), 20 * log10(signal_rms), 0.01);

	free(goertzel);
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

	qrtone_goertzel_t* goertzel = qrtone_goertzel_new();

	qrtone_goertzel_init(goertzel, sample_rate, signal_frequency, SAMPLES);

	int32_t cursor = 0;

	while (cursor < SAMPLES) {
		int32_t window_size = MIN((rand() % 115) + 20, SAMPLES - cursor);
		qrtone_goertzel_process_samples(goertzel, audio + cursor, window_size);
		cursor += window_size;
	}

	double signal_rms = qrtone_goertzel_compute_rms(goertzel);

	mu_assert_double_eq(20 * log10(powerRMS), 20 * log10(signal_rms), 0.01);

	free(goertzel);
}


MU_TEST(testPercentile) {
	qrtone_percentile_t* percentile = qrtone_percentile_new();

	qrtone_percentile_init_quantile(percentile, 0.5);
	int32_t i;
	for (i = 0; i < sizeof(years) / sizeof(int32_t); i++) {
		qrtone_percentile_add(percentile, values[i]);
	}

	mu_assert_double_eq(41.360847658017306, qrtone_percentile_result(percentile), 0.0000001);

	qrtone_percentile_free(percentile);

	free(percentile);
}


MU_TEST(testCircularArray) {
	qrtone_array_t* a = qrtone_array_new();

	qrtone_array_init(a, 5);
	mu_assert_int_eq(0, qrtone_array_size(a));
	qrtone_array_add(a, 0.5f);
	mu_assert_int_eq(1, qrtone_array_size(a));
	mu_assert_double_eq(0.5f, qrtone_array_get(a, 0), QRTONE_FLOAT_EPSILON);
	qrtone_array_add(a, 0.1f);	
	mu_assert_int_eq(2, qrtone_array_size(a));
	mu_assert_double_eq(0.5f, qrtone_array_get(a, 0), QRTONE_FLOAT_EPSILON);
	mu_assert_double_eq(0.1f, qrtone_array_get(a, 1), QRTONE_FLOAT_EPSILON);
	mu_assert_double_eq(0.1f, qrtone_array_last(a), QRTONE_FLOAT_EPSILON);
	qrtone_array_add(a, 0.2f);
	mu_assert_int_eq(3, qrtone_array_size(a));
	qrtone_array_add(a, 0.3f);
	mu_assert_int_eq(4, qrtone_array_size(a));
	qrtone_array_add(a, 0.5f);
	mu_assert_int_eq(5, qrtone_array_size(a));
	qrtone_array_add(a, 0.9f);
	mu_assert_int_eq(5, qrtone_array_size(a));
	qrtone_array_add(a, 1.0f);
	mu_assert_int_eq(5, qrtone_array_size(a));
	mu_assert_double_eq(1.0f, qrtone_array_last(a), QRTONE_FLOAT_EPSILON);
	mu_assert_double_eq(1.0f, qrtone_array_get(a, 4), QRTONE_FLOAT_EPSILON);
	mu_assert_double_eq(0.9f, qrtone_array_get(a, 3), QRTONE_FLOAT_EPSILON);
	mu_assert_double_eq(0.5f, qrtone_array_get(a, 2), QRTONE_FLOAT_EPSILON);
	mu_assert_double_eq(0.3f, qrtone_array_get(a, 1), QRTONE_FLOAT_EPSILON);
	mu_assert_double_eq(0.2f, qrtone_array_get(a, 0), QRTONE_FLOAT_EPSILON);

	qrtone_array_clear(a);
	mu_assert_int_eq(0, qrtone_array_size(a));
	qrtone_array_add(a, 0.5f);
	mu_assert_int_eq(1, qrtone_array_size(a));
	qrtone_array_free(a);

	free(a);
}


MU_TEST(testPeakFinder1) {
	qrtone_peak_finder_t* p = qrtone_peak_finder_new();

	qrtone_peak_finder_init(p, -1, -1);

	int32_t i;

	int32_t expected[] = { 5,  17,  27,  38,  50,  52,  61,  69,  78,  87, 102, 104, 116, 130, 137, 148, 160, 164, 170, 177, 183, 193, 198, 205, 207, 217, 228, 237, 247, 257, 268, 272, 279, 290, 299 };

	int32_t cursor = 0;
	for (i = 0; i < sizeof(years) / sizeof(int32_t); i++) {
		if (qrtone_peak_finder_add(p, (int64_t)i + 1, (float)values[i])) {
			mu_assert_int_eq(expected[cursor++], (int32_t)qrtone_peak_finder_get_last_peak_index(p));
		}
	}

	mu_assert_int_eq(cursor, sizeof(expected) / sizeof(int32_t));

	free(p);
}




MU_TEST(findPeaksIncreaseCondition) {
	qrtone_peak_finder_t* p = qrtone_peak_finder_new();

	qrtone_peak_finder_init(p, 3, -1);

	int32_t i;

	int32_t expected[] = { 3, 12 };

	double testVals[] = { 4, 5, 7, 13, 10, 9, 9, 10, 4, 6, 7, 8, 11 , 3, 2, 2 };

	int32_t cursor = 0;
	for (i = 0; i < sizeof(testVals) / sizeof(double); i++) {
		if (qrtone_peak_finder_add(p, i, (float)testVals[i])) {
			mu_assert_int_eq(expected[cursor++], (int32_t)qrtone_peak_finder_get_last_peak_index(p));
		}
	}

	mu_assert_int_eq(cursor, sizeof(expected) / sizeof(int32_t));

	free(p);
}




MU_TEST(findPeaksDecreaseCondition) {
	qrtone_peak_finder_t* p = qrtone_peak_finder_new();

	qrtone_peak_finder_init(p, -1, 2);

	int32_t i;

	int32_t expected[] = { 3, 12 };

	double testVals[] = { 4, 5, 7, 13, 10, 9, 9, 10, 4, 6, 7, 8, 11 , 3, 2, 2 };

	int32_t cursor = 0;
	for (i = 0; i < sizeof(testVals) / sizeof(double); i++) {
		if (qrtone_peak_finder_add(p, i , (float)testVals[i])) {
			mu_assert_int_eq(expected[cursor++], (int32_t)qrtone_peak_finder_get_last_peak_index(p));
		}
	}

	mu_assert_int_eq(cursor, sizeof(expected) / sizeof(int32_t));

	free(p);
}

MU_TEST(testHannWindow) {
	float ref[] = { 0.f,0.0039426493f,0.015708419f,0.035111757f,0.06184666f,0.095491503f,0.13551569f,0.18128801f,
				0.2320866f,0.28711035f,0.3454915f,0.40630934f,0.46860474f,0.53139526f,0.59369066f,0.6545085f,
				0.71288965f,0.7679134f,0.81871199f,0.86448431f,0.9045085f,0.93815334f,0.96488824f,0.98429158f,
				0.99605735f,1.f,0.99605735f,0.98429158f,0.96488824f,0.93815334f,0.9045085f,0.86448431f,0.81871199f,
				0.7679134f,0.71288965f,0.6545085f,0.59369066f,0.53139526f,0.46860474f,0.40630934f,0.3454915f,
				0.28711035f,0.2320866f,0.18128801f,0.13551569f,0.095491503f,0.06184666f,0.035111757f,0.015708419f,
				0.0039426493f,0.f };
	int32_t window_length = sizeof(ref) / sizeof(float);
	float* signal = malloc(sizeof(ref));
	int32_t i;
	for (i = 0; i < window_length; i++) {
		signal[i] = 1.0f;
	}
	qrtone_hann_window(signal, window_length, window_length, 0);
	for (i = 0; i < window_length; i++) {
		mu_assert_double_eq(ref[i], signal[i], QRTONE_FLOAT_EPSILON);
	}

	free(signal);
}

#define GAUSSIAN_SAMPLES 521

MU_TEST(testPeakFinding) {
	float samples[GAUSSIAN_SAMPLES];
	double sigma = 0.5;
	// Create gaussian
	float maxVal = 0;
	int32_t max_index = 0;
	int32_t i;
	for (i = 0; i < GAUSSIAN_SAMPLES; i++) {
		samples[i] = (float)exp(-1.0 / 2.0 * pow((i - GAUSSIAN_SAMPLES / 2.) / (sigma * GAUSSIAN_SAMPLES / 2.), 2));
		if (maxVal < samples[i]) {
			maxVal = samples[i];
			max_index = i;
		}
	}
	// emulate windowed evaluation
	maxVal = 0;
	int32_t window_index = 0;
	int32_t window = 35;
	for (i = window; i < GAUSSIAN_SAMPLES; i += window) {
		if (maxVal < samples[i]) {
			maxVal = samples[i];
			window_index = i;
		}
	}
	// Estimation of peak position
	int64_t location_est = qrtone_find_peak_location(samples[window_index - window], samples[window_index], samples[window_index + window], window_index, window);
	mu_assert_int_eq(max_index, (int32_t)location_est);

	// Estimation of peak height, should be 1.0
	double location, height, half_curvature;
	qrtone_quadratic_interpolation(samples[window_index - window], samples[window_index], samples[window_index + window], &location, &height, &half_curvature);
	mu_assert_double_eq(1.0, height, 0.001);
}

MU_TEST(testSymbolsInterleaving) {
	int8_t data[] = { 'a', 'b', 'c', '1', '2', '3', 'd', 'e', 'f', '4', '5', '6', 'g', 'h' };
	int8_t data_expected[] = { 'a', '1', 'd', '4', 'g', 'b', '2', 'e', '5', 'h', 'c', '3', 'f', '6' };
	int8_t* data_interleaved = malloc(sizeof(data));
	memcpy(data_interleaved, data, sizeof(data));
	qrtone_interleave_symbols(data_interleaved, sizeof(data), 3);
	mu_assert_int_array_eq(data_expected, sizeof(data), data_interleaved, sizeof(data));
	qrtone_deinterleave_symbols(data_interleaved, sizeof(data), 3);
	mu_assert_int_array_eq(data, sizeof(data), data_interleaved, sizeof(data));
	free(data_interleaved);
}

// Marsaglia and Bray, ``A Convenient Method for Generating Normal Variables'' 
double gaussrand()
{
	static double V1, V2, S;
	static int phase = 0;
	double X;

	if (phase == 0) {
		do {
			double U1 = (double)rand() / RAND_MAX;
			double U2 = (double)rand() / RAND_MAX;

			V1 = 2 * U1 - 1;
			V2 = 2 * U2 - 1;
			S = V1 * V1 + V2 * V2;
		} while (S >= 1 || S == 0);

		X = V1 * sqrt(-2 * log(S) / S);
	}
	else
		X = V2 * sqrt(-2 * log(S) / S);

	phase = 1 - phase;

	return X;
}

MU_TEST(testGenerate) {
	int writeFile = 0;
	FILE* f;
	if(writeFile) {
		f = fopen("inputsignal_44100_16bitsPCM.raw", "wb");
	}
	qrtone_t* qrtone = qrtone_new();
	double sample_rate = 44100;
	qrtone_init(qrtone, sample_rate);
	double powerRMS = pow(10.0, -26.0 / 20.0);
	double powerPeak = powerRMS * sqrt(2);
	double noise_peak = pow(10.0, -50.0 / 20.0);

	int32_t samples_length = qrtone_set_payload(qrtone, IPFS_PAYLOAD, sizeof(IPFS_PAYLOAD));

	int32_t offset_before = (int32_t)(sample_rate * 0.35);

	int32_t total_length = offset_before + samples_length + offset_before;

	int32_t cursor = 0;
	qrtone_t* qrtone_decoder = qrtone_new();
	qrtone_init(qrtone_decoder, sample_rate);
	while (cursor < total_length) {
		int32_t window_size = MIN(qrtone_get_maximum_length(qrtone_decoder), total_length - cursor);
		float* window = malloc(sizeof(float) * window_size);
		memset(window, 0, sizeof(float) * window_size);
		// add audio samples
		if(cursor + window_size > offset_before && cursor < samples_length - offset_before) {
			qrtone_get_samples(qrtone, window + MAX(0, offset_before - cursor), window_size - MAX(0, offset_before - cursor), MAX(0, cursor - offset_before), (float_t)powerPeak);
		}
		// add noise
		qrtone_generate_pitch(window, window_size, cursor, sample_rate, 125.0f, noise_peak);
		int32_t i;
		for(i=0; i < window_size; i++) {
			if(writeFile) {
				int16_t sample = (int16_t)(window[i] * 32768);
				fwrite(&sample, sizeof(int16_t), 1, f);
			}
		}
		if(qrtone_push_samples(qrtone_decoder, window, window_size)) {
			// Got data
			free(window);
			break;
		}
		cursor += window_size;
		free(window);
	}
	if(writeFile) {
		fclose(f);
	}
	mu_assert(qrtone_get_payload(qrtone_decoder) != NULL, "no decoded message");
	if(qrtone_get_payload(qrtone_decoder) != NULL) {
		mu_assert_int_array_eq(IPFS_PAYLOAD, sizeof(IPFS_PAYLOAD), qrtone_get_payload(qrtone_decoder), qrtone_get_payload_length(qrtone_decoder));
	}

	qrtone_free(qrtone);
	qrtone_free(qrtone_decoder);
	free(qrtone);
	free(qrtone_decoder);
}

MU_TEST(testHeaderEncodeDecode) {
	qrtone_header_t* header = qrtone_header_new();
	qrtone_header_init(header, sizeof(IPFS_PAYLOAD), 14, 2, 1, 0);

	int8_t headerdata[3];
	qrtone_header_encode(header, headerdata);

	qrtone_header_t* decoded_header = qrtone_header_new();
	qrtone_header_init_from_data(decoded_header, headerdata);

	mu_assert_int_eq(qrtone_header_get_length(header), qrtone_header_get_length(decoded_header));
	mu_assert_int_eq(qrtone_header_get_ecc_level(header), qrtone_header_get_ecc_level(decoded_header));
	mu_assert_int_eq(qrtone_header_get_crc(header), qrtone_header_get_crc(decoded_header));
	mu_assert_int_eq(qrtone_header_get_number_of_blocks(header), qrtone_header_get_number_of_blocks(decoded_header));

	free(header);
	free(decoded_header);
}


MU_TEST(testWriteSignal) {
	FILE* f = fopen("audioTest_44100_16bitsPCM.raw", "wb");
	if (f == NULL)
	{
		printf("Error opening file!\n");
		exit(1);
	}

	qrtone_t* qrtone = qrtone_new();
	double sample_rate = 44100;
	qrtone_init(qrtone, sample_rate);

	float power_peak = powf(10.0f, -16.0f / 20.0f);

	int blankBefore = (int)(sample_rate * 0.55);
	int blankAfter = (int)(sample_rate * 0.6);

	int8_t payload[] = {0x00, 0x04, 'n', 'i' , 'c' , 'o', 0x01, 0x05, 'h', 'e', 'l', 'l', 'o' };

	int32_t samples_length = blankBefore + blankAfter + qrtone_set_payload(qrtone, payload, sizeof(payload));

	float* samples = malloc(sizeof(float) * samples_length);

	memset(samples, 0, sizeof(float) * samples_length);

	qrtone_get_samples(qrtone, samples + blankBefore, samples_length, 0, power_peak);


	// Write signal
	int s;
	float factor = 32767.f / (power_peak * 4);
	for (s = 0; s < samples_length; s++) {
		int16_t sample = (int16_t)(samples[s] * factor);
		fwrite(&sample, sizeof(int16_t), 1, f);
	}

	qrtone_free(qrtone);
	free(samples);

	fclose(f);

	free(qrtone);
}


MU_TEST(testSymbolsEncodingDecoding) {
	qrtone_t* qrtone = qrtone_new();
	double sample_rate = 44100;
	qrtone_init(qrtone, sample_rate);

	int8_t payload[] = { 0x00, 0x04, 'n', 'i' , 'c' , 'o', 0x01, 0x05, 'h', 'e', 'l', 'l', 'o' };
	int8_t expected_symbols[] = { 0, 0, 6, 0, 1, 15, 0, 0, 8, 4, 5, 12, 6, 6, 13, 14, 8, 0, 6, 6, 6, 9, 5, 14, 6, 6, 3, 12, 6, 6, 15, 12, 2, 9, 6, 7 };;

	int32_t block_symbols_size = 14;
	int32_t block_ecc_symbols = 2;

	qrtone_header_t* header = qrtone_header_new();
	qrtone_header_init(header, sizeof(payload), block_symbols_size, block_ecc_symbols, 1, 0);

	int8_t headerdata[3];
	qrtone_header_encode(header, headerdata);

	int8_t* symbols = malloc(qrtone_header_get_number_of_symbols(header));

	qrtone_payload_to_symbols(qrtone, payload, sizeof(payload), block_symbols_size, block_ecc_symbols, qrtone_header_get_crc(header), symbols);

	mu_assert_int_array_eq(expected_symbols, sizeof(expected_symbols), symbols, qrtone_header_get_number_of_symbols(header));

	// revert back to payload

	int8_t* decoded_payload = qrtone_symbols_to_payload(qrtone, symbols, qrtone_header_get_number_of_symbols(header), block_symbols_size, block_ecc_symbols, qrtone_header_get_crc(header));

	mu_assert(decoded_payload != NULL, "CRC Failed");

	mu_assert_int_eq(0, qrtone_get_fixed_errors(qrtone));

	mu_assert_int_array_eq(payload, sizeof(payload), decoded_payload, qrtone_header_get_length(header));

	free(symbols);
	free(decoded_payload);
	qrtone_free(qrtone);
	free(qrtone);
	free(header);
}


MU_TEST(testSymbolsEncodingDecodingWithError) {
	qrtone_t* qrtone = qrtone_new();
	double sample_rate = 44100;
	qrtone_init(qrtone, sample_rate);

	int8_t payload[] = { 0x00, 0x04, 'n', 'i' , 'c' , 'o', 0x01, 0x05, 'h', 'e', 'l', 'l', 'o' };
	int8_t expected_symbols[] = { 0, 0, 6, 0, 1, 15, 0, 0, 8, 4, 5, 12, 6, 6, 13, 14, 8, 0, 6, 6, 6, 9, 5, 14, 6, 6, 3, 12, 6, 6, 15, 12, 2, 9, 6, 7 };

	int32_t block_symbols_size = 14;
	int32_t block_ecc_symbols = 2;

	qrtone_header_t* header = qrtone_header_new();
	qrtone_header_init(header, sizeof(payload), block_symbols_size, block_ecc_symbols, 1, 0);

	int8_t headerdata[3];
	qrtone_header_encode(header, headerdata);

	int8_t* symbols = malloc(qrtone_header_get_number_of_symbols(header));

	qrtone_payload_to_symbols(qrtone, payload, sizeof(payload), block_symbols_size, block_ecc_symbols, qrtone_header_get_crc(header), symbols);

	mu_assert_int_array_eq(expected_symbols, sizeof(expected_symbols), symbols, qrtone_header_get_number_of_symbols(header));

	// Insert error
	symbols[5] = MAX(0, MIN(15, ~symbols[5]));

	// revert back to payload

	int8_t* decoded_payload = qrtone_symbols_to_payload(qrtone, symbols, qrtone_header_get_number_of_symbols(header), block_symbols_size, block_ecc_symbols, qrtone_header_get_crc(header));

	mu_assert(decoded_payload != NULL, "CRC Failed");

	mu_assert_int_eq(1, qrtone_get_fixed_errors(qrtone));

	mu_assert_int_array_eq(payload, sizeof(payload), decoded_payload, qrtone_header_get_length(header));

	free(symbols);
	free(decoded_payload);
	qrtone_free(qrtone);

	free(qrtone);
	free(header);
}

MU_TEST_SUITE(test_suite) {
	MU_RUN_TEST(testCRC8);
	MU_RUN_TEST(testCRC16);
	MU_RUN_TEST(test1khz);
	MU_RUN_TEST(test1khzIterative);
	MU_RUN_TEST(testPercentile);
	MU_RUN_TEST(testCircularArray);
	MU_RUN_TEST(testPeakFinder1);
	MU_RUN_TEST(findPeaksIncreaseCondition);
	MU_RUN_TEST(findPeaksDecreaseCondition);
	MU_RUN_TEST(testHannWindow);
	MU_RUN_TEST(testPeakFinding);
	MU_RUN_TEST(testSymbolsInterleaving);
	MU_RUN_TEST(testGenerate);
	//MU_RUN_TEST(testWriteSignal);
	MU_RUN_TEST(testHeaderEncodeDecode);
	MU_RUN_TEST(testSymbolsEncodingDecoding);
	MU_RUN_TEST(testSymbolsEncodingDecodingWithError);
}

int main(int argc, char** argv) {
	//_crtBreakAlloc = 4410;
	MU_RUN_SUITE(test_suite);
	MU_REPORT();
#ifdef _WIN32
#ifdef _DEBUG
	_CrtDumpMemoryLeaks(); //Display memory leaks
#endif
#endif
	return minunit_status == 1 || minunit_fail > 0 ? -1 : 0;
}

