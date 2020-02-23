/*
 * BSD 3-Clause License
 *
 * Copyright (c) Ifsttar
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

package org.noise_planet.qrtone;

import java.util.concurrent.atomic.AtomicLong;

public class QRTone {
    public static final double M2PI = Math.PI * 2;
    private Configuration configuration;
    // DTMF 16*16 frequencies
    public final static int NUM_FREQUENCIES = 32;
    final double[] frequencies;

    public QRTone(Configuration configuration) {
        this.configuration = configuration;
        this.frequencies = configuration.computeFrequencies(NUM_FREQUENCIES);
    }

    /**
     * Checksum of bytes (could be used only up to 64 bytes)
     * @param payload payload to crc
     * @param from payload index to begin crc
     * @param to excluded index to end crc
     * @return crc value
     */
    public static byte crc8(byte[] payload, int from, int to) {
        int crc8 = 0;
        for (int i=from; i < to; i++) {
            int crc = 0;
            int accumulator = (crc8 ^ payload[i]) & 0x0FF;
            for (int j = 0; j < 8; j++) {
                if (((accumulator ^ crc) & 0x01) == 0x01) {
                    crc = ((crc ^ 0x18) >> 1) | 0x80;
                } else {
                    crc = crc >> 1;
                }
                accumulator = accumulator >> 1;
            }
            crc8 = (byte) crc;
        }
        return (byte) (crc8 & 0x0FF);
    }


    /**
     * randomly swap specified integer
     * @param n
     * @param index
     */
    public static void fisherYatesShuffleIndex(int n, int[] index) {
        int i;
        AtomicLong rndCache = new AtomicLong(n);
        for (i = index.length - 1; i > 0; i--) {
            index[index.length - 1 - i] = warbleRand(rndCache) % (i + 1);
        }
    }

    public static void swapChars(byte[] inputString, int[] index) {
        int i;
        for (i = inputString.length - 1; i > 0; i--)
        {
            int v = index[inputString.length - 1 - i];
            byte tmp = inputString[i];
            inputString[i] = inputString[v];
            inputString[v] = tmp;
        }
    }

    /**
     * Pseudo random generator
     * @param next Seed
     * @return pseudo-random value
     */
    public static int warbleRand(AtomicLong next) {
        next.set(next.get() * 1103515245L + 12345L);
        return (int)(((next.get() / 65536) & 0xFFFF  % 32768));
    }

    /**
     * Apply hamming window on provided signal
     * @param signal Signal to update
     * @param windowLength Hamming window length
     * @param offset If the signal length is inferior than windowLength, give the offset of the hamming window
     */
    public static void applyHamming(float[] signal, int windowLength, int offset) {
        for (int i = 0; i < signal.length; i++) {
            signal[i] *= (float) ((0.5 - 0.5 * Math.cos((M2PI * (i + offset)) / (windowLength - 1))));
        }
    }

    /**
     * Apply tukey window on specified array
     * @param signal Audio samples
     * @param alpha Tukay alpha (0-1)
     * @param windowLength full length of tukey window
     * @param offset Offset of the provided signal buffer (> 0)
     */
    public static void applyTukey(float[] signal, double alpha, int windowLength, int offset) {
        int index_begin_flat = (int)(Math.floor(alpha * (windowLength - 1) / 2.0));
        int index_end_flat = windowLength - index_begin_flat;
        double window_value = 0;
        for(int i=offset; i < offset + index_begin_flat + 1 && i - offset < signal.length; i++) {
            window_value = 0.5 * (1 + Math.cos(Math.PI * (-1 + 2.0*i/alpha/(windowLength-1))));
            signal[i - offset] = (float)(signal[i - offset] * window_value);
        }
        // End Hann part
        for(int i=offset + index_end_flat - 1; i < offset + windowLength && i - offset < signal.length; i++) {
            window_value =0.5 * (1 +  Math.cos(Math.PI * (-2.0/alpha + 1 + 2.0*i/alpha/(windowLength-1))));
            signal[i - offset] = (float)(signal[i - offset] * window_value);
        }
    }

    public static void generatePitch(double[] signal_out, final int offset, double sample_rate, double frequency, double powerPeak) {
        double tStep = 1 / sample_rate;
        for(int i=0; i < signal_out.length; i++) {
            signal_out[i] += Math.sin((i + offset) * tStep * M2PI * frequency) * powerPeak;
        }
    }

    public static double computeRms(float[] signal) {
        double sum = 0;
        for (double aSignal : signal) {
            sum += aSignal * aSignal;
        }
        return Math.sqrt(sum / signal.length);
    }

}
