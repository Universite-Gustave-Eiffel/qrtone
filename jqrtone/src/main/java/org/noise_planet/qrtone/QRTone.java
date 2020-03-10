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

import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;
import com.google.zxing.common.reedsolomon.ReedSolomonException;

import java.io.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class QRTone {
    public static final double M2PI = Math.PI * 2;
    public static final long PERMUTATION_SEED = 3141592653589793238L;
    private enum STATE {WAITING_TRIGGER, PARSING_SYMBOLS};
    private static final double TUKEY_ALPHA  = 0.5;
    private STATE qrToneState = STATE.WAITING_TRIGGER;
    private long firstToneSampleIndex = -1;
    protected static final int MAX_PAYLOAD_LENGTH = 0xFF;
    // Header size in bytes
    private final static int HEADER_SIZE = 2;
    final int wordLength;
    final int gateLength;
    final int wordSilenceLength;
    final double gate1Frequency;
    final double gate2Frequency;
    private Configuration configuration;
    // DTMF 16*16 frequencies
    public final static int NUM_FREQUENCIES = 32;
    // Column and rows of DTMF that make a char
    public final static int FREQUENCY_ROOT = 16;
    private final double[] frequencies;
    final TriggerAnalyzer triggerAnalyzer;
    private int[] symbolsToDeliver;

    public QRTone(Configuration configuration) {
        this.configuration = configuration;
        this.wordLength = (int)(configuration.sampleRate * configuration.wordTime);
        this.gateLength = (int)(configuration.sampleRate * configuration.gateTime);
        this.wordSilenceLength = (int)(configuration.sampleRate * configuration.wordSilenceTime);
        this.frequencies = configuration.computeFrequencies(NUM_FREQUENCIES);
        gate1Frequency = frequencies[FREQUENCY_ROOT ];
        gate2Frequency = frequencies[FREQUENCY_ROOT + 2];
        triggerAnalyzer = new TriggerAnalyzer(configuration.sampleRate, gateLength, new double[]{gate1Frequency, gate2Frequency}, configuration.triggerSnr);
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public double[] getFrequencies() {
        return frequencies;
    }

    public int setPayload(byte[] payload) {
        return setPayload(payload, Configuration.DEFAULT_ECC_LEVEL);
    }

    static int[] payloadToSymbols(byte[] payload, Configuration.ECC_LEVEL eccLevel) {
        final int blockSymbolsSize = Configuration.getTotalSymbolsForEcc(eccLevel);
        final int blockECCSymbols = Configuration.getEccSymbolsForEcc(eccLevel);
        return payloadToSymbols(payload, blockSymbolsSize, blockECCSymbols);
    }
    static int[] payloadToSymbols(byte[] payload, int blockSymbolsSize, int blockECCSymbols) {
        final int payloadSymbolsSize = blockSymbolsSize - blockECCSymbols;
        final int payloadByteSize = payloadSymbolsSize / 2;
        final int numberOfBlocks = (int)Math.ceil((payload.length * 2) / (double)payloadSymbolsSize);
        final int numberOfSymbols = numberOfBlocks * blockECCSymbols + payload.length * 2;
        int[] symbols = new int[numberOfSymbols];
        for(int blockId = 0; blockId < numberOfBlocks; blockId++) {
            int[] blockSymbols = new int[blockSymbolsSize];
            int payloadSize = Math.min(payloadByteSize, payload.length - blockId * payloadByteSize);
            for (int i = 0; i < payloadSize; i++) {
                // offset most significant bits to the right without keeping sign
                blockSymbols[i * 2] = (payload[i + blockId * payloadByteSize] >>> 4) & 0x0F;
                // keep only least significant bits for the second hexadecimal symbol
                blockSymbols[i * 2 + 1] = payload[i + blockId * payloadByteSize] & 0x0F;
            }
            // Add ECC parity symbols
            GenericGF gallois = GenericGF.AZTEC_PARAM;
            ReedSolomonEncoder encoder = new ReedSolomonEncoder(gallois);
            encoder.encode(blockSymbols, blockECCSymbols);
            // Copy data to main symbols
            System.arraycopy(blockSymbols, 0, symbols, blockId * blockSymbolsSize, payloadSize * 2);
            // Copy parity to main symbols
            System.arraycopy(blockSymbols, payloadSymbolsSize, symbols, blockId * blockSymbolsSize + payloadSize * 2, blockECCSymbols);
        }
        // Permute symbols
        int[] index = new int[symbols.length];
        fisherYatesShuffleIndex(PERMUTATION_SEED, index);
        swapSymbols(symbols, index);
        return symbols;
    }

    static byte[] symbolsToPayload(int[] symbols, Configuration.ECC_LEVEL eccLevel) throws ReedSolomonException {
        final int blockSymbolsSize =  Configuration.getTotalSymbolsForEcc(eccLevel);
        final int blockECCSymbols = Configuration.getEccSymbolsForEcc(eccLevel);
        final int payloadSymbolsSize = blockSymbolsSize - blockECCSymbols;
        final int payloadByteSize = payloadSymbolsSize / 2;
        final int payloadLength = ((symbols.length / blockSymbolsSize) * payloadSymbolsSize + Math.max(0, symbols.length % blockSymbolsSize - blockECCSymbols)) / 2;
        final int numberOfBlocks = (int)Math.ceil(symbols.length / (double)blockSymbolsSize);

        // Cancel permutation of symbols
        int[] index = new int[symbols.length];
        fisherYatesShuffleIndex(PERMUTATION_SEED, index);
        unswapSymbols(symbols, index);
        byte[] payload = new byte[payloadLength];
        for(int blockId = 0; blockId < numberOfBlocks; blockId++) {
            int[] blockSymbols = new int[blockSymbolsSize];
            int payloadSymbolsLength = Math.min(payloadSymbolsSize, symbols.length - blockECCSymbols - blockId * blockSymbolsSize);
            // Copy payload symbols
            System.arraycopy(symbols, blockId * blockSymbolsSize, blockSymbols, 0, payloadSymbolsLength);
            // Copy parity sumbols
            System.arraycopy(symbols, blockId * blockSymbolsSize + payloadSymbolsLength, blockSymbols, payloadSymbolsSize, blockECCSymbols);
            //int[] blockSymbols = Arrays.copyOfRange(symbols, blockId * blockSymbolsSize,
            //        blockId * blockSymbolsSize + blockSymbolsSize);
            // Fix symbols thanks to ECC parity symbols
            GenericGF gallois = GenericGF.AZTEC_PARAM;
            ReedSolomonDecoder decoder = new ReedSolomonDecoder(gallois);
            decoder.decode(blockSymbols, Configuration.getEccSymbolsForEcc(eccLevel));
            int payloadBlockByteSize = Math.min(payloadByteSize, payload.length - blockId * payloadByteSize);
            for (int i = 0; i < payloadBlockByteSize; i++) {
                payload[i + blockId * payloadByteSize] = (byte) ((blockSymbols[i * 2] << 4) | (blockSymbols[i * 2 + 1] & 0x0F));
            }
        }
        return payload;
    }

    /**
     * @param eccLevel Error correction level
     * @return Maximum payload length in bytes
     */
    public int maxPayloadLength(Configuration.ECC_LEVEL eccLevel) {
        return MAX_PAYLOAD_LENGTH;
    }

    byte[] encodeHeader(Header qrToneHeader) {
        if(qrToneHeader.length > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(String.format("Payload length cannot be superior than %d bytes", MAX_PAYLOAD_LENGTH));
        }
        // Generate header
        byte[] header = new byte[HEADER_SIZE];
        // Payload length
        header[0] = (byte)(qrToneHeader.length & 0xFF);
        // ECC level
        header[1] = (byte)(0x03 & qrToneHeader.eccLevel.ordinal());
        byte crc = crc8(header, 0, header.length);
        header[1] = (byte)((crc & 0xFC) | header[1] & 0x03);
        return header;
    }

    Header decodeHeader(byte[] data) {
        // Check
        byte[] header = new byte[HEADER_SIZE];
        header[0] = data[0];
        header[1] = (byte)(data[1] & 0x03);
        byte crc = crc8(header, 0, header.length);
        if((crc & 0xFC) != (data[1] & 0xFC)) {
            // CRC error
            return null;
        }
        return new Header(data[0] & 0xFF, Configuration.ECC_LEVEL.values()[data[1] & 0x03]);
    }

    /**
     * Set the payload to send
     * @param payload Payload content
     * @return Number of samples of the signal for {@link #getSamples(float[], int, double)}
     */
    public int setPayload(byte[] payload, Configuration.ECC_LEVEL eccLevel) {
        byte[] header = encodeHeader(new Header(payload.length, eccLevel));
        // Convert bytes to hexadecimal array
        int[] headerSymbols = payloadToSymbols(header, header.length * 2 + 2, 2);
        int[] payloadSymbols = payloadToSymbols(payload, eccLevel);
        symbolsToDeliver = new int[headerSymbols.length+payloadSymbols.length];
        System.arraycopy(headerSymbols, 0, symbolsToDeliver, 0, headerSymbols.length);
        System.arraycopy(payloadSymbols, 0, symbolsToDeliver, headerSymbols.length, payloadSymbols.length);
        return 2 * gateLength + (symbolsToDeliver.length / 2) * (wordSilenceLength + wordLength);
    }

    /**
     * Compute the audio samples for sending the message.
     *
     * @param samples Write samples here
     * @param offset  Offset from the beginning of the message.
     */
    public void getSamples(float[] samples, int offset, double power) {
        int cursor = 0;
        generatePitch(samples, Math.max(0, cursor - offset), gateLength, Math.max(0, offset - cursor), configuration.sampleRate, gate1Frequency, power);
        applyHann(samples, Math.max(0, cursor - offset), cursor + gateLength, gateLength, offset);
        cursor += gateLength;
        generatePitch(samples, Math.max(0, cursor - offset), gateLength, Math.max(0, offset - cursor), configuration.sampleRate, gate2Frequency, power);
        applyHann(samples, Math.max(0, cursor - offset), cursor + gateLength, gateLength, offset - cursor);
        cursor += gateLength;
        for (int i = 0; i < symbolsToDeliver.length; i += 2) {
            cursor += wordSilenceLength;
            double f1 = frequencies[symbolsToDeliver[i]];
            double f2 = frequencies[symbolsToDeliver[i + 1] + FREQUENCY_ROOT];
            generatePitch(samples, Math.max(0, cursor - offset), wordLength, Math.max(0, offset - cursor), configuration.sampleRate, f1, power / 2);
            generatePitch(samples, Math.max(0, cursor - offset), wordLength, Math.max(0, offset - cursor), configuration.sampleRate, f2, power / 2);
            applyTukey(samples, Math.max(0, cursor - offset), cursor + wordLength, TUKEY_ALPHA, wordLength, Math.max(0, offset - cursor));
            cursor += wordLength;
        }
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
     * Checksum of bytes
     * @param payload payload to crc
     * @param from payload index to begin crc
     * @param to excluded index to end crc
     * @return crc value
     */
    public static byte crc4(byte[] payload, int from, int to) {
        int crc4 = 0;
        for (int i=from; i < to; i++) {
            int crc = 0;
            int accumulator = (crc4 ^ payload[i]) & 0x00F;
            for (int j = 0; j < 8; j++) {
                if (((accumulator ^ crc) & 0x01) == 0x01) {
                    // best polynomial for crc4 0x9
                    // shifted to right so polynomial is 0x90
                    // https://users.ece.cmu.edu/~koopman/crc/index.html
                    crc = ((crc ^ 0x18) >> 1) | 0x90;
                } else {
                    crc = crc >> 1;
                }
                accumulator = accumulator >> 1;
            }
            crc4 = (byte) crc;
        }
        return (byte) (crc4 & 0x00F);
    }

    /**
     * randomly swap specified integer
     * @param seed Seed number for pseudo random generation
     * @param index Array of elements to permute
     */
    public static void fisherYatesShuffleIndex(long seed, int[] index) {
        int i;
        AtomicLong rndCache = new AtomicLong(seed);
        for (i = index.length - 1; i > 0; i--) {
            index[index.length - 1 - i] = warbleRand(rndCache) % (i + 1);
        }
    }

    public static void swapSymbols(int[] inputData, int[] index) {
        int i;
        for (i = inputData.length - 1; i > 0; i--)
        {
            final int v = index[inputData.length - 1 - i];
            final int tmp = inputData[i];
            inputData[i] = inputData[v];
            inputData[v] = tmp;
        }
    }

    public static void unswapSymbols(int[] inputData, int[] index) {
        int i;
        for (i = 1; i < inputData.length; i++) {
            final int v = index[inputData.length - 1 - i];
            final int tmp = inputData[i];
            inputData[i] = inputData[v];
            inputData[v] = tmp;
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
     * Apply hann window on provided signal
     * @param signal Signal to update
     * @param windowLength Hamming window length
     * @param offset If the signal length is inferior than windowLength, give the offset of the hamming window
     */
    public static void applyHann(float[] signal, int from, int to, int windowLength, int offset) {
        for (int i = 0; i < to - from && offset + i < windowLength && i+from < signal.length; i++) {
            signal[i + from] *= ((0.5 - 0.5 * Math.cos((M2PI * (i + offset)) / (windowLength - 1))));
        }
    }

    /**
     * Apply tukey window on specified array
     * @param signal Audio samples
     * @param from index of audio sample to begin with
     * @param to excluded index of audio sample to end with
     * @param alpha Tukay alpha (0-1)
     * @param windowLength full length of tukey window
     * @param offset Offset of the provided signal buffer (> 0)
     */
    public static void applyTukey(float[] signal,int from, int to, double alpha, int windowLength, int offset) {
        int index_begin_flat = (int)(Math.floor(alpha * (windowLength - 1) / 2.0));
        int index_end_flat = windowLength - index_begin_flat;
        double window_value = 0;
        for(int i=offset; i < index_begin_flat + 1 && i - offset + from < signal.length; i++) {
            window_value = 0.5 * (1 + Math.cos(Math.PI * (-1 + 2.0*(i)/alpha/(windowLength-1))));
            signal[i - offset + from] *= window_value;
        }
        // End Hann part
        for(int i=Math.max(offset , index_end_flat - 1); i < windowLength && i - offset + from < signal.length; i++) {
            window_value =0.5 * (1 +  Math.cos(Math.PI * (-2.0/alpha + 1 + 2.0*i/alpha/(windowLength-1))));
            signal[i - offset + from] *= window_value;
        }
    }

    /**
     * @param signal_out Where to write pitch samples
     * @param location Location index of pitch start
     * @param length Length of the pitch
     * @param offset Offset in samples of the pitch generation
     * @param sample_rate Samples rate in Hz
     * @param frequency Frequency of the pitch
     * @param powerPeak Peak RMS power of the pitch
     */
    public static void generatePitch(float[] signal_out, int location, int length, final int offset, double sample_rate, double frequency, double powerPeak) {
        double tStep = 1 / sample_rate;
        for(int i=0; i+offset < length && i+location < signal_out.length; i++) {
            signal_out[i+location] += Math.sin((i + offset) * tStep * M2PI * frequency) * powerPeak;
        }
    }

    void setTriggerCallback(TriggerAnalyzer.TriggerCallback triggerCallback) {
        triggerAnalyzer.setTriggerCallback(triggerCallback);
    }

    public static double computeRms(float[] signal) {
        double sum = 0;
        for (double aSignal : signal) {
            sum += aSignal * aSignal;
        }
        return Math.sqrt(sum / signal.length);
    }

    public void pushSamples(float[] samples) {
        // todo if state==trigger
        if(qrToneState == STATE.WAITING_TRIGGER) {
            triggerAnalyzer.processSamples(samples);
            if(triggerAnalyzer.getFirstToneLocation() != -1) {
                qrToneState = STATE.PARSING_SYMBOLS;
                int firstToneIndex = (int)(triggerAnalyzer.getTotalProcessed() -
                        triggerAnalyzer.getFirstToneLocation());
                if(firstToneIndex > wordSilenceLength) {
                    // First tone is contained in the current samples
                }
                triggerAnalyzer.reset();
            }
        }
    }

    protected static class Header {
        public final int length;
        public final Configuration.ECC_LEVEL eccLevel;

        public Header(int length, Configuration.ECC_LEVEL eccLevel) {
            this.length = length;
            this.eccLevel = eccLevel;
        }
    }
}
