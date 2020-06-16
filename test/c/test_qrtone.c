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
static const float values[] = { 11.0f,16.0f,23.0f,36.0f,58.0f,29.0f,20.0f,10.0f,8.0f,3.0f,0.0f,0.0f,2.0f,11.0f,27.0f,47.0f,63.0f,60.0f,39.0f,28.0f,26.0f,22.0f,11.0f,21.0f,40.0f,78.0f,122.0f,103.0f,73.0f,47.0f,35.0f,11.0f,5.0f,16.0f,34.0f,70.0f,81.0f,111.0f,101.0f,73.0f,40.0f,20.0f,16.0f,5.0f,11.0f,22.0f,40.0f,60.0f,80.9f,83.4f,47.7f,47.8f,30.7f,12.2f,9.6f,10.2f,32.4f,47.6f,54.0f,62.9f,85.9f,61.2f,45.1f,36.4f,20.9f,11.4f,37.8f,69.8f,106.1f,100.8f,81.6f,66.5f,34.8f,30.6f,7.0f,19.8f,92.5f,154.4f,125.9f,84.8f,68.1f,38.5f,22.8f,10.2f,24.1f,82.9f,132.0f,130.9f,118.1f,89.9f,66.6f,60.0f,46.9f,41.0f,21.3f,16.0f,6.4f,4.1f,6.8f,14.5f,34.0f,45.0f,43.1f,47.5f,42.2f,28.1f,10.1f,8.1f,2.5f,0.0f,1.4f,5.0f,12.2f,13.9f,35.4f,45.8f,41.1f,30.1f,23.9f,15.6f,6.6f,4.0f,1.8f,8.5f,16.6f,36.3f,49.6f,64.2f,67.0f,70.9f,47.8f,27.5f,8.5f,13.2f,56.9f,121.5f,138.3f,103.2f,85.7f,64.6f,36.7f,24.2f,10.7f,15.0f,40.1f,61.5f,98.5f,124.7f,96.3f,66.6f,64.5f,54.1f,39.0f,20.6f,6.7f,4.3f,22.7f,54.8f,93.8f,95.8f,77.2f,59.1f,44.0f,47.0f,30.5f,16.3f,7.3f,37.6f,74.0f,139.0f,111.2f,101.6f,66.2f,44.7f,17.0f,11.3f,12.4f,3.4f,6.0f,32.3f,54.3f,59.7f,63.7f,63.5f,52.2f,25.4f,13.1f,6.8f,6.3f,7.1f,35.6f,73.0f,85.1f,78.0f,64.0f,41.8f,26.2f,26.7f,12.1f,9.5f,2.7f,5.0f,24.4f,42.0f,63.5f,53.8f,62.0f,48.5f,43.9f,18.6f,5.7f,3.6f,1.4f,9.6f,47.4f,57.1f,103.9f,80.6f,63.6f,37.6f,26.1f,14.2f,5.8f,16.7f,44.3f,63.9f,69.0f,77.8f,64.9f,35.7f,21.2f,11.1f,5.7f,8.7f,36.1f,79.7f,114.4f,109.6f,88.8f,67.8f,47.5f,30.6f,16.3f,9.6f,33.2f,92.6f,151.6f,136.3f,134.7f,83.9f,69.4f,31.5f,13.9f,4.4f,38.0f,141.7f,190.2f,184.8f,159.0f,112.3f,53.9f,37.5f,27.9f,10.2f,15.1f,47.0f,93.8f,105.9f,105.5f,104.5f,66.6f,68.9f,38.0f,34.5f,15.5f,12.6f,27.5f,92.5f,155.4f,154.6f,140.4f,115.9f,66.6f,45.9f,17.9f,13.4f,29.3f,91.9f,149.2f,153.6f,135.9f,114.2f,70.1f,50.2f,20.5f,14.3f,31.3f,89.9f,151.5f,149.3f };

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

void qrtone_goertzel_init(qrtone_goertzel_t * this, float sample_rate, float frequency, int32_t window_size, int8_t hann_window);

void qrtone_goertzel_process_samples(qrtone_goertzel_t * this, float* samples, int32_t samples_len);

float qrtone_goertzel_compute_rms(qrtone_goertzel_t * this);

