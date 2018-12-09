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

package org.noise_planet.jwarble;

import org.jtransforms.fft.DoubleFFT_1D;
import org.jtransforms.utils.CommonUtils;

import java.util.Arrays;

public class OpenWarble {

    public static final double M2PI = Math.PI * 2;
    private long pushedSamples = 0;
    private long processedSamples = 0;
    private Configuration configuration;
    public final static int NUM_FREQUENCIES = 12;
    final double[] frequencies = new double[NUM_FREQUENCIES];
    // Frequencies not used by OpenWarble, one tone over used frequencies, this is base level to detect pitch
    final double[] frequencies_uptone = new double[NUM_FREQUENCIES];
    final int block_length;
    final int word_length;
    final int chirp_length;
    final int messageSamples;
    double[] signalCache;
    double[] convolutionCache;
    public double[] chirpFFT;
    public enum PROCESS_RESPONSE {PROCESS_IDLE, PROCESS_ERROR, PROCESS_PITCH, PROCESS_COMPLETE}
    private long triggerSampleIndexBegin = -1;
    int parsed_cursor = 0; // parsed words
    byte[] parsed;
    private UnitTestCallback unitTestCallback;

    public OpenWarble(Configuration configuration) {
        this.configuration = configuration;
        block_length = configuration.payloadSize;
        parsed = new byte[configuration.payloadSize];
        word_length = (int)(configuration.sampleRate * configuration.wordTime);
        chirp_length = word_length;
        messageSamples = chirp_length + block_length * word_length;
        signalCache = new double[chirp_length * 3];
        convolutionCache = new double[signalCache.length * 2];
        // Precompute pitch frequencies
        for(int i = 0; i < NUM_FREQUENCIES; i++) {
            if(configuration.frequencyIncrement != 0) {
                frequencies[i] = configuration.firstFrequency + (i * 2) * configuration.frequencyIncrement;
                frequencies_uptone[i] = configuration.firstFrequency + (i * 2 + 1) * configuration.frequencyIncrement;
            } else {
                frequencies[i] = configuration.firstFrequency * Math.pow(configuration.frequencyMulti, i * 2);
                frequencies_uptone[i] = configuration.firstFrequency * Math.pow(configuration.frequencyMulti, i * 2 + 1);
            }
        }
        // Precompute chirp FFT for fast convolution
        chirpFFT = new double[CommonUtils.nextPow2(chirp_length + signalCache.length - 1) * 2];
        generate_chirp(chirpFFT, 0, chirp_length, configuration.sampleRate, frequencies[0], frequencies[frequencies.length - 1], 1);
        reverse(chirpFFT, chirp_length);
        new DoubleFFT_1D(chirpFFT.length / 2).realForwardFull(chirpFFT);
    }

    public static void reverse(double[] array, int length) {
        for(int i = 0; i < length / 2; i++)
        {
            double temp = array[i];
            array[i] = array[length - i - 1];
            array[length - i - 1] = temp;
        }
    }

    public static int nextFastSize(int n)
    {
        while(true) {
            int m=n;
            while ( (m%2) == 0 ) m/=2;
            while ( (m%3) == 0 ) m/=3;
            while ( (m%5) == 0 ) m/=5;
            if (m<=1)
                break; /* n is completely factorable by twos, threes, and fives */
            n++;
        }
        return n;
    }

    public long getTriggerSampleIndexBegin() {
        return triggerSampleIndexBegin;
    }

    private MessageCallback callback = null;

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
    public static double[] generalized_goertzel(final double[] signal, int start, int length, double sampleRate,final double[] freqs) {
        double[] outFreqsPower = new double[freqs.length];
        // Fix frequency using the sampleRate of the signal
        double samplingRateFactor = length / sampleRate;
        // Computation via second-order system
        for(int id_freq = 0; id_freq < freqs.length; id_freq++) {
            // for a single frequency :
            // precompute the constants
            double pik_term = M2PI * (freqs[id_freq] * samplingRateFactor) / length;
            double cos_pik_term2 = Math.cos(pik_term) * 2;

            Complex cc = new Complex(pik_term, 0).exp();
            // state variables
            double s0;
            double s1 = 0.;
            double s2 = 0.;
            // 'main' loop
            // number of iterations is (by one) less than the length of signal
            for(int ind=start; ind < start + length - 1; ind++) {
                s0 = signal[ind] + cos_pik_term2 * s1 - s2;
                s2 = s1;
                s1 = s0;
            }
            // final computations
            s0 = signal[start + length - 1] + cos_pik_term2 * s1 - s2;

            // complex multiplication substituting the last iteration
            // and correcting the phase for (potentially) non - integer valued
            // frequencies at the same time
            Complex parta = new Complex(s0, 0).sub(new Complex(s1, 0).mul(cc));
            Complex partb = new Complex(pik_term * (length - 1.), 0).exp();
            Complex y = parta.mul(partb);
            outFreqsPower[id_freq] = Math.sqrt((y.r * y.r  + y.i * y.i) * 2) / length;

        }
        return outFreqsPower;
    }

