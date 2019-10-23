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

import java.util.Arrays;

public class OpenWarble {
    private static final int BACKGROUND_LVL_SIZE = 32;
    public static final double M2PI = Math.PI * 2;
    private long pushedSamples = 0;
    private long processedSamples = 0;
    private int correctedErrors = 0;
    private Percentile backgroundLevel;
    private Configuration configuration;
    public final static int NUM_FREQUENCIES = 12;
    public final static int WINDOW_OFFSET_DENOMINATOR = 4;
    final int frequency_door1;
    public final static byte door2_check = 'W';
    final double[] frequencies = new double[NUM_FREQUENCIES];
    // Frequencies one tone over used frequencies on pair words
    final double[] frequencies_uptone = new double[NUM_FREQUENCIES];
    final int block_length;
    final int word_length;
    final int silence_length;
    final int door_length;
    final int messageSamples;
    final int windowOffsetLength;
    double[] signalCache;
    double[] rmsGateHistory;
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
        silence_length = (int)(configuration.sampleRate * configuration.wordSilence);
        windowOffsetLength = (int)Math.ceil(word_length / (double)WINDOW_OFFSET_DENOMINATOR);
        door_length = word_length;
        messageSamples = door_length + silence_length + door_length + block_length * (silence_length + word_length);
        signalCache = new double[door_length * 3];
        rmsGateHistory = new double[signalCache.length / windowOffsetLength];
        backgroundLevel = new Percentile(BACKGROUND_LVL_SIZE);
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
        frequency_door1 = NUM_FREQUENCIES - 1;
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
        if((triggerSampleIndexBegin < 0 && pushedSamples - processedSamples >= windowOffsetLength)
            ||(triggerSampleIndexBegin >= 0 && pushedSamples - processedSamples >= word_length)) {
            PROCESS_RESPONSE processResponse = PROCESS_RESPONSE.PROCESS_PITCH;
            while(processResponse == PROCESS_RESPONSE.PROCESS_PITCH || processResponse == PROCESS_RESPONSE.PROCESS_COMPLETE) {
                processResponse = process();
                switch (processResponse) {
                    case PROCESS_PITCH:
                        if (callback != null && parsed_cursor > 0) {
                            callback.onPitch(triggerSampleIndexBegin);
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

    public int getCorrectedErrors() {
        return correctedErrors;
    }

    private double getSnr(double level) {
        return 10 * Math.log10(level / backgroundLevel.getPercentile(0.1));
    }

    /**
     * @param targetPitch Sample index
     */
    private Hamming12_8.CorrectResult decode(long targetPitch, Byte expected, double[] score, boolean trace) {
        double[] levelsUp = generalized_goertzel(signalCache, (int) (targetPitch - (pushedSamples - signalCache.length)), word_length / 2, configuration.sampleRate, (parsed_cursor + 1) % 2 == 0 ? frequencies : frequencies_uptone);
        double[] levelsDown = generalized_goertzel(signalCache, (int) (targetPitch - (pushedSamples - signalCache.length)) + word_length / 2, word_length / 2, configuration.sampleRate, (parsed_cursor + 1) % 2 == 0 ? frequencies : frequencies_uptone);

        int word = 0;
        boolean[] freqs = null;
        int code = 0;
        if(trace) {
            freqs = new boolean[frequencies.length];
        }
        if(expected != null) {
            code = Hamming12_8.encode(expected);
        }
        for (int i = 0; i < frequencies.length; i++) {
            if (levelsUp[i] > levelsDown[i]) {
                // This bit is 1
                word |= 1 << i;
                if(trace) {
                    freqs[i] = true;
                }
                if(score != null) {
                    double snr = 10 * Math.log10(levelsUp[i] / levelsDown[i]);
                    if((code & (1 << i)) != 0) {
                        score[i] = snr;
                    } else {
                        score[i] = -snr;
                    }
                }
            } else if(score != null) {
                double snr = 10 * Math.log10(levelsDown[i] / levelsUp[i]);
                if((code & (1 << i)) == 0) {
                    score[i] = snr;
                } else {
                    score[i] = -snr;
                }
            }
        }
        Hamming12_8.CorrectResult result = Hamming12_8.decode(word);

        if (trace) {
            unitTestCallback.detectWord(result, word, freqs);
        }

        return result;
    }

    private PROCESS_RESPONSE process() {
        PROCESS_RESPONSE response = PROCESS_RESPONSE.PROCESS_IDLE;
        if(triggerSampleIndexBegin < 0) {
            // Looking for trigger chirp
            long cursor = signalCache.length - pushedSamples + processedSamples;
            if(cursor < signalCache.length - door_length) {
                while (cursor < signalCache.length - door_length) {
                    final double[] doorFrequencies = new double[]{frequencies[frequency_door1]};
                    double[] levels = generalized_goertzel(signalCache, (int) cursor + door_length / 2 - windowOffsetLength, windowOffsetLength, configuration.sampleRate, doorFrequencies);
                    levels[0] = Math.max(levels[0], 1e-12);
                    backgroundLevel.add(levels[0]);
                    System.arraycopy(rmsGateHistory, 1, rmsGateHistory, 0, rmsGateHistory.length - 1);
                    rmsGateHistory[rmsGateHistory.length - 1] = levels[0];
                    cursor += windowOffsetLength;
                    processedSamples += windowOffsetLength;
                }
                // Find max value
                double maxValue = Double.MIN_VALUE;
                boolean negative = false;
                int maxIndex = -1;
                for (int i = 0; i < rmsGateHistory.length; i++) {
                    if (Math.abs(rmsGateHistory[i]) > maxValue) {
                        maxValue = Math.abs(rmsGateHistory[i]);
                        negative = rmsGateHistory[i] < 0;
                        maxIndex = i;
                    }
                }
                // Count all peaks near max value
                double oldSnr = rmsGateHistory[Math.max(0, maxIndex - WINDOW_OFFSET_DENOMINATOR / 2 - 1)];
                boolean increase = rmsGateHistory[Math.max(0, maxIndex - WINDOW_OFFSET_DENOMINATOR / 2)] > oldSnr;
                int peakCount = 0;
                for (int i = Math.max(0, maxIndex - WINDOW_OFFSET_DENOMINATOR / 2); i < Math.min(rmsGateHistory.length, maxIndex + WINDOW_OFFSET_DENOMINATOR / 2); i++) {
                    double snr = rmsGateHistory[i];
                    double value = snr - oldSnr;
                    if (((value > 0 && !increase) || (value < 0 && increase))) {
                        boolean signalNegative = rmsGateHistory[i - 1] < 0;
                        if (((negative && signalNegative) || (!negative && !signalNegative)) && Math.abs(rmsGateHistory[i - 1]) > maxValue * configuration.convolutionPeakRatio) {
                            peakCount++;
                        }
                    }
                    increase = value > 0;
                    oldSnr = snr;
                }
                if (peakCount == 1 && maxIndex < rmsGateHistory.length - (door_length / windowOffsetLength) && getSnr(maxValue) > configuration.triggerSnr) {
                    triggerSampleIndexBegin = processedSamples - (rmsGateHistory.length - maxIndex) * windowOffsetLength;
                    response = PROCESS_RESPONSE.PROCESS_PITCH;
                    correctedErrors = 0;
                    parsed_cursor = 0;
                    System.out.println(String.format("TRIGGER %.3f-%.3f s %.3f snr", triggerSampleIndexBegin / configuration.sampleRate,(triggerSampleIndexBegin+door_length) / configuration.sampleRate, getSnr(maxValue)));
                }
            }
        } else {
            // Target pitch contain the pitch peak ( 0.25 to start from hanning filter lobe)
            // Compute absolute position
            long targetPitch = triggerSampleIndexBegin + silence_length + door_length + parsed_cursor * (word_length + silence_length);
            if(targetPitch < pushedSamples - signalCache.length) {
                // Missed first pitch
                response = PROCESS_RESPONSE.PROCESS_ERROR;
                triggerSampleIndexBegin = -1;
            } else if(targetPitch + word_length + word_length / 2 <= pushedSamples) {
                if (parsed_cursor == 0) {
                    // Validation gate - the expected word is known
                    // Find the most appropriate sample index by computing the sample offset score
                    // This is the most cpu intensive task, but triggered only when a message is found
                    Hamming12_8.CorrectResult result = null;
                    double[] score = new double[frequencies.length];
                    double bestScore = Double.MIN_VALUE;
                    int bestScoreOffset = 0;
                    int step = (int)Math.ceil((1.0 / frequencies[0]) * configuration.sampleRate);
                    for(int offset = -door_length / 2; offset < (door_length / 2) - step; offset += step) {
                        Hamming12_8.CorrectResult offsetResult = decode(targetPitch + offset, door2_check, score, false);
                        if(offsetResult.value == door2_check) {
                            Arrays.sort(score);
                            double median = score.length % 2 == 0 ? (score[(score.length - 1) / 2] + score[(score.length - 1) / 2 + 1]) / 2.0 : score[score.length / 2];
                            if(bestScore < median) {
                                bestScore = median;
                                bestScoreOffset = offset;
                                result = offsetResult;
                            }
                        }
                    }
                    triggerSampleIndexBegin = triggerSampleIndexBegin + bestScoreOffset;
                    if (result == null || result.result == Hamming12_8.CorrectResultCode.FAIL_CORRECTION ||
                            door2_check != result.value) {
                        response = PROCESS_RESPONSE.PROCESS_ERROR;
                        triggerSampleIndexBegin = -1;
                    } else {
                        response = PROCESS_RESPONSE.PROCESS_PITCH;
                        parsed_cursor++;
                    }
                } else {
                    Hamming12_8.CorrectResult result = decode(targetPitch, null, null, unitTestCallback != null);
                    if (result.result == Hamming12_8.CorrectResultCode.FAIL_CORRECTION) {
                        response = PROCESS_RESPONSE.PROCESS_ERROR;
                        triggerSampleIndexBegin = -1;
                    } else {
                        if (result.result == Hamming12_8.CorrectResultCode.CORRECTED_ERROR) {
                            correctedErrors += 1;
                        }
                        // message
                        parsed[parsed_cursor - 1] = result.value;
                        parsed_cursor++;
                        if (parsed_cursor - 1 == parsed.length) {
                            response = PROCESS_RESPONSE.PROCESS_COMPLETE;
                            triggerSampleIndexBegin = -1;
                        } else {
                            response = PROCESS_RESPONSE.PROCESS_PITCH;
                        }
                    }
                }
            }
            processedSamples = pushedSamples;
        }
        return response;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public int getWord_length() {
        return word_length;
    }

    public int getDoor_length() {
        return door_length;
    }

    public static void generate_pitch(double[] signal_out, final int location, final int length, double sample_rate, double frequency, double power_peak) {
        double t_step = 1 / sample_rate;
        for(int i=location; i < location + length; i++) {
            // Apply Hamming window
		    final double window = 0.5 * (1 - Math.cos((M2PI * (i - location)) / (length - 1)));
            signal_out[i] += Math.sin(i * t_step * M2PI * frequency) * power_peak * window;
        }
    }

    public double[] generate_signal(double powerPeak, byte[] words) {
        double[] signal = new double[messageSamples];
        int location = 0;
        // Pure tone trigger signal
        generate_pitch(signal, location, door_length ,configuration.sampleRate, frequencies[frequency_door1], powerPeak);
        location += door_length + silence_length;
        // Check special word
        int code = Hamming12_8.encode(door2_check);
        for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
            if((code & (1 << idfreq)) != 0) {
                generate_pitch(signal, location, door_length / 2 ,configuration.sampleRate, frequencies_uptone[idfreq], powerPeak / frequencies.length);
            } else {
                generate_pitch(signal, location + door_length / 2, door_length / 2 ,configuration.sampleRate, frequencies_uptone[idfreq], powerPeak / frequencies.length);
            }
        }
        if(unitTestCallback != null) {
            boolean[] freqs = new boolean[frequencies.length];
            for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                freqs[idfreq] = (code & (1 << idfreq)) != 0;
            }
            unitTestCallback.generateWord(door2_check, code, freqs);
        }
        location += door_length;
        // Message
        for(int idword = 0; idword < block_length; idword++) {
            location += silence_length;
            code = Hamming12_8.encode(words[idword]);
            for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                if((code & (1 << idfreq)) != 0) {
                    generate_pitch(signal, location, word_length / 2 ,configuration.sampleRate, idword % 2 == 0 ? frequencies[idfreq] : frequencies_uptone[idfreq], powerPeak / frequencies.length);
                } else {
                    generate_pitch(signal, location + word_length / 2, word_length / 2 ,configuration.sampleRate, idword % 2 == 0 ? frequencies[idfreq] : frequencies_uptone[idfreq], powerPeak / frequencies.length);
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
        void generateWord(byte word, int encodedWord, boolean[] frequencies);
        void detectWord(Hamming12_8.CorrectResult result, int encodedWord, boolean[] frequencies);
    }
}
