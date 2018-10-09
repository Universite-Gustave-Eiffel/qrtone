

  def _compute_rms(const double* signal, int32_t s_length):
    return cpywarble.warble_compute_rms(signal, s_length)

  def _spectrum_to_char(self, double* rms):
    return cpywarble.spectrumToChar(self._c_pywarble, rms)


  def reed_encode_solomon(self, int8_t* msg):
    cdef int8_t * block = <int8_t>PyMem_Malloc(sizeof(int8_t) * self._c_pywarble.block_length)
    cpywarble.warble_reed_encode_solomon(self._c_pywarble, msg, block)
    return block

  def reed_decode_solomon(self, int8_t* words):
    cdef int8_t * msg = <int8_t>PyMem_Malloc(sizeof(int8_t) * self._c_pywarble.block_length)
    cpywarble.warble_reed_decode_solomon(self._c_pywarble, words, msg)
    return msg

  def swap_chars(int8_t* input_string, int32_t* index, int32_t n):
    cpywarble.warble_swap_chars(input_string, index, n)
    return input_string

  def unswap_chars(int8_t* input_string, int32_t* index, int32_t n):
    cpywarble.warble_unswap_chars(input_string, index, n)
    return input_string

  def fisher_yates_shuffle_index(int n, int* index):
    cpywarble.warble_fisher_yates_shuffle_index(n, index)
    return index


  def char_to_frequencies(self, int8_t c):
    cdef double f0
    cdef double f1
    cpywarble.warble_char_to_frequencies(self._c_pywarble, c, &f0, &f1)
    return f0, f1

  def get_highest_index(double* rms, const int32_t vfrom, const int32_t vto):
    return cpywarble.warble_get_highest_index(rms, vfrom, vto)
