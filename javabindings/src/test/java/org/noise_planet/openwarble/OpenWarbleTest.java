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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class OpenWarbleTest {

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
    // Receive Ipfs address
    // Python code:
    // import base58
    // import struct
    // s = struct.Struct('b').unpack
    // payload = map((lambda v : s(v)[0], base58.b58decode("QmXjkFQjnD8i8ntmwehoAHBfJEApETx8ebScyVzAHqgjpD"))
    byte expected_payload[] = { 18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36,
            -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114 };

    double samplingRate = 44100;
    InputStream inputStream = OpenWarbleTest.class.getResourceAsStream("audioTest_44100_16bitsPCM_0.0872s_1760.raw");
    short[] signal_short = loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
    OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expected_payload.length, samplingRate));
    // Convert into double
    double[] buffer;
    int window = 4096;
    final List<byte[]> payloads = new ArrayList<>();
    final AtomicBoolean gotError =  new AtomicBoolean(false);

    // Set callback to collect message
    openWarble.setCallback(new MessageCallback() {
      @Override
      public void onNewMessage(byte[] payload, long sampleId) {
        payloads.add(payload);
      }

      @Override
      public void onPitch(long sampleId) {

      }

      @Override
      public void onError(long sampleId) {
        gotError.set(true);
      }
    });

    // Push audio samples to OpenWarble
    for(int i=0;i< signal_short.length;i+=window) {
      buffer = new double[Math.min(signal_short.length-i, window)];
      for(int j=0; j<buffer.length; j++) {
        buffer[j] = signal_short[i+j];
      }
      openWarble.pushSamples(buffer);
      assert(!gotError.get());
    }

    // Check result
    assertEquals(1, payloads.size());
    assertArrayEquals(expected_payload, payloads.get(0));
    assertEquals(signal_short.length - signal_short.length % openWarble.getDefaultWindowLength(), openWarble.getPushedSamples());
  }

}
