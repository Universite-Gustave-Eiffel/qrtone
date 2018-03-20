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

package org.noise_planet.openwarble;

import org.junit.Test;
import org.renjin.gcc.runtime.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class OpenWarbleTest {
  // Frequency factor, related to piano key
  // https://en.wikipedia.org/wiki/Piano_key_frequencies
  private static final double MULT = 1.0594630943591;

  public static short[] convertBytesToShort(byte[] buffer, int length, ByteOrder byteOrder) {
    ShortBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, length).order(byteOrder).asShortBuffer();
    short[] samplesShort = new short[byteBuffer.capacity()];
    byteBuffer.order();
    byteBuffer.get(samplesShort);
    return samplesShort;
  }

  public static short[] loadShortStream(InputStream inputStream, ByteOrder byteOrder) throws IOException {
    short[] fullArray = new short[0];
    byte[] buffer = new byte[4096];
    int read;
    // Read input signal up to buffer.length
    while ((read = inputStream.read(buffer)) != -1) {
      // Convert bytes into double values. Samples array size is 8 times inferior than buffer size
      if (read < buffer.length) {
        buffer = Arrays.copyOfRange(buffer, 0, read);
      }
      short[] signal = convertBytesToShort(buffer, buffer.length, byteOrder);
      short[] nextFullArray = new short[fullArray.length + signal.length];
      if(fullArray.length > 0) {
        System.arraycopy(fullArray, 0, nextFullArray, 0, fullArray.length);
      }
      System.arraycopy(signal, 0, nextFullArray, fullArray.length, signal.length);
      fullArray = nextFullArray;
    }
    return fullArray;
  }

  @Test
  public void coreRecording1Test() throws IOException {
    char expected_payload[] = { 18, 32, 139, 163, 206, 2, 52, 26, 139, 93, 119, 147, 39, 46, 108, 4, 31, 36, 156,
            95, 247, 186, 174, 163, 181, 224, 193, 42, 212, 156, 50, 83, 138, 114 };
    double samplingRate = 44100;
    double word_length = 0.0872; // pitch length in seconds
    InputStream inputStream = OpenWarbleTest.class.getResourceAsStream("audioTest_44100_16bitsPCM_0.0872s_1760.raw");
    short[] signal_short = loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
    // Convert into double
    double[] signal = new double[signal_short.length];
    for(int i=0;i< signal_short.length;i++) {
      signal[i] = signal_short[i];
    }
    Ptr cfg = warble.warble_create();

    warble.warble_init(cfg, samplingRate, 1760, MULT, (short)0, word_length, expected_payload.length, new IntPtr(new int[]{9, 25}), (short)2);

    // Encode test message
    byte[] words = new byte[warble.warble_cfg_get_block_length(cfg)];
    warble.warble_reed_encode_solomon(cfg, new CharPtr(expected_payload), new BytePtr(words));

    int window_length = warble.warble_cfg_get_window_length(cfg);
    int res;
    for(int i=0; i < signal.length - window_length; i+= window_length) {
      res = warble.warble_feed(cfg, new DoublePtr(Arrays.copyOfRange(signal, i, i + window_length)), i);
      assertNotEquals(-1, res);
      if(res == 1) {
        // Feed complete
        // Check parsed frequencies from signal with frequencies computed from expected payload
        for(int j=0; j<words.length;j++) {
          double[] frequencies = new double[4];
          warble.warble_char_to_frequencies(cfg, words[j], new DoublePtr(frequencies, 0), new DoublePtr(frequencies, 1));
          byte parsed = warble.warble_cfg_get_parsed(cfg).getByte(j);
          warble.warble_char_to_frequencies(cfg, parsed, new DoublePtr(frequencies, 2), new DoublePtr(frequencies, 3));
          assertEquals(frequencies[0], frequencies[2], 0.1);
          assertEquals(frequencies[1], frequencies[3], 0.1);
        }
      }
    }
  }

}
