/*
 * BSD 3-Clause License
 *
 * Copyright (c) Unité Mixte de Recherche en Acoustique Environnementale (univ-gustave-eiffel)
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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class QRTone {
    public static final double M2PI = Math.PI * 2;
    public static final long PERMUTATION_SEED = 3141592653589793238L;
    private enum STATE {WAITING_TRIGGER, PARSING_SYMBOLS};
    private static final double TUKEY_ALPHA  = 0.5;
    public static final int CRC_BYTE_LENGTH = 2;
    private STATE qrToneState = STATE.WAITING_TRIGGER;
    private IterativeGeneralizedGoertzel[] frequencyAnalyzers;
    private long firstToneSampleIndex = -1;
    protected static final int MAX_PAYLOAD_LENGTH = 0xFF;
    // Header size in bytes
    final static int HEADER_SIZE = 3;
    final static int HEADER_SYMBOLS = HEADER_SIZE*2;
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
    byte[] symbolsToDeliver;
    private byte[] symbolsCache;
    private Header headerCache;
    private long pushedSamples = 0;
    private int symbolIndex = 0;
    private byte[] payload;
    private AtomicInteger fixedErrors = new AtomicInteger(0);

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

    /**
     * @return The maximum window length to push in order to not loosing a second queued message
     */
    public int getMaximumWindowLength() {
        if(qrToneState == STATE.WAITING_TRIGGER) {
            return triggerAnalyzer.getMaximumWindowLength();
        } else {
            return frequencyAnalyzers[0].getWindowSize() - frequencyAnalyzers[0].getProcessedSamples();
        }
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public double[] getFrequencies() {
        return frequencies;
    }

    public int setPayload(byte[] payload) {
        return setPayload(payload, Configuration.DEFAULT_ECC_LEVEL, true);
    }

    static byte[] payloadToSymbols(byte[] payload) {
        byte[] symbols = new byte[payload.length * 2];
        for (int i = 0; i < payload.length; i++) {
            // offset most significant bits to the right without keeping sign
            symbols[i * 2] = (byte)((payload[i] >>> 4) & 0x0F);
            // keep only least significant bits for the second hexadecimal symbol
            symbols[i * 2 + 1] = (byte)(payload[i] & 0x0F);
        }
        return  symbols;
    }

    static byte[] payloadToSymbols(byte[] payload, Configuration.ECC_LEVEL eccLevel, boolean addCRC) {
        final int blockSymbolsSize = Configuration.getTotalSymbolsForEcc(eccLevel);
        final int blockECCSymbols = Configuration.getEccSymbolsForEcc(eccLevel);
        Header header = new Header(payload.length, eccLevel, addCRC);
        if(addCRC) {
            CRC16 crc16 = new CRC16();
            for(byte b : payload) {
                crc16.add(b);
            }
            payload = Arrays.copyOf(payload, payload.length + CRC_BYTE_LENGTH);
            final int res = crc16.crc();
            payload[payload.length - 2] = (byte)(res >>> 8);
            payload[payload.length - 1] = (byte)(res & 0xFF);
        }
        final int payloadSymbolsSize = header.payloadSymbolsSize;
        final int payloadByteSize = header.payloadByteSize;
        final int numberOfBlocks = header.numberOfBlocks;
        final int numberOfSymbols = header.numberOfSymbols;
        byte[] symbols = new byte[numberOfSymbols];
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
            arraycopy(blockSymbols, 0, symbols, blockId * blockSymbolsSize, payloadSize * 2);
            // Copy parity to main symbols
            arraycopy(blockSymbols, payloadSymbolsSize, symbols, blockId * blockSymbolsSize + payloadSize * 2, blockECCSymbols);
        }
        // Permute symbols
        interleaveSymbols(symbols, blockSymbolsSize);
        return symbols;
    }

    private static void arraycopy(int[] src, int srcPos, byte[] dest, int destPos, int length) {
        for(int i=0; i < length; i++) {
            dest[i+destPos] = (byte) src[i+srcPos];
        }
    }

    private static void arraycopy(byte[] src, int srcPos, int[] dest, int destPos, int length) {
        for(int i=0; i < length; i++) {
            dest[i+destPos] = src[i+srcPos];
        }
    }

    static byte[] symbolsToPayload(byte[] symbols, Configuration.ECC_LEVEL eccLevel, boolean hasCRC, AtomicInteger fixedErrors) throws ReedSolomonException {
        final int blockSymbolsSize = Configuration.getTotalSymbolsForEcc(eccLevel);
        final int blockECCSymbols = Configuration.getEccSymbolsForEcc(eccLevel);
        return symbolsToPayload(symbols, blockSymbolsSize, blockECCSymbols, hasCRC, fixedErrors);
    }

    /**
     * @return Parsed payload
     */
    public byte[] getPayload() {
        return payload;
    }

    static byte[] symbolsToPayload(byte[] symbols) {
        byte[] payload = new byte[symbols.length / 2];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ((symbols[i * 2] << 4) | (symbols[i * 2 + 1] & 0x0F));
        }
        return payload;
    }

    static byte[] symbolsToPayload(byte[] symbols, int blockSymbolsSize, int blockECCSymbols, boolean hasCRC, AtomicInteger fixedErrors) throws ReedSolomonException {
        final int payloadSymbolsSize = blockSymbolsSize - blockECCSymbols;
        final int payloadByteSize = payloadSymbolsSize / 2;
        final int payloadLength = ((symbols.length / blockSymbolsSize) * payloadSymbolsSize + Math.max(0, symbols.length % blockSymbolsSize - blockECCSymbols)) / 2;
        final int numberOfBlocks = (int)Math.ceil(symbols.length / (double)blockSymbolsSize);

        // Cancel permutation of symbols
        deinterleaveSymbols(symbols, blockSymbolsSize);
        int offset = 0;
        if(hasCRC) {
            offset = -2;
        }
        byte[] payload = new byte[payloadLength + offset];
        int[] crcValue = new int[CRC_BYTE_LENGTH];
        int crcIndex = 0;
        for(int blockId = 0; blockId < numberOfBlocks; blockId++) {
            int[] blockSymbols = new int[blockSymbolsSize];
            int payloadSymbolsLength = Math.min(payloadSymbolsSize, symbols.length - blockECCSymbols - blockId * blockSymbolsSize);
            // Copy payload symbols
            arraycopy(symbols, blockId * blockSymbolsSize, blockSymbols, 0, payloadSymbolsLength);
            // Copy parity sumbols
            arraycopy(symbols, blockId * blockSymbolsSize + payloadSymbolsLength, blockSymbols, payloadSymbolsSize, blockECCSymbols);
            // Use Reed-Solomon in order to fix correctable errors
            // Fix symbols thanks to ECC parity symbols
            GenericGF gallois = GenericGF.AZTEC_PARAM;
            ReedSolomonDecoder decoder = new ReedSolomonDecoder(gallois);
            int errors = decoder.decode(blockSymbols, blockECCSymbols);
            if(fixedErrors != null) {
                fixedErrors.addAndGet(errors);
            }
            int payloadBlockByteSize = Math.min(payloadByteSize, payloadLength + offset - blockId * payloadByteSize);
            for (int i = 0; i < payloadBlockByteSize; i++) {
                payload[i + blockId * payloadByteSize] = (byte) ((blockSymbols[i * 2] << 4) | (blockSymbols[i * 2 + 1] & 0x0F));
            }
            for (int i = Math.max(0, payloadBlockByteSize); i < Math.min(payloadByteSize, payloadLength - blockId * payloadByteSize); i++) {
                crcValue[crcIndex++] = ((blockSymbols[i * 2] << 4) | (blockSymbols[i * 2 + 1] & 0x0F));
            }
        }
        if(hasCRC) {
            int storedCRC = 0;
            storedCRC = storedCRC | crcValue[0] << 8;
            storedCRC = storedCRC | crcValue[1];
            // Check if fixed payload+CRC give a correct result
            if(crc16(payload, 0, payload.length) != storedCRC) {
                throw new ReedSolomonException("CRC check failed");
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

    /**
     * Set the payload to send
     * @param payload Payload content
     * @return Number of samples of the signal for {@link #getSamples(float[], int, double)}
     */
    public int setPayload(byte[] payload, Configuration.ECC_LEVEL eccLevel, boolean addPayloadCRC) {
        Header header = new Header(payload.length, eccLevel, addPayloadCRC);
        byte[] headerb = header.encodeHeader();
        // Convert bytes to hexadecimal array
        byte[] headerSymbols = payloadToSymbols(headerb);
        byte[] payloadSymbols = payloadToSymbols(payload, eccLevel, addPayloadCRC);
        symbolsToDeliver = new byte[headerSymbols.length+payloadSymbols.length];
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
        CRC8 crc8 = new CRC8();
        crc8.add(payload, from, to);
        return crc8.crc();
    }
    /**
     * Checksum of bytes (could be used only up to 64 bytes)
     * @param payload payload to crc
     * @param from payload index to begin crc
     * @param to excluded index to end crc
     * @return crc value
     */
    public static int crc16(byte[] payload, int from, int to) {
        CRC16 crc = new CRC16();
        for(int i = from; i < to; i++) {
            crc.add(payload[i]);
        }
        return crc.crc();
    }

    public static void interleaveSymbols(byte[] inputData, int blockSize) {
        byte[] interleavedData = new byte[inputData.length];
        int insertionCursor = 0;
        for(int j = 0; j < blockSize; j++) {
            int cursor = j;
            while (cursor < inputData.length) {
                interleavedData[insertionCursor++] = inputData[cursor];
                cursor += blockSize;
            }
        }
        System.arraycopy(interleavedData, 0, inputData, 0, interleavedData.length);
    }

    public static void deinterleaveSymbols(byte[] inputData, int blockSize) {
        byte[] interleavedData = new byte[inputData.length];
        int insertionCursor = 0;
        for(int j = 0; j < blockSize; j++) {
            int cursor = j;
            while (cursor < inputData.length) {
                interleavedData[cursor] = inputData[insertionCursor++];
                cursor += blockSize;
            }
        }
        System.arraycopy(interleavedData, 0, inputData, 0, interleavedData.length);
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

    private void feedTriggerAnalyzer(float[] samples) {
        triggerAnalyzer.processSamples(samples);
        if(triggerAnalyzer.getFirstToneLocation() != -1) {
            qrToneState = STATE.PARSING_SYMBOLS;
            firstToneSampleIndex = pushedSamples - (triggerAnalyzer.getTotalProcessed() - triggerAnalyzer.getFirstToneLocation());
            frequencyAnalyzers = new IterativeGeneralizedGoertzel[frequencies.length];
            for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                frequencyAnalyzers[idfreq] = new IterativeGeneralizedGoertzel(configuration.sampleRate, frequencies[idfreq], wordLength);
            }
            symbolsCache = new byte[HEADER_SYMBOLS];
            triggerAnalyzer.reset();
            fixedErrors.set(0);
        }
    }

    private boolean analyzeTones(float[] samples) {

        int cursor = Math.max(0, getToneIndex(samples.length));
        while (cursor < samples.length) {
            int windowLength = Math.min(samples.length - cursor, wordLength - frequencyAnalyzers[0].getProcessedSamples());
            if(windowLength == 0) {
                break;
            }
            float[] window = new float[windowLength];
            System.arraycopy(samples, cursor, window, 0, windowLength);
            applyHann(window, 0, windowLength, wordLength, frequencyAnalyzers[0].getProcessedSamples());
            for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                frequencyAnalyzers[idfreq].processSamples(window, 0, windowLength);
            }
            if(frequencyAnalyzers[0].getProcessedSamples() == wordLength) {
                double[] spl = new double[frequencies.length];
                for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                    double rmsValue = frequencyAnalyzers[idfreq].computeRMS(false).rms;
                    spl[idfreq] = 20 * Math.log10(rmsValue);
                }
                for(int symbolOffset = 0; symbolOffset < 2; symbolOffset++) {
                    int maxSymbolId = -1;
                    double maxSymbolGain = Double.NEGATIVE_INFINITY;
                    for(int idFreq = symbolOffset * FREQUENCY_ROOT; idFreq < (symbolOffset + 1) * FREQUENCY_ROOT; idFreq++) {
                        double gain = spl[idFreq];
                        if(gain > maxSymbolGain) {
                            maxSymbolGain = gain;
                            maxSymbolId = idFreq;
                        }
                    }
                    symbolsCache[this.symbolIndex * 2 + symbolOffset] = (byte)(maxSymbolId - symbolOffset * FREQUENCY_ROOT);
                }
                symbolIndex+=1;
                if(symbolIndex * 2 == symbolsCache.length) {
                    if(headerCache == null) {
                        byte[] payloads = symbolsToPayload(symbolsCache);
                        headerCache = Header.decodeHeader(payloads);
                        // CRC error
                        if(headerCache == null) {
                            reset();
                            break;
                        }
                        symbolsCache = new byte[headerCache.numberOfSymbols];
                        symbolIndex = 0;
                        firstToneSampleIndex += (HEADER_SYMBOLS / 2) * (wordLength+wordSilenceLength);
                    } else {
                        // Decoding complete
                        try {
                            payload = symbolsToPayload(symbolsCache, headerCache.eccLevel, true, fixedErrors);
                            reset();
                            return true;
                        } catch (ReedSolomonException ex) {
                            // Can't decode payload
                            reset();
                            break;
                        }
                    }
                }
            }
            cursor += windowLength;
        }
        return false;
    }

    /**
     * Analyze samples
     * @param samples Samples. Should not be greater than {@link #getMaximumWindowLength()} in order to not miss multiple messages
     * @return True if a payload has been decoded and can be retrieved with {@link #getPayload()}
     */
    public boolean pushSamples(float[] samples) {
        pushedSamples += samples.length;
        if(qrToneState == STATE.WAITING_TRIGGER) {
            feedTriggerAnalyzer(samples);
        }
        if(qrToneState == STATE.PARSING_SYMBOLS && firstToneSampleIndex + wordSilenceLength < pushedSamples) {
            return analyzeTones(samples);
        }
        return false;
    }

    public void reset() {
        symbolsCache = null;
        symbolIndex = 0;
        headerCache = null;
        firstToneSampleIndex = -1;
        qrToneState = STATE.WAITING_TRIGGER;
        symbolsToDeliver = null;
        frequencyAnalyzers = null;
        triggerAnalyzer.reset();
    }

    /**
     * @return Errors corrected by Reed-Solomon algorithm
     */
    public int getFixedErrors() {
        return fixedErrors.get();
    }

    private long getToneLocation() {
        return firstToneSampleIndex + symbolIndex * (wordLength + wordSilenceLength) + wordSilenceLength;
    }
    private int getToneIndex(int bufferLength) {
        return (int)(bufferLength - (pushedSamples - getToneLocation()));
    }

}
