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
import org.renjin.gcc.runtime.BytePtr;
import org.renjin.gcc.runtime.LongPtr;
import org.renjin.gcc.runtime.Ptr;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

public class ReedSolomonTest {


  @Test
  public void testReedSolomon() {
    int distance = 8;
    int payload = 10;
    byte[] message = new byte[payload];
    long[] next = new long[]{1};
    for(int i = 0; i < payload; i++) {
      message[i] = (byte)(warble.warble_rand(new LongPtr(next)) & 255);
    }
    byte[] words = new byte[distance + payload];
    byte[] result = new byte[message.length];

    Ptr rs = reed_solomon.correct_reed_solomon_create(
            warble.correct_rs_primitive_polynomial_ccsds, (byte)1, (byte)1, distance);

    encode.correct_reed_solomon_encode(rs, new BytePtr(message),  message.length, new BytePtr(words));

    // Add errors
    for(int i=0; i < distance / 2; i++) {
      words[warble.warble_rand(new LongPtr(next)) % words.length] = (byte)(warble.warble_rand(new LongPtr(next)) & 255);
    }


    assertNotEquals(-1, decode.correct_reed_solomon_decode(rs, new BytePtr(words), words.length, new BytePtr(result)));

    assertArrayEquals(message, Arrays.copyOfRange(result,0, payload));
  }

}
