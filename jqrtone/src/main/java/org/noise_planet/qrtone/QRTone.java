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
     * Goertzel algorithm - Compute the RMS power of the selected frequencies for the provided audio signals.
     * http://asp.eurasipjournals.com/content/pdf/1687-6180-2012-56.pdf
     * ipfs://QmdAMfyq71Fm72Rt5u1qtWM7teReGAHmceAtDN5SG4Pt22
     * Sysel and Rajmic:Goertzel algorithm generalized to non-integer multiples of fundamental frequency. EURASIP Journal on Advances in Signal Processing 2012 2012:56.
     * @param signal Audio signal
     * @param sampleRate Sampling rate in Hz
     * @param freqs Array of frequency search in Hz
     * @return rms Rms power by frequencies
     */
    public static double[] generalizedGoertzel(final float[] signal, int start, int length, double sampleRate, final double[] freqs, double[] phase, boolean hannWindow) {
        assert length > 0 : "Illegal length";
        double[] outFreqsPower = new double[freqs.length];
        // Fix frequency using the sampleRate of the signal
        double samplingRateFactor = length / sampleRate;
        // Computation via second-order system
        for(int idFreq = 0; idFreq < freqs.length; idFreq++) {
            // for a single frequency :
            // precompute the constants
            double pikTerm = M2PI * (freqs[idFreq] * samplingRateFactor) / length;
            double cosPikTerm2 = Math.cos(pikTerm) * 2.0;

            Complex cc = new Complex(pikTerm, 0).exp();
            // state variables
            double s0 = 0;
            double s1 = 0.;
            double s2 = 0.;
            // 'main' loop
            // number of iterations is (by one) less than the length of signal
            for(int ind=start; ind < start + length - 1; ind++) {
                if(hannWindow) {
                    final double window = 0.5 * (1 - Math.cos((M2PI * (ind - start)) / (length - 1)));
                    s0 = signal[ind] * window + cosPikTerm2 * s1 - s2;
                } else {
                    s0 = signal[ind] + cosPikTerm2 * s1 - s2;
                }
                s2 = s1;
                s1 = s0;
            }
            // final computations
            if(!hannWindow) {
                s0 = signal[start + length - 1] + cosPikTerm2 * s1 - s2;
            }

            // complex multiplication substituting the last iteration
            // and correcting the phase for (potentially) non - integer valued
            // frequencies at the same time
            Complex parta = new Complex(s0, 0).sub(new Complex(s1, 0).mul(cc));
            Complex partb = new Complex(pikTerm * (length - 1.), 0).exp();
            Complex y = parta.mul(partb);
            outFreqsPower[idFreq] = Math.sqrt((y.r * y.r  + y.i * y.i) * 2) / length;

            if(phase != null) {
                phase[idFreq] = Math.atan2(y.i, y.r);
            }
        }
        return outFreqsPower;
    }

    public static double computeRms(float[] signal) {
        double sum = 0;
        for (double aSignal : signal) {
            sum += aSignal * aSignal;
        }
        return Math.sqrt(sum / signal.length);
    }











    public static final class Complex {
        public final double r;
        public final double i;

        public Complex(double r, double i) {
            this.r = r;
            this.i = i;
        }

        Complex add(Complex c2) {
            return new Complex(r + c2.r, i + c2.i);
        }

        Complex sub(Complex c2) {
            return new Complex(r - c2.r, i - c2.i);
        }

        Complex mul(Complex c2) {
            return new Complex(r * c2.r - i * c2.i, r * c2.i + i * c2.r);
        }

        Complex exp() {
            return new Complex(Math.cos(r), -Math.sin(r));
        }
    }
}