qrtone_percentile_t* qrtone_percentile_new(void);

void qrtone_percentile_free(qrtone_percentile_t * this);

float qrtone_percentile_result(qrtone_percentile_t * this);

void qrtone_percentile_add(qrtone_percentile_t * this, float data);

void qrtone_percentile_init_quantile(qrtone_percentile_t * this, float quant);

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

int64_t qrtone_find_peak_location(float p0, float p1, float p2, int64_t p1_location, int32_t window_length);

void qrtone_quadratic_interpolation(float p0, float p1, float p2, float* location, float* height, float* half_curvature);

void qrtone_interleave_symbols(int8_t * symbols, int32_t symbols_length, int32_t block_size);

void qrtone_deinterleave_symbols(int8_t * symbols, int32_t symbols_length, int32_t block_size);

void qrtone_generate_pitch(float* samples, int32_t samples_length, int32_t offset, float sample_rate, float frequency, float power_peak);

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
	const float sample_rate = 44100;
	float powerRMS = pow(10.0, -26.0 / 20.0); // -26 dBFS
	float signal_frequency = 1000;
	float powerPeak = powerRMS * sqrt(2);

	float audio[SAMPLES];
	int s;
	for (s = 0; s < SAMPLES; s++) {
		float t = s * (1 / (float)sample_rate);
		audio[s] = (float)(sin(2 * M_PI * signal_frequency * t) * (powerPeak));
	}

	qrtone_goertzel_t* goertzel = qrtone_goertzel_new();

	qrtone_goertzel_init(goertzel, sample_rate, signal_frequency, SAMPLES, 0);

	qrtone_goertzel_process_samples(goertzel, audio, SAMPLES);

	float signal_rms = qrtone_goertzel_compute_rms(goertzel);

	mu_assert_double_eq(20 * log10(powerRMS), 20 * log10(signal_rms), 0.01);

	free(goertzel);
}

