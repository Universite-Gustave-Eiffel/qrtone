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

import logging
import unittest
import pywarble
import math
import sys

def trace(frame, event, arg):
    print "%s, %s:%d" % (event, frame.f_code.co_filename, frame.f_lineno)
    return trace

"""
Some basic tests
"""

logging.basicConfig(level=logging.DEBUG)

##
# Unit test, check ut_reference.py for reference values on this unit test
class TestModule(unittest.TestCase):

    def create_default(self, payload):
        sample_rate = 44100
        first_frequency = 1760
        frequency_multiplication = 1.0594630943591
        frequency_increment = 0
        word_time = 0.0872
        message_size = len(payload)
        frequencies_index_triggers = [9, 25]
        snr_trigger = 10
        return pywarble.pywarble(sample_rate, first_frequency,
         frequency_multiplication, frequency_increment, word_time, message_size,
          frequencies_index_triggers, snr_trigger)

    ##
    # Test init
    def test_init(self):

        self.create_default("parrot")

    def test_warble(self):
    	sampleRate = 44100.0;
    	powerRMS = 500;
    	signalFrequency = 1000;
    	powerPeak = powerRMS * math.sqrt(2);
        audio = [math.sin(2 * math.pi * signalFrequency * s * (1 / sampleRate)) * (powerPeak) for s in range(int(sampleRate))]

    	freqs = [ 1000 ]

        warble = self.create_default("test")
    	out = warble._generalized_goertzel(audio, sampleRate, freqs);

    	signal_rms = warble._compute_rms(audio);

    	self.assertAlmostEqual(signal_rms, out[0], 1)

if __name__ == '__main__':
    sys.settrace(trace)
    suite = unittest.TestLoader().loadTestsFromTestCase(TestModule)
    unittest.TextTestRunner(verbosity=2).run(suite)
