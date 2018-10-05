
from libc.stdint cimport int8_t, int16_t, int32_t, int64_t
from libcpp cimport bool

cdef extern from "warble.h":
    ctypedef struct warble:
      pass
    cdef int WARBLE_PITCH_COUNT
    # Imports definitions from a c header file
    void warble_generalized_goertzel(const double* signal, int32_t s_length, double sample_rate, const double* frequencies, int32_t f_length, double* rms)
    double warble_compute_rms(const double* signal, int32_t s_length)
    int8_t spectrumToChar(warble *warble, double* rms)
    void warble_init(warble* this, double sample_rate, double first_frequency, double frequency_multiplication, int32_t frequency_increment, double word_time, int32_t message_size, int32_t* frequencies_index_triggers, int32_t frequencies_index_triggers_length, double snr_trigger)
    void warble_free(warble *warble)
    int32_t warble_feed(warble *warble, double* signal, int64_t sample_index)
    int32_t warble_generate_window_size(warble *warble)
    void warble_generate_signal(warble *warble,double powerPeak, int8_t* words, double* signal_out)
    void warble_reed_encode_solomon(warble *warble, int8_t* msg, int8_t* block)
    int warble_reed_decode_solomon(warble *warble, int8_t* words, int8_t* msg)
    void warble_swap_chars(int8_t* input_string, int32_t* index, int32_t n)
    void warble_unswap_chars(int8_t* input_string, int32_t* index, int32_t n)
    void warble_fisher_yates_shuffle_index(int n, int* index)
    int warble_rand(int64_t* next)
    void warble_char_to_frequencies(warble *warble, int8_t c, double* f0, double* f1)
    int warble_get_highest_index(double* rms, const int vfrom, const int vto)
    warble* warble_create()
