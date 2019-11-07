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

import com.backblaze.erasure.ReedSolomon;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main class for data transfer over audio
 * @author Nicolas Fortin (UMRAE - UGE)
 */
public class OpenWarble {
    private static final int BACKGROUND_LVL_SIZE = 32;
    // WARBLE_RS_DISTANCE is the number of maximum fixed bytes for WARBLE_RS_P bytes - 1
    public static final int WARBLE_RS_P = 10;
    public static final int WARBLE_RS_DISTANCE = 2;
    public final static int NUM_FREQUENCIES = 12;
    public final static int WINDOW_OFFSET_DENOMINATOR = 5;
    public static final double M2PI = Math.PI * 2;
    private long pushedSamples = 0;
    private long processedSamples = 0;
    private int hammingCorrectedErrors = 0;
    private ReedSolomonResult lastReedSolomonResult = null;
    private Percentile backgroundLevel;
    // The noise peak of the gate tone must only be located to the frequency of the gate tone
    private Percentile backgroundLevelUpTone;
    private Configuration configuration;
    final int frequencyDoor1;
    public final static byte door2Check = 'W';
    final double[] frequencies = new double[NUM_FREQUENCIES];
    // Frequencies one tone over used frequencies on pair words
    final double[] frequenciesUptone = new double[NUM_FREQUENCIES];
    final int blockLength; // Full payload + all parity bytes
    final int shardSize; // Number of Reed Solomon parts
    final int wordLength;
    final int silenceLength;
    final int doorLength;
    final int messageSamples;
    final int windowOffsetLength;
    double[] signalCache;
    double[] rmsGateHistory;
    double[] rmsGateUpToneHistory;
    public enum PROCESS_RESPONSE {PROCESS_IDLE, PROCESS_ERROR, PROCESS_PITCH, PROCESS_COMPLETE}
    private long triggerSampleIndexBegin = -1;
    int parsedCursor = 0; // parsed words
    byte[] parsed;
    private UnitTestCallback unitTestCallback;
    int[] shuffleIndex;

    public OpenWarble(Configuration configuration) {
        this.configuration = configuration;
        // Reed Solomon initialization
        if(configuration.reedSolomonEncode) {
            shardSize = Math.max(1, (int)Math.ceil(configuration.payloadSize / (float)(WARBLE_RS_P - 1)));
            // Compute total bytes to send
            // payload + parity + crc
            blockLength = configuration.payloadSize + WARBLE_RS_DISTANCE * shardSize + shardSize;
            // Compute index shuffling of bytes
            shuffleIndex = new int[blockLength];
            for(int i = 0; i < blockLength; i++) {
                shuffleIndex[i] = i;
            }
            fisherYatesShuffleIndex(blockLength, shuffleIndex);
        } else {
            blockLength = configuration.payloadSize;
            shardSize = configuration.payloadSize;
        }
        parsed = new byte[blockLength];
        wordLength = (int)(configuration.sampleRate * configuration.wordTime);
        silenceLength = (int)(configuration.sampleRate * configuration.wordSilence);
        windowOffsetLength = (int)Math.ceil(wordLength / (double)WINDOW_OFFSET_DENOMINATOR);
        doorLength = wordLength;
        messageSamples = doorLength + silenceLength + doorLength + blockLength * (silenceLength + wordLength);
        signalCache = new double[doorLength * 3];
        rmsGateHistory = new double[signalCache.length / windowOffsetLength];
        rmsGateUpToneHistory = new double[rmsGateHistory.length];
        backgroundLevel = new Percentile(BACKGROUND_LVL_SIZE);
        backgroundLevelUpTone = new Percentile(BACKGROUND_LVL_SIZE);
        // Precompute pitch frequencies
        for(int i = 0; i < NUM_FREQUENCIES; i++) {
            if(configuration.frequencyIncrement != 0) {
                frequencies[i] = configuration.firstFrequency + (i * 2) * configuration.frequencyIncrement;
                frequenciesUptone[i] = configuration.firstFrequency + (i * 2 + 1) * configuration.frequencyIncrement;
            } else {
                frequencies[i] = configuration.firstFrequency * Math.pow(configuration.frequencyMulti, i * 2);
                frequenciesUptone[i] = configuration.firstFrequency * Math.pow(configuration.frequencyMulti, i * 2 + 1);
            }
        }
        frequencyDoor1 = NUM_FREQUENCIES - 1;
    }