    public static double compute_rms(double[] signal) {
        double sum = 0;
        for (double aSignal : signal) {
            sum += aSignal * aSignal;
        }
        return Math.sqrt(sum / signal.length);
    }

    public void pushSamples(double[] samples) {
        if(samples.length < signalCache.length) {
            // Move previous samples backward
            System.arraycopy(signalCache, samples.length, signalCache, 0, signalCache.length - samples.length);
            System.arraycopy(samples, 0, signalCache, signalCache.length - samples.length, samples.length);
            pushedSamples+=samples.length;
        } else {
            // Copy arrays
            System.arraycopy(samples, Math.max(0, samples.length - signalCache.length), signalCache, 0,
                    signalCache.length);
            pushedSamples+=signalCache.length;
        }
        if((triggerSampleIndexBegin < 0 && pushedSamples - processedSamples >= signalCache.length)
            ||(triggerSampleIndexBegin >= 0 && pushedSamples - processedSamples >= word_length)) {
            PROCESS_RESPONSE processResponse = PROCESS_RESPONSE.PROCESS_PITCH;
            while(processResponse == PROCESS_RESPONSE.PROCESS_PITCH || processResponse == PROCESS_RESPONSE.PROCESS_COMPLETE) {
                processResponse = process();
                switch (processResponse) {
                    case PROCESS_PITCH:
                        if (callback != null) {
                            callback.onPitch(triggerSampleIndexBegin + parsed_cursor * word_length);
                        }
                        break;
                    case PROCESS_COMPLETE:
                        if (callback != null) {
                            callback.onNewMessage(parsed, triggerSampleIndexBegin);
                        }
                        break;
                    case PROCESS_ERROR:
                        if (callback != null) {
                            callback.onError(processedSamples);
                        }
                }
            }
        }
    }

    public int getMaxPushSamplesLength() {
        if(triggerSampleIndexBegin < 0) {
            return Math.min(signalCache.length, (int) (signalCache.length - (pushedSamples - processedSamples)));
        } else {
            return Math.min(word_length, (int) (word_length - (pushedSamples - processedSamples)));
        }
    }

