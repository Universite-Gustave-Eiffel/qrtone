"""
BSD 3-Clause License

Copyright (c) 2018, Ifsttar
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
"""

cimport cpywarble
from libc.stdint cimport int8_t, int16_t, int32_t, int64_t
from libc.stdlib cimport malloc
from libc.string cimport memcpy
from cpython cimport array
import array
from cpython.bytes cimport PyBytes_FromStringAndSize
from libcpp cimport bool
from cpython.mem cimport PyMem_Malloc

cdef array.array int_array_template = array.array('i', [])
cdef array.array double_array_template = array.array('d', [])

cdef class pywarble:
  cdef cpywarble.warble* _c_pywarble
  def __cinit__(self):
    self._c_pywarble = cpywarble.warble_create()
    if not self._c_pywarble:
      raise MemoryError()

  def __dealloc__(self):
    if self._c_pywarble is not NULL:
        cpywarble.warble_free(self._c_pywarble)

  def __init__(self, double sample_rate, double first_frequency,
   double frequency_multiplication, int32_t frequency_increment, double word_time,
  	int32_t message_size, list frequencies_index_triggers, double snr_trigger):
      cdef int[::1] cfrequencies_index_triggers = array.array('i',frequencies_index_triggers)
      cpywarble.warble_init(self._c_pywarble, sample_rate, first_frequency,
      	frequency_multiplication,
      	frequency_increment, word_time,
      	message_size, <int32_t*>&cfrequencies_index_triggers[0], cfrequencies_index_triggers.shape[0], snr_trigger)

  def _generalized_goertzel(self, list signal, double sample_rate, list frequencies):
    cdef double[::1] csignal = array.array('d',signal)
    cdef double[::1] cfrequencies = array.array('d',frequencies)
    cdef double[::1] rms = array.clone(double_array_template, len(frequencies), zero=False)
    cpywarble.warble_generalized_goertzel(&csignal[0], csignal.shape[0], sample_rate, &cfrequencies[0], cfrequencies.shape[0], &rms[0])
    return list(rms)

  def _compute_rms(self, list signal):
    cdef double[::1] csignal = array.array('d',signal)
    return cpywarble.warble_compute_rms(&csignal[0], csignal.shape[0])

  def generate_window_size(self):
    return cpywarble.warble_generate_window_size(self._c_pywarble)

  def generate_signal(self,double powerPeak, const char * words):
      cdef double[::1] signal_out = array.clone(double_array_template, cpywarble.warble_generate_window_size(self._c_pywarble), zero=False)
      cpywarble.warble_generate_signal(self._c_pywarble,powerPeak, <int8_t *>words, &signal_out[0])
      return list(signal_out)

  def feed(self, list signal, int64_t sample_index):
      if len(signal) == 0:
        return 0
      cdef double[::1] csignal = array.array('d',signal)
      return cpywarble.warble_feed(self._c_pywarble, &csignal[0], csignal.shape[0])

  def get_payload_size(self):
      return cpywarble.warble_cfg_get_payloadSize(self._c_pywarble);

  def get_frequencies_index_triggers_count(self):
      return cpywarble.warble_cfg_get_frequenciesIndexTriggersCount(self._c_pywarble)

  def get_frequencies_index_triggers(self):
      cdef double[::1] triggers = array.clone(double_array_template, cpywarble.warble_generate_window_size(self._c_pywarble), zero=False)
      memcpy(&triggers[0], cpywarble.warble_cfg_get_frequenciesIndexTriggers(self._c_pywarble), sizeof(double) * cpywarble.warble_cfg_get_frequenciesIndexTriggersCount(self._c_pywarble))
      return list(triggers)

  def get_sample_rate(self):
      return cpywarble.warble_cfg_get_sampleRate(self._c_pywarble)

  def get_block_length(self):
      return cpywarble.warble_cfg_get_block_length(self._c_pywarble)

  def get_distance(self):
      return cpywarble.warble_cfg_get_distance(self._c_pywarble)

  def get_rs_message_length(self):
      return cpywarble.warble_cfg_get_rs_message_length(self._c_pywarble)

  def get_distance_last(self):
      return cpywarble.warble_cfg_get_distance_last(self._c_pywarble)

  def get_parsed(self):
      return cpywarble.warble_cfg_get_parsed(self._c_pywarble)

  def get_shuffleIndex(self):
      cdef int[::1] index = array.clone(double_array_template, cpywarble.WARBLE_PITCH_COUNT, zero=False)
      memcpy(&index[0], cpywarble.warble_cfg_get_shuffleIndex(self._c_pywarble), sizeof(int32_t) * cpywarble.warble_cfg_get_block_length(self._c_pywarble))
      return list(index)

  def get_frequencies(self):
      cdef double[::1] freqs = array.clone(double_array_template, cpywarble.WARBLE_PITCH_COUNT, zero=False)
      memcpy(&freqs[0], cpywarble.warble_cfg_get_frequencies(self._c_pywarble), sizeof(double) * cpywarble.WARBLE_PITCH_COUNT);
      return list(freqs)

  def get_trigger_sample_index(self):
      return cpywarble.warble_cfg_get_triggerSampleIndex(self._c_pywarble)

  def get_trigger_sample_index_begin(self):
      return cpywarble.warble_cfg_get_triggerSampleIndexBegin(self._c_pywarble)

  def get_trigger_sample_rms(self):
      return cpywarble.warble_cfg_get_triggerSampleRMS(self._c_pywarble)

  def get_word_length(self):
      return cpywarble.warble_cfg_get_word_length(self._c_pywarble)

  def get_window_length(self):
      return cpywarble.warble_cfg_get_window_length(self._c_pywarble)