    public long getTriggerSampleIndexBegin() {
        return triggerSampleIndexBegin;
    }

    public ReedSolomonResult getLastReedSolomonResult() {
        return lastReedSolomonResult;
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
    public static double[] generalizedGoertzel(final double[] signal, int start, int length, double sampleRate, final double[] freqs) {
        double[] outFreqsPower = new double[freqs.length];
        // Fix frequency using the sampleRate of the signal
        double samplingRateFactor = length / sampleRate;
        // Computation via second-order system
        for(int idFreq = 0; idFreq < freqs.length; idFreq++) {
            // for a single frequency :
            // precompute the constants
            double pikTerm = M2PI * (freqs[idFreq] * samplingRateFactor) / length;
            double cosPikTerm2 = Math.cos(pikTerm) * 2;

            Complex cc = new Complex(pikTerm, 0).exp();
            // state variables
            double s0;
            double s1 = 0.;
            double s2 = 0.;
            // 'main' loop
            // number of iterations is (by one) less than the length of signal
            for(int ind=start; ind < start + length - 1; ind++) {
                s0 = signal[ind] + cosPikTerm2 * s1 - s2;
                s2 = s1;
                s1 = s0;
            }
            // final computations
            s0 = signal[start + length - 1] + cosPikTerm2 * s1 - s2;

            // complex multiplication substituting the last iteration
            // and correcting the phase for (potentially) non - integer valued
            // frequencies at the same time
            Complex parta = new Complex(s0, 0).sub(new Complex(s1, 0).mul(cc));
            Complex partb = new Complex(pikTerm * (length - 1.), 0).exp();
            Complex y = parta.mul(partb);
            outFreqsPower[idFreq] = Math.sqrt((y.r * y.r  + y.i * y.i) * 2) / length;

        }
        return outFreqsPower;
    }

    public static double computeRms(double[] signal) {
        double sum = 0;
        for (double aSignal : signal) {
            sum += aSignal * aSignal;
        }
        return Math.sqrt(sum / signal.length);
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

    public void pushSamples(double[] samples) {
        if(samples.length > getMaxPushSamplesLength()) {
            throw new IllegalArgumentException("Provided sample array length is greater than getMaxPushSamplesLength()");
        }
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
            ||(triggerSampleIndexBegin >= 0 && pushedSamples - processedSamples >= wordLength)) {
            PROCESS_RESPONSE processResponse = PROCESS_RESPONSE.PROCESS_PITCH;
            while(processResponse == PROCESS_RESPONSE.PROCESS_PITCH || processResponse == PROCESS_RESPONSE.PROCESS_COMPLETE) {
                processResponse = process();
                switch (processResponse) {
                    case PROCESS_PITCH:
                        if (callback != null && parsedCursor > 0) {
                            callback.onPitch(triggerSampleIndexBegin);
                        }
                        break;
                    case PROCESS_COMPLETE:
                        if (callback != null) {
                            if(configuration.reedSolomonEncode) {
                                unswapChars(parsed, shuffleIndex);
                                lastReedSolomonResult = decodeReedSolomon(parsed);
                                if(lastReedSolomonResult.code != ReedSolomonResultCode.FAIL_CORRECTION) {
                                    callback.onNewMessage(lastReedSolomonResult.payload, triggerSampleIndexBegin);
                                }
                            } else {
                                callback.onNewMessage(parsed, triggerSampleIndexBegin);
                            }
                        }
                        triggerSampleIndexBegin = -1;
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
            return Math.min(wordLength, (int) (wordLength - (pushedSamples - processedSamples)));
        }
    }

    public int getHammingCorrectedErrors() {
        return hammingCorrectedErrors;
    }

    private static double getSnr(double level, Percentile background) {
        return 10 * Math.log10(level / background.getPercentile(0.1));
    }

    /**
     * @param targetPitch Sample index
     */
    private Hamming12_8.CorrectResult decode(long targetPitch, Byte expected, double[] score, boolean trace) {
        // Bits are coded using
        int startOne = Math.max(0, (int) (targetPitch - (pushedSamples - signalCache.length)));
        int startZero = startOne + wordLength / 2;

        double[] levelsUp = generalizedGoertzel(signalCache, startOne, Math.min(signalCache.length - startOne, wordLength / 2), configuration.sampleRate, (parsedCursor + 1) % 2 == 0 ? frequencies : frequenciesUptone);
        double[] levelsDown = generalizedGoertzel(signalCache, startZero,  Math.min(signalCache.length - startZero, wordLength / 2), configuration.sampleRate, (parsedCursor + 1) % 2 == 0 ? frequencies : frequenciesUptone);

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
            if(cursor < signalCache.length - doorLength) {
                while (cursor < signalCache.length - doorLength) {
                    final double[] doorFrequencies = new double[]{frequencies[frequencyDoor1], frequenciesUptone[frequencyDoor1]};
                    double[] levels = generalizedGoertzel(signalCache, (int) cursor + doorLength / 4, doorLength / 2, configuration.sampleRate, doorFrequencies);
                    levels[0] = Math.max(levels[0], 1e-12);
                    levels[1] = Math.max(levels[1], 1e-12);
                    backgroundLevel.add(levels[0]);
                    backgroundLevelUpTone.add(levels[1]);
                    System.arraycopy(rmsGateHistory, 1, rmsGateHistory, 0, rmsGateHistory.length - 1);
                    System.arraycopy(rmsGateUpToneHistory, 1, rmsGateUpToneHistory, 0, rmsGateUpToneHistory.length - 1);
                    rmsGateHistory[rmsGateHistory.length - 1] = levels[0];
                    rmsGateUpToneHistory[rmsGateHistory.length - 1] = levels[1];
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
                if (peakCount == 1 && getSnr(maxValue, backgroundLevel) > configuration.triggerSnr && getSnr(rmsGateUpToneHistory[maxIndex], backgroundLevelUpTone) < configuration.triggerSnr) {
                    triggerSampleIndexBegin = processedSamples - (rmsGateHistory.length - maxIndex) * windowOffsetLength;
                    response = PROCESS_RESPONSE.PROCESS_PITCH;
                    hammingCorrectedErrors = 0;
                    parsedCursor = 0;
                }
            }
        } else {
            // Target pitch contain the pitch peak ( 0.25 to start from hanning filter lobe)
            // Compute absolute position
            long targetPitch = triggerSampleIndexBegin + silenceLength + doorLength + parsedCursor * (wordLength + silenceLength);
            if(targetPitch < pushedSamples - signalCache.length) {
                // Missed first pitch
                response = PROCESS_RESPONSE.PROCESS_ERROR;
                triggerSampleIndexBegin = -1;
            } else if(targetPitch + wordLength + wordLength / 2 <= pushedSamples) {
                if (parsedCursor == 0) {
                    // Validation gate - the expected word is known
                    // Find the most appropriate sample index by computing the sample offset score
                    // This is the most cpu intensive task, but triggered only when a message is found
                    Hamming12_8.CorrectResult result = decode(targetPitch, door2Check, null, false);
                    if (result.result == Hamming12_8.CorrectResultCode.FAIL_CORRECTION ||
                            door2Check != result.value) {
                        response = PROCESS_RESPONSE.PROCESS_IDLE;
                        triggerSampleIndexBegin = -1;
                    } else {
                        response = PROCESS_RESPONSE.PROCESS_PITCH;
                        parsedCursor++;
                    }
                } else {
                    Hamming12_8.CorrectResult result = decode(targetPitch, null, null, unitTestCallback != null);
                    if (!configuration.reedSolomonEncode && result.result == Hamming12_8.CorrectResultCode.FAIL_CORRECTION) {
                        // Failed to decode byte
                        // Reed Solomon is disabled so the entire message is lost
                        response = PROCESS_RESPONSE.PROCESS_ERROR;
                        triggerSampleIndexBegin = -1;
                    } else {
                        if (result.result == Hamming12_8.CorrectResultCode.CORRECTED_ERROR) {
                            hammingCorrectedErrors += 1;
                        }
                        // message
                        parsed[parsedCursor - 1] = result.value;
                        parsedCursor++;
                        if (parsedCursor - 1 == parsed.length) {
                            response = PROCESS_RESPONSE.PROCESS_COMPLETE;
                        } else {
                            response = PROCESS_RESPONSE.PROCESS_PITCH;
                        }
                    }
                }
                processedSamples = Math.max(pushedSamples - signalCache.length, Math.min(pushedSamples, targetPitch + wordLength));
            } else {
                processedSamples = pushedSamples;
            }
        }
        return response;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public int getWordLength() {
        return wordLength;
    }

    public int getDoorLength() {
        return doorLength;
    }

    public static void generatePitch(double[] signal_out, final int location, final int length, double sample_rate, double frequency, double powerPeak) {
        double tStep = 1 / sample_rate;
        for(int i=location; i < location + length; i++) {
            // Apply Hamming window
		    final double window = 0.5 * (1 - Math.cos((M2PI * (i - location)) / (length - 1)));
            signal_out[i] += Math.sin(i * tStep * M2PI * frequency) * powerPeak * window;
        }
    }

    public double[] generateSignal(double powerPeak, byte[] words) {
        if(configuration.reedSolomonEncode) {
            words = encodeReedSolomon(words);
            swapChars(words, shuffleIndex);
        }
        double[] signal = new double[messageSamples];
        int location = 0;
        // Pure tone trigger signal
        generatePitch(signal, location, doorLength,configuration.sampleRate, frequencies[frequencyDoor1], powerPeak);
        location += doorLength + silenceLength;
        // Check special word
        int code = Hamming12_8.encode(door2Check);
        for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
            if((code & (1 << idfreq)) != 0) {
                generatePitch(signal, location, doorLength / 2 ,configuration.sampleRate, frequenciesUptone[idfreq], powerPeak / frequencies.length);
            } else {
                generatePitch(signal, location + doorLength / 2, doorLength / 2 ,configuration.sampleRate, frequenciesUptone[idfreq], powerPeak / frequencies.length);
            }
        }
        if(unitTestCallback != null) {
            boolean[] freqs = new boolean[frequencies.length];
            for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                freqs[idfreq] = (code & (1 << idfreq)) != 0;
            }
            unitTestCallback.generateWord(door2Check, code, freqs);
        }
        location += doorLength;
        // Message
        for(int idword = 0; idword < blockLength; idword++) {
            location += silenceLength;
            code = Hamming12_8.encode(words[idword]);
            int ones = 0;
            // Count the number of waves in each columns to have stable emission levels
            for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                if ((code & (1 << idfreq)) != 0) {
                    ones++;
                }
            }
            for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                if((code & (1 << idfreq)) != 0) {
                    generatePitch(signal, location, wordLength / 2 ,configuration.sampleRate, idword % 2 == 0 ? frequencies[idfreq] : frequenciesUptone[idfreq], powerPeak / ones);
                } else {
                    generatePitch(signal, location + wordLength / 2, wordLength / 2 ,configuration.sampleRate, idword % 2 == 0 ? frequencies[idfreq] : frequenciesUptone[idfreq], powerPeak / (frequencies.length - ones));
                }
            }
            if(unitTestCallback != null) {
                boolean[] freqs = new boolean[frequencies.length];
                for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                    freqs[idfreq] = (code & (1 << idfreq)) != 0;
                }
                unitTestCallback.generateWord(words[idword], code, freqs);
            }
            location+= wordLength;
        }
        return signal;
    }

    /**
     * Encode and interleave using reed solomon algorithm
     * @param payload data to encode
     * @return Encoded data
     */
    public byte[] encodeReedSolomon(byte[] payload) {
        byte [] [] dataShards = new byte[OpenWarble.WARBLE_RS_P + OpenWarble.WARBLE_RS_DISTANCE][];
        byte[] blocks = Arrays.copyOf(payload, blockLength);
        // Init empty shards arrays
        for (int block = 0; block < OpenWarble.WARBLE_RS_P + OpenWarble.WARBLE_RS_DISTANCE; block++) {
            dataShards[block] = new byte[shardSize];
        }
        // Push payload
        for (int payloadIndex = 0; payloadIndex < payload.length; payloadIndex++) {
            final int blockId = payloadIndex % (OpenWarble.WARBLE_RS_P - 1);
            final int column = payloadIndex / (OpenWarble.WARBLE_RS_P - 1);
            dataShards[blockId][column] = payload[payloadIndex];
        }

        // push crc bytes
        int parityCursor = payload.length + OpenWarble.WARBLE_RS_DISTANCE * shardSize;
        for (int column = 0; column < shardSize; column++) {
            final int startPayload = column * (OpenWarble.WARBLE_RS_P - 1);
            final int endPayload = Math.min(configuration.payloadSize, startPayload + (OpenWarble.WARBLE_RS_P - 1));
            byte crc = crc8(payload, startPayload, endPayload);
            dataShards[OpenWarble.WARBLE_RS_P - 1][column] = crc;
            blocks[parityCursor++] = crc;
        }

        // Compute parity
        ReedSolomon codec = ReedSolomon.create(OpenWarble.WARBLE_RS_P, OpenWarble.WARBLE_RS_DISTANCE);
        codec.encodeParity(dataShards, 0, shardSize);

        final int totalShards = OpenWarble.WARBLE_RS_P+OpenWarble.WARBLE_RS_DISTANCE;

        // Copy parity bytes from RS structure to blocks
        int cursor = payload.length;
        for (int column = 0; column < shardSize; column++) {
            for(int row = OpenWarble.WARBLE_RS_P; row < totalShards; row++) {
                blocks[cursor++] = dataShards[row][column];
            }
        }

        return blocks;
    }

    /**
     * Compute all combinations of errors for n errors with n bytes array
     * @param currentNumberOfErrors
     * @param tryTable
     * @return
     */
    public static int nextErrorState(int currentNumberOfErrors, int[] tryTable, int nBytes) {
        if (tryTable[currentNumberOfErrors] == nBytes - 1) {
            // All possibilities have been done for the last entry
            int incrementCursor = currentNumberOfErrors - 1;
            while (incrementCursor >= 0) {
                if (tryTable[incrementCursor] < tryTable[incrementCursor + 1] - 1) {
                    tryTable[incrementCursor]++;
                    tryTable[incrementCursor + 1] = tryTable[incrementCursor] + 1;
                    break;
                }
                incrementCursor--;
            }
            if (incrementCursor < 0) {
                currentNumberOfErrors++;
                tryTable[0] = 0;
                for (int c = 1; c < tryTable.length; c++) {
                    tryTable[c] = tryTable[c - 1] + 1;
                }
            }
        } else {
            tryTable[currentNumberOfErrors] += 1;
        }
        return currentNumberOfErrors;
    }

    /**
     * deinterleave and decode using reed solomon algorithm
     * @param blocks data to decode
     * @return Decoded data
     */
    public ReedSolomonResult decodeReedSolomon(byte[] blocks) {
        int fixedErrors = 0;
        ReedSolomon reedSolomon = ReedSolomon.create(WARBLE_RS_P, WARBLE_RS_DISTANCE);
        byte[][] dataShards = new byte[WARBLE_RS_P + WARBLE_RS_DISTANCE][];

        // Init empty shards arrays
        for (int block = 0; block < WARBLE_RS_P + WARBLE_RS_DISTANCE; block++) {
            dataShards[block] = new byte[1];
        }

        // Check Reed Solomon sequences
        final int totalShards = OpenWarble.WARBLE_RS_P + OpenWarble.WARBLE_RS_DISTANCE;
        for (int idColumn = 0; idColumn < shardSize; idColumn++) {
            final int startPayload = idColumn * (OpenWarble.WARBLE_RS_P - 1);
            final int endPayload = Math.min(configuration.payloadSize, startPayload + (OpenWarble.WARBLE_RS_P - 1));
            // Check crc
            final int crcIndex = configuration.payloadSize +
                    OpenWarble.WARBLE_RS_DISTANCE * shardSize + idColumn;
            byte got = OpenWarble.crc8(blocks, startPayload, endPayload);
            byte expected = blocks[crcIndex];
            if (expected != got) {
                final int startParity = configuration.payloadSize + idColumn * OpenWarble.WARBLE_RS_DISTANCE;
                final int endParity = startParity + OpenWarble.WARBLE_RS_DISTANCE;
                // CRC Error
                // Prepare for reed solomon test

                // Clean dataShards table
                for (int block = 0; block < WARBLE_RS_P + WARBLE_RS_DISTANCE; block++) {
                    dataShards[block][0] = 0;
                }
                // Copy payload
                for (int payloadIndex = startPayload; payloadIndex < endPayload; payloadIndex++) {
                    final int blockId = payloadIndex % (OpenWarble.WARBLE_RS_P - 1);
                    dataShards[blockId][0] = blocks[payloadIndex];
                }
                // copy crc
                dataShards[OpenWarble.WARBLE_RS_P - 1][0] = expected;
                // Copy parity
                for (int parityIndex = startParity; parityIndex < endParity; parityIndex++) {
                    final int blockId = (parityIndex - startParity) % OpenWarble.WARBLE_RS_DISTANCE;
                    dataShards[OpenWarble.WARBLE_RS_P + blockId][0] = blocks[parityIndex];
                }
                // Some data have been altered
                // We can fix up to OpenWarble.WARBLE_RS_DISTANCE errors
                // But we don't know what is the missing bytes
                // So we have to check for all missing bytes possibilities against the expected crc
                // crc can also be corrected
                boolean[] shardPresent = new boolean[totalShards];
                int[] tryTable = new int[WARBLE_RS_DISTANCE];
                int tryCursor = 0;
                byte[] crcInput = new byte[OpenWarble.WARBLE_RS_P - 1];
                boolean errorFixed = false;
                byte[] originalBytes = new byte[OpenWarble.WARBLE_RS_P + OpenWarble.WARBLE_RS_DISTANCE];
                for (int row = 0; row < WARBLE_RS_P + OpenWarble.WARBLE_RS_DISTANCE; row++) {
                    originalBytes[row] = dataShards[row][0];
                }

                while (tryCursor < tryTable.length) {
                    Arrays.fill(shardPresent, true);
                    for (int c = 0; c < tryCursor + 1; c++) {
                        shardPresent[tryTable[c]] = false;
                    }
                    reedSolomon.decodeMissing(dataShards, shardPresent, 0, 1);
                    expected = dataShards[OpenWarble.WARBLE_RS_P - 1][0];
                    // crc check
                    for (int row = 0; row < WARBLE_RS_P - 1; row++) {
                        crcInput[row] = dataShards[row][0];
                    }
                    got = crc8(crcInput, 0, Math.min(crcInput.length, endPayload - startPayload));
                    if (got == expected) {
                        for (int row = 0; row < OpenWarble.WARBLE_RS_P; row++) {
                            if (!shardPresent[row]) {
                                if(row < OpenWarble.WARBLE_RS_P - 1) {
                                    // Copy data to block
                                    blocks[startPayload + row] = dataShards[row][0];
                                } else {
                                    // Fix crc
                                    blocks[crcIndex] = expected;
                                }
                            }
                        }
                        // Error(s) fixed !
                        errorFixed = true;
                        fixedErrors += 1;
                        break;
                    } else {
                        // Nothing has been fixed
                        // Restore bytes to original state
                        for (int c = 0; c < tryCursor + 1; c++) {
                            dataShards[tryTable[c]][0] = originalBytes[tryTable[c]];
                        }
                        // Compute the next possible missing shards situation
                        tryCursor = nextErrorState(tryCursor, tryTable, WARBLE_RS_P+WARBLE_RS_DISTANCE);
                    }
                }
                if(!errorFixed) {
                    return new ReedSolomonResult(fixedErrors, ReedSolomonResultCode.FAIL_CORRECTION, null);
                }
            }
        }
        return new ReedSolomonResult(fixedErrors,
                fixedErrors == 0 ? ReedSolomonResultCode.NO_ERRORS : ReedSolomonResultCode.CORRECTED_ERROR,
                Arrays.copyOfRange(blocks, 0, configuration.payloadSize));
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
     * randomly swap specified integer
     * @param n
     * @param index
     */
    void fisherYatesShuffleIndex(int n, int[] index) {
        int i;
        AtomicLong rndCache = new AtomicLong(n);
        for (i = index.length - 1; i > 0; i--) {
            index[index.length - 1 - i] = warbleRand(rndCache) % (i + 1);
        }
    }

    void swapChars(byte[] inputString, int[] index) {
        int i;
        for (i = inputString.length - 1; i > 0; i--)
        {
            int v = index[inputString.length - 1 - i];
            byte tmp = inputString[i];
            inputString[i] = inputString[v];
            inputString[v] = tmp;
        }
    }

    void unswapChars(byte[] inputString, int[] index) {
        int i;
        for (i = 1; i < inputString.length; i++) {
            int v = index[inputString.length - 1 - i];
            byte tmp = inputString[i];
            inputString[i] = inputString[v];
            inputString[v] = tmp;
        }
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

    public enum ReedSolomonResultCode {NO_ERRORS, CORRECTED_ERROR, FAIL_CORRECTION}

    public static final class ReedSolomonResult {
        public final int fixedErrors;
        public final ReedSolomonResultCode code;
        public final byte[] payload;

        public ReedSolomonResult(int fixedErrors, ReedSolomonResultCode code, byte[] payload) {
            this.fixedErrors = fixedErrors;
            this.code = code;
            this.payload = payload;
        }
    }
}