    private PROCESS_RESPONSE process() {
        PROCESS_RESPONSE response = PROCESS_RESPONSE.PROCESS_IDLE;
        if(triggerSampleIndexBegin < 0) {
            // Looking for trigger chirp
            int realSize = signalCache.length + chirp_length - 1;
            double[] fft = Arrays.copyOf(signalCache, chirpFFT.length);
            DoubleFFT_1D fftTool = new DoubleFFT_1D(chirpFFT.length / 2);
            fftTool.realForwardFull(fft);
            for(int i = 0; i < signalCache.length / 2; i++) {
                OpenWarble.Complex c1 = new OpenWarble.Complex(fft[i * 2], fft[i * 2 + 1]);
                OpenWarble.Complex c2 = new OpenWarble.Complex(chirpFFT[i * 2], chirpFFT[i * 2 + 1]);
                OpenWarble.Complex cc = c1.mul(c2);
                fft[i * 2] = cc.r;
                fft[i * 2 + 1] = cc.i;
            }
            fftTool.complexInverse(fft, true);
            int startIndex = (realSize - signalCache.length);
            // Move convolution cache backward
            System.arraycopy(convolutionCache, signalCache.length, convolutionCache, 0, convolutionCache.length - signalCache.length);
            int startConvolutionCache = convolutionCache.length - signalCache.length;
            for(int i=0; i < signalCache.length; i++) {
                convolutionCache[startConvolutionCache + i] = fft[startIndex + i * 2];
            }
            // Find max value
            double maxValue = Double.MIN_VALUE;
            int maxIndex = -1;
            for(int i=chirp_length / 2; i < convolutionCache.length - chirp_length; i++) {
                if(convolutionCache[i] > maxValue) {
                    maxValue = convolutionCache[i];
                    maxIndex = i;
                }
            }
            // Count all peaks near max value
            double oldWeightedAvg = 0;
            boolean increase = false;
            int peakCount = 0;
            for(int i = Math.max(0, maxIndex - chirp_length / 2); i < maxIndex + chirp_length / 2; i++) {
                double weightedAvg = convolutionCache[i];
                double value = weightedAvg - oldWeightedAvg;
                if(convolutionCache[i] > 0 && ((value > 0  && !increase) || (value < 0 && increase))) {
                    if(convolutionCache[i - 1] > maxValue * configuration.convolutionPeakRatio) {
                        peakCount++;
                    }
                }
                increase = value > 0;
                oldWeightedAvg = weightedAvg;
            }
            if(peakCount == 1) {
                triggerSampleIndexBegin = maxIndex - chirp_length / 2 + (pushedSamples - convolutionCache.length);
                response = PROCESS_RESPONSE.PROCESS_PITCH;
            }
            if(unitTestCallback != null) {
                unitTestCallback.onConvolution(convolutionCache);
            }
        } else {
            // Target pitch contain the pitch peak ( 0.25 to start from hanning filter lobe)
            // Compute absolute position

            long targetPitch = triggerSampleIndexBegin + chirp_length + parsed_cursor * word_length;
            if(targetPitch >= pushedSamples - signalCache.length && targetPitch + word_length <= pushedSamples) {
                double[] levelsUp = generalized_goertzel(signalCache, (int)(targetPitch - (pushedSamples - signalCache.length)),word_length, configuration.sampleRate, frequencies);
                double[] levelsDown = generalized_goertzel(signalCache, (int)(targetPitch - (pushedSamples - signalCache.length)),word_length, configuration.sampleRate, frequencies_uptone);

                int word = 0;
                for(int i = 0; i < frequencies.length; i++) {
                    double snr = 10 * Math.log10(levelsUp[i] / levelsDown[i]);
                    if(snr >= configuration.triggerSnr) {
                        // This bit is 1
                        word |= 1 << i;
                    }
                }
                Hamming12_8.CorrectResult result = Hamming12_8.decode(word);
                if(unitTestCallback != null) {
                    boolean[] freqs = new boolean[frequencies.length];
                    for(int i = 0; i < frequencies.length; i++) {
                        double snr = 10 * Math.log10(levelsUp[i] / levelsDown[i]);
                        freqs[i] = snr >= configuration.triggerSnr;
                    }
                    unitTestCallback.detectWord(result.value, word, freqs);
                }
                if(result.result == Hamming12_8.CorrectResultCode.FAIL_CORRECTION) {
                    response = PROCESS_RESPONSE.PROCESS_ERROR;
                    triggerSampleIndexBegin = -1;
                } else {
                    parsed[parsed_cursor] = result.value;
                    parsed_cursor++;
                    if(parsed_cursor == parsed.length) {
                        response = PROCESS_RESPONSE.PROCESS_COMPLETE;
                        triggerSampleIndexBegin = -1;
                    } else {
                        response = PROCESS_RESPONSE.PROCESS_PITCH;
                    }
                }
            }
        }
        processedSamples = pushedSamples;
        return response;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public int getWord_length() {
        return word_length;
    }

    public int getChirp_length() {
        return chirp_length;
    }

    public static void generate_pitch(double[] signal_out, final int location, final int length, double sample_rate, double frequency, double power_peak) {
        double t_step = 1 / sample_rate;
        for(int i=location; i < location + length; i++) {
		    final double window = 0.5 * (1 - Math.cos((M2PI * (i - location)) / (length - 1)));
            signal_out[i] += Math.sin(i * t_step * M2PI * frequency) * power_peak * window;
        }
    }

    public static void generate_chirp(double[] signal_out, final int location,final int length, double sample_rate, double frequencyStart, double frequencyEnd, double power_peak) {
        double f1_div_f0 = frequencyEnd / frequencyStart;
        double chirp_time = length / sample_rate;
        for (int i = location; i < location + length; i++) {
            // Generate log chirp into cache
            // low frequency to high frequency
            final double window = 0.5 * (1 - Math.cos((M2PI * (i - location)) / (length - 1)));
            signal_out[i] += window * Math.sin(M2PI * chirp_time / Math.log(f1_div_f0) * frequencyStart * (Math.pow(f1_div_f0, ((double) (i - location) / sample_rate) / chirp_time) - 1.0));
        }
    }

    public double[] generate_signal(double powerPeak, byte[] words) {
        double[] signal = new double[messageSamples];
        int location = 0;
        generate_chirp(signal, 0, chirp_length, configuration.sampleRate, configuration.firstFrequency, frequencies[frequencies.length - 1], powerPeak);
        location += chirp_length;
        for(int idword = 0; idword < block_length; idword++) {
            int code = Hamming12_8.encode(words[idword]);
            for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                if((code & (1 << idfreq)) != 0) {
                    generate_pitch(signal, location, word_length ,configuration.sampleRate, frequencies[idfreq], powerPeak);
                }
            }
            if(unitTestCallback != null) {
                boolean[] freqs = new boolean[frequencies.length];
                for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                    freqs[idfreq] = (code & (1 << idfreq)) != 0;
                }
                unitTestCallback.generateWord(words[idword], code, freqs);
            }
            location+=word_length;
        }
        return signal;
    }

    public MessageCallback getCallback() {
        return callback;
    }

    public void setCallback(MessageCallback callback) {
        this.callback = callback;
    }

    public void setUnitTestCallback(UnitTestCallback unitTestCallback) {
        this.unitTestCallback = unitTestCallback;
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

    public interface UnitTestCallback {
        void onConvolution(double[] convolutionResult);
        void generateWord(byte word, int encodedWord, boolean[] frequencies);
        void detectWord(byte word, int encodedWord, boolean[] frequencies);
    }
}