MU_TEST(test1khzIterative) {
	const float sample_rate = 44100;
	float powerRMS = pow(10.0, -26.0 / 20.0); // -26 dBFS
	float signal_frequency = 1000;
	float powerPeak = powerRMS * sqrt(2);

	float audio[SAMPLES];
	int s;
	for (s = 0; s < SAMPLES; s++) {
		float t = s * (1 / (float)sample_rate);
		audio[s] = (float)(sin(2 * M_PI * signal_frequency * t) * (powerPeak));
	}

	qrtone_goertzel_t* goertzel = qrtone_goertzel_new();

	qrtone_goertzel_init(goertzel, sample_rate, signal_frequency, SAMPLES, 0);

	int32_t cursor = 0;

	while (cursor < SAMPLES) {
		int32_t window_size = MIN((rand() % 115) + 20, SAMPLES - cursor);
		qrtone_goertzel_process_samples(goertzel, audio + cursor, window_size);
		cursor += window_size;
	}

	float signal_rms = qrtone_goertzel_compute_rms(goertzel);

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

	mu_assert_double_eq(41.36084, qrtone_percentile_result(percentile), 0.00001);

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

	float testVals[] = { 4, 5, 7, 13, 10, 9, 9, 10, 4, 6, 7, 8, 11 , 3, 2, 2 };

	int32_t cursor = 0;
	for (i = 0; i < sizeof(testVals) / sizeof(float); i++) {
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

	float testVals[] = { 4, 5, 7, 13, 10, 9, 9, 10, 4, 6, 7, 8, 11 , 3, 2, 2 };

	int32_t cursor = 0;
	for (i = 0; i < sizeof(testVals) / sizeof(float); i++) {
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

#define GAUSSIAN_SAMPLES 141

MU_TEST(testPeakFinding) {
	float samples[GAUSSIAN_SAMPLES];
	float sigma = 0.5;
	// Create gaussian
	float maxVal = 0;
	int32_t max_index = 0;
	int32_t i;
	for (i = 0; i < GAUSSIAN_SAMPLES; i++) {
		samples[i] = expf(-1.0f / 2.0f * powf((i - GAUSSIAN_SAMPLES / 2.f) / (sigma * GAUSSIAN_SAMPLES / 2.f), 2.0f));
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
	float location, height, half_curvature;
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
float gaussrand()
{
	static float V1, V2, S;
	static int phase = 0;
	float X;

	if (phase == 0) {
		do {
			float U1 = (float)rand() / RAND_MAX;
			float U2 = (float)rand() / RAND_MAX;

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

void lvl_callback(void* ptr, int64_t location,  float first_tone_level, float second_tone_level, int32_t triggered) {
	char buffer[100];
	float time = location / 16000.0;
	int32_t size = sprintf(buffer, "%.3f, %.2f,%.2f,%d\n", time, first_tone_level, second_tone_level, triggered);
	fwrite(buffer, 1, size, (FILE*) ptr);
}


MU_TEST(testGenerate) {
	int writeFile = 1;
	FILE* f = NULL;
	FILE* fcsv = NULL;
	if(writeFile) {
		f = fopen("testGenerate_16000HZ_16bitsPCM.raw", "wb");
		fcsv = fopen("spectrum.csv", "w");
		char buffer[100];
		int32_t size = sprintf(buffer, "t,3603Hz,3952Hz,trigger\n");
		fwrite(buffer, 1, size, fcsv);
	}
	qrtone_t* qrtone = qrtone_new();
	float sample_rate = 16000;
	qrtone_init(qrtone, sample_rate);
	float powerRMS = pow(10.0, -26.0 / 20.0);
	float powerPeak = powerRMS * sqrt(2);
	float noise_peak = pow(10.0, -50.0 / 20.0);

	int32_t samples_length = qrtone_set_payload(qrtone, IPFS_PAYLOAD, sizeof(IPFS_PAYLOAD));

	int32_t offset_before = (int32_t)(sample_rate * 0.35);

	int32_t total_length = offset_before + samples_length + offset_before;

	int32_t cursor = 0;
	qrtone_t* qrtone_decoder = qrtone_new();
	qrtone_init(qrtone_decoder, sample_rate);
	qrtone_set_level_callback(qrtone_decoder, fcsv, lvl_callback);
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
				int16_t sample = (int16_t)(window[i] * 32768.0f);
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
		fclose(fcsv);
	}
	mu_assert(qrtone_get_payload(qrtone_decoder) != NULL, "no decoded message");

	if(qrtone_get_payload(qrtone_decoder) != NULL) {
		mu_assert_int_array_eq(IPFS_PAYLOAD, sizeof(IPFS_PAYLOAD), qrtone_get_payload(qrtone_decoder), qrtone_get_payload_length(qrtone_decoder));
	}

	mu_assert_double_eq(offset_before / sample_rate, qrtone_get_payload_sample_index(qrtone_decoder) / sample_rate, 0.001);

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
	float sample_rate = 44100;
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
	float sample_rate = 44100;
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
	float sample_rate = 44100;
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



MU_TEST(testReadArduino) {

	qrtone_t* qrtone = qrtone_new();
	float sample_rate = 16000;
	qrtone_init(qrtone, sample_rate);


	FILE* f = fopen("ipfs_16khz_16bits_mono.raw", "rb");
	mu_check(f != NULL);

	int16_t buffer[128];
	const int32_t number_of_samples = sizeof(buffer) / sizeof(int16_t);
	size_t res = number_of_samples;
	while (res == number_of_samples) {
		// Read short samples
		res = fread(buffer, sizeof(int16_t), number_of_samples, f);
		// Init float samples array
		float* window = malloc(sizeof(float) * res);
		memset(window, 0, sizeof(float) * res);
		int32_t i;
		for(i = 0; i < res; i++) {
			window[i] = buffer[i] / 32767.0f;
		}
		if (qrtone_push_samples(qrtone, window, res)) {
			// Got data
			free(window);
			break;
		}
		free(window);
	}
	fclose(f);

	mu_assert(qrtone_get_payload(qrtone) != NULL, "no decoded message");
	if (qrtone_get_payload(qrtone) != NULL) {
		mu_assert_int_array_eq(IPFS_PAYLOAD, sizeof(IPFS_PAYLOAD), qrtone_get_payload(qrtone), qrtone_get_payload_length(qrtone));
	}

	printf("\ntestReadArduino: %d errors fixed \n", qrtone_get_fixed_errors(qrtone));

	qrtone_free(qrtone);

	free(qrtone);
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
	MU_RUN_TEST(testReadArduino);
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

