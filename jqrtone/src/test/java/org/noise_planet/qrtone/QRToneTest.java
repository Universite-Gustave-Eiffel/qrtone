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

import com.google.zxing.common.reedsolomon.ReedSolomonException;
import org.junit.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class QRToneTest {
    // Send Ipfs address
    // Python code:
    // import base58
    // import struct
    // s = struct.Struct('b').unpack
    // payload = map(lambda v : s(v)[0], base58.b58decode("QmXjkFQjnD8i8ntmwehoAHBfJEApETx8ebScyVzAHqgjpD"))
    public static final byte[] IPFS_PAYLOAD = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};

    @Test
    public void crcTest() {
        byte base = QRTone.crc8(IPFS_PAYLOAD, 0, IPFS_PAYLOAD.length);
        AtomicLong next = new AtomicLong(1337);
        for(int i=0; i < IPFS_PAYLOAD.length; i++) {
            byte[] alteredPayload = Arrays.copyOf(IPFS_PAYLOAD, IPFS_PAYLOAD.length);
            alteredPayload[i] = (byte) (QRTone.warbleRand(next) % 255);
            assertNotEquals(base, QRTone.crc8(alteredPayload, 0, alteredPayload.length));
        }
    }

    @Test
    public void crc4Test() {
        byte[] expectedPayload = new byte[]{18, 32};
        byte base = QRTone.crc8(expectedPayload, 0, expectedPayload.length);
        AtomicLong next = new AtomicLong(1337);
        for(int i=0; i < expectedPayload.length; i++) {
            byte[] alteredPayload = Arrays.copyOf(expectedPayload, expectedPayload.length);
            alteredPayload[i] = (byte) (QRTone.warbleRand(next) % 255);
            assertNotEquals(base, QRTone.crc4(alteredPayload, 0, alteredPayload.length));
        }
    }
    @Test
    public void hannTest() {
        float[] ref= {0f,0.0039426493f,0.015708419f,0.035111757f,0.06184666f,0.095491503f,0.13551569f,0.18128801f,
                0.2320866f,0.28711035f,0.3454915f,0.40630934f,0.46860474f,0.53139526f,0.59369066f,0.6545085f,
                0.71288965f,0.7679134f,0.81871199f,0.86448431f,0.9045085f,0.93815334f,0.96488824f,0.98429158f,
                0.99605735f,1f,0.99605735f,0.98429158f,0.96488824f,0.93815334f,0.9045085f,0.86448431f,0.81871199f,
                0.7679134f,0.71288965f,0.6545085f,0.59369066f,0.53139526f,0.46860474f,0.40630934f,0.3454915f,
                0.28711035f,0.2320866f,0.18128801f,0.13551569f,0.095491503f,0.06184666f,0.035111757f,0.015708419f,
                0.0039426493f,0f} ;
        float[] signal = new float[ref.length];
        Arrays.fill(signal, 1);
        QRTone.applyHann(signal,0,  signal.length, signal.length, 0);
        assertArrayEquals(ref, signal, 1e-6f);
    }


    @Test
    public void generalized_goertzel() throws Exception {
        double sampleRate = 44100;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        float signalFrequency = 1000;
        double powerPeak = powerRMS * Math.sqrt(2);

        float[] audio = new float[4410];
        for (int s = 0; s < audio.length; s++) {
            double t = s * (1 / sampleRate);
            audio[s] = (float)(Math.cos(QRTone.M2PI * signalFrequency * t) * (powerPeak));
        }

        IterativeGeneralizedGoertzel.GoertzelResult res = new IterativeGeneralizedGoertzel(sampleRate, signalFrequency,
                audio.length).processSamples(audio, 0, audio.length).computeRMS(true);


        double signal_rms = QRTone.computeRms(audio);

        assertEquals(signal_rms, res.rms, 0.1);
        assertEquals(0, res.phase, 1e-8);
    }

    @Test
    public void generalized_goertzelIterativeTest() throws Exception {
        double sampleRate = 44100;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        float signalFrequency = 1000;
        double powerPeak = powerRMS * Math.sqrt(2);

        float[] audio = new float[44100];
        for (int s = 0; s < audio.length; s++) {
            double t = s * (1 / sampleRate);
            audio[s] = (float)(Math.cos(QRTone.M2PI * signalFrequency * t) * (powerPeak));
        }

        int cursor = 0;
        Random random = new Random(1337);
        IterativeGeneralizedGoertzel goertzel = new IterativeGeneralizedGoertzel(sampleRate, signalFrequency,
                audio.length);
        while (cursor < audio.length) {
            int windowSize = Math.min(random.nextInt(115) + 20, audio.length - cursor);
            goertzel.processSamples(audio, cursor, cursor + windowSize);
            cursor += windowSize;
        }
        IterativeGeneralizedGoertzel.GoertzelResult res = goertzel.computeRMS(true);


        double signal_rms = QRTone.computeRms(audio);

        assertEquals(20 * Math.log10(signal_rms), 20 * Math.log10(res.rms), 0.1);
        assertEquals(0, res.phase, 1e-8);
    }

    public void printArray(double[] frequencies, double[]... arrays) {
        for (int idfreq = 0; idfreq < QRTone.NUM_FREQUENCIES; idfreq++) {
            if (idfreq > 0) {
                System.out.print(", ");
            }
            System.out.print(String.format(Locale.ROOT, "%.0f Hz", frequencies[idfreq]));
        }
        System.out.print("\n");
        for(int idarray = 0; idarray < arrays.length; idarray++) {
            for (int idfreq = 0; idfreq < QRTone.NUM_FREQUENCIES; idfreq++) {
                if (idfreq > 0) {
                    System.out.print(", ");
                }
                System.out.print(String.format(Locale.ROOT, "%.2f", 20 * Math.log10(arrays[idarray][idfreq])));
            }
            System.out.print("\n");
        }
    }

    @Test
    public void testGoertzelLeaks() {


        double sampleRate = 44100;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        double powerPeak = powerRMS * Math.sqrt(2);
        Configuration configuration = Configuration.getAudible(sampleRate);
        double[] frequencies = configuration.computeFrequencies(QRTone.NUM_FREQUENCIES);
        int signalFreqIndex = 1;

        float[] audio = new float[30000];
        double[] reference = new double[QRTone.NUM_FREQUENCIES];
        for (int s = 0; s < audio.length; s++) {
            double t = s * (1 / sampleRate);
            audio[s] = (float) (Math.cos(QRTone.M2PI * frequencies[signalFreqIndex] * t) * (powerPeak));
        }
        int windowSize = configuration.computeMinimumWindowSize(sampleRate, frequencies[0], frequencies[1]);
        int s = 0;
        double rms[] = new double[QRTone.NUM_FREQUENCIES];
        IterativeGeneralizedGoertzel[] goertzel = new IterativeGeneralizedGoertzel[QRTone.NUM_FREQUENCIES];
        for (int idfreq = 0; idfreq < QRTone.NUM_FREQUENCIES; idfreq++) {
            goertzel[idfreq] = new IterativeGeneralizedGoertzel(sampleRate,frequencies[idfreq], windowSize);
        }
        int pushed = 0;
        while (s < audio.length - (windowSize + windowSize / 2)) {
            // First
            float[] window = Arrays.copyOfRange(audio, s, s + windowSize);
            // Square window
            for (int idfreq = 0; idfreq < QRTone.NUM_FREQUENCIES; idfreq++) {
                goertzel[idfreq].processSamples(window, 0, window.length);
                reference[idfreq] += goertzel[idfreq].computeRMS(false).rms;
            }
            // Hann window
            QRTone.applyHann(window,0, window.length, window.length, 0);
            for (int idfreq = 0; idfreq < QRTone.NUM_FREQUENCIES; idfreq++) {
                goertzel[idfreq].processSamples(window, 0, window.length);
                rms[idfreq] += goertzel[idfreq].computeRMS(false).rms;
            }
            // Second Hann window
            window = Arrays.copyOfRange(audio, s + windowSize / 2, s + windowSize + windowSize / 2);
            QRTone.applyHann(window, 0, window.length, window.length, 0);
            for (int idfreq = 0; idfreq < QRTone.NUM_FREQUENCIES; idfreq++) {
                goertzel[idfreq].processSamples(window, 0, window.length);
                rms[idfreq] += goertzel[idfreq].computeRMS(false).rms;
            }
            // next
            pushed += 1;
            s += windowSize;
        }
        for (int idfreq = 0; idfreq < QRTone.NUM_FREQUENCIES; idfreq++) {
            rms[idfreq] /= pushed;
            reference[idfreq] /= pushed;
        }

        double refSignal = 20*Math.log10(rms[signalFreqIndex]);

        // Print results
        printArray(frequencies, reference, rms);

        double leakfzero = refSignal - 20*Math.log10(rms[signalFreqIndex - 1]);
        double leakfone = refSignal - 20*Math.log10(rms[signalFreqIndex + 1]);

        System.out.println(String.format(Locale.ROOT, "Maximum leak=%.2f", Math.min(leakfzero, leakfone)));

        // We need sufficient level decrease on neighbors frequencies
        assertTrue(35 < leakfzero);
        assertTrue(35 < leakfone);

    }

    @Test
    public void testTukeyWindow() {
        float[] expected = new float[]{0f,0.0157084f,0.0618467f,0.135516f,0.232087f,0.345492f,0.468605f,0.593691f,
                0.71289f,0.818712f,0.904508f,0.964888f,0.996057f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,
                1f,1f,1f,1f,1f,1f,1f,1f,0.996057f,0.964888f,0.904508f,0.818712f,0.71289f,0.593691f,0.468605f,0.345492f,
                0.232087f,0.135516f,0.0618467f,0.0157084f,0f
        };

        float[] window = new float[expected.length];
        Arrays.fill(window, 1.0f);

        QRTone.applyTukey(window,0, window.length, 0.5, window.length, 0);

        assertArrayEquals(expected, window, 1e-5f);
    }


    /**
     * Check equality between iterative window function on random size windows with full window Tukey
     *
     * @throws Exception
     */
    @Test
    public void iterativeTestTukey() throws Exception {
        double sampleRate = 44100;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        float signalFrequency = 1000;
        double powerPeak = powerRMS * Math.sqrt(2);

        float[] audio = new float[44100];
        for (int s = 0; s < audio.length; s++) {
            double t = s * (1 / sampleRate);
            audio[s] = (float) (Math.cos(QRTone.M2PI * signalFrequency * t) * (powerPeak));
        }

        int cursor = 0;
        Random random = new Random(1337);
        double iterativeRMS = 0;
        while (cursor < audio.length) {
            int windowSize = Math.min(random.nextInt(115) + 20, audio.length - cursor);
            float[] window = new float[windowSize];
            System.arraycopy(audio, cursor, window, 0, window.length);
            QRTone.applyTukey(window,0, window.length, 0.5, audio.length, cursor);
            for (float v : window) {
                iterativeRMS += v * v;
            }
            cursor += windowSize;
        }
        QRTone.applyTukey(audio,0, audio.length, 0.5, audio.length, 0);
        double signal_rms = QRTone.computeRms(audio);
        assertEquals(signal_rms, Math.sqrt(iterativeRMS / audio.length), 1e-6);
    }

    public static void writeShortToFile(String path, short[] signal) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(path);
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
            for(int i = 0; i < signal.length; i++) {
                byteBuffer.putShort(0, signal[i]);
                fileOutputStream.write(byteBuffer.array());
            }
        } finally {
            fileOutputStream.close();
        }
    }

    public static void writeFloatToFile(String path, float[] signal) throws IOException {
        short[] shortSignal = new short[signal.length];
        double maxValue = Double.MIN_VALUE;
        for (double aSignal : signal) {
            maxValue = Math.max(maxValue, Math.abs(aSignal));
        }
        maxValue *= 2;
        for(int i=0; i<signal.length;i++) {
            shortSignal[i] = (short)((signal[i] / maxValue) * Short.MAX_VALUE);
        }
        writeShortToFile(path, shortSignal);
    }

    private void pushTone(float[] signal,int location,double frequency, QRTone qrTone, double powerPeak) {
        float[] tone = new float[qrTone.wordLength];
        QRTone.generatePitch(tone, 0, qrTone.wordLength, 0, qrTone.getConfiguration().sampleRate, frequency, powerPeak);
        QRTone.applyTukey(tone, 0, tone.length, 0.8, tone.length, 0);
        for(int i=0; i < tone.length; i++) {
            signal[location+i] += tone[i];
        }
    }

    @Test
    public void randTest() {
        // This specific random must give the same results regardless of the platform/compiler
        int[] expected= new int[] {1199,22292,14258,30291,11005,15335,22572,27361,8276,27653};
        AtomicLong seed = new AtomicLong(1337);
        for(int expectedValue : expected) {
            assertEquals(expectedValue, QRTone.warbleRand(seed));
        }
    }

    @Test
    public void testShuffle1() {
        int[] expectedPayload = new int[]{18, 32, -117, -93, -50, 2, 52, 26, -117, 93};
        int[] swappedPayload = new int[]{26, -50, -93, 18, -117, 93, 2, 32, -117, 52};
        int[] index = new int[expectedPayload.length];
        QRTone.fisherYatesShuffleIndex(QRTone.PERMUTATION_SEED, index);
        int[] symbols = Arrays.copyOf(expectedPayload, expectedPayload.length);
        QRTone.swapSymbols(symbols, index);
        assertArrayEquals(swappedPayload, symbols);
    }

    @Test
    public void testShuffle() {
        int[] expectedPayload = new int[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93};
        int[] index = new int[expectedPayload.length];
        QRTone.fisherYatesShuffleIndex(QRTone.PERMUTATION_SEED, index);
        int[] symbols = Arrays.copyOf(expectedPayload, expectedPayload.length);
        QRTone.swapSymbols(symbols, index);
        QRTone.unswapSymbols(symbols, index);
        assertArrayEquals(expectedPayload, symbols);
    }

    @Test
    public void testEncodeDecodeHeader() {
        QRTone.Header expectedHeader = new QRTone.Header(QRTone.MAX_PAYLOAD_LENGTH, Configuration.ECC_LEVEL.ECC_H);
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        byte[] headerBytes = qrTone.encodeHeader(expectedHeader);
        QRTone.Header decodedHeader = qrTone.decodeHeader(headerBytes);
        assertEquals(expectedHeader.length, decodedHeader.length);
        assertEquals(expectedHeader.eccLevel, decodedHeader.eccLevel);
    }

    @Test
    public void testEncodeDecodeHeaderCRC() {
        QRTone.Header expectedHeader = new QRTone.Header(QRTone.MAX_PAYLOAD_LENGTH, Configuration.ECC_LEVEL.ECC_H);
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        byte[] headerBytes = qrTone.encodeHeader(expectedHeader);
        headerBytes[1] = (byte)(headerBytes[1] | 0x04);
        QRTone.Header decodedHeader = qrTone.decodeHeader(headerBytes);
        assertNull(decodedHeader);
    }

    @Test
    public void testEncodeDecodeHeaderCRC2() {
        QRTone.Header expectedHeader = new QRTone.Header(QRTone.MAX_PAYLOAD_LENGTH, Configuration.ECC_LEVEL.ECC_H);
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        byte[] headerBytes = qrTone.encodeHeader(expectedHeader);
        headerBytes[0] = (byte)(0xFE);
        QRTone.Header decodedHeader = qrTone.decodeHeader(headerBytes);
        assertNull(decodedHeader);
    }

    @Test
    public void testSymbolEncodingDecodingL() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_L;
        int[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingM() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_M;
        int[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingQ() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_Q;
        int[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingH() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_H;
        int[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingCRC1() throws ReedSolomonException {
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_Q;
        int[] symbols = QRTone.payloadToSymbols(IPFS_PAYLOAD, eccLevel);
        System.out.println(String.format(Locale.ROOT, "Signal length %.3f seconds", (symbols.length / 2) * 0.06 + 0.012));
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel);
        assertArrayEquals(IPFS_PAYLOAD, processedBytes);
    }


    public void testToneGeneration() throws IOException {
        double sampleRate = 44100;
        double timeBlankBefore = 3;
        double timeBlankAfter = 2;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        double powerPeak = powerRMS * Math.sqrt(2);
        double noisePeak = Math.pow(10, -80.0 / 20.0);
        int samplesBefore = (int)(timeBlankBefore * sampleRate);
        int samplesAfter = (int)(timeBlankAfter * sampleRate);
        Configuration configuration = Configuration.getAudible(sampleRate);
        QRTone qrTone = new QRTone(configuration);
        System.out.println(String.format(Locale.ROOT, "%.1f Hz", qrTone.getFrequencies()[31]));
        final int dataSampleLength = qrTone.setPayload(IPFS_PAYLOAD);
        float[] samples = new float[samplesBefore + dataSampleLength + samplesAfter];
        Random random = new Random(QRTone.PERMUTATION_SEED);
        int cursor = 0;
        while (cursor < dataSampleLength) {
            int windowSize = Math.min(random.nextInt(115) + 20, samples.length - cursor);
            float[] window = new float[windowSize];
            qrTone.getSamples(window, cursor, powerPeak);
            System.arraycopy(window, 0, samples, cursor + samplesBefore, window.length);
            cursor += windowSize;
        }
        for (int s = 0; s < samples.length; s++) {
            samples[s] += (float)(random.nextGaussian() * noisePeak);
        }
        writeFloatToFile("target/toneSignal.raw", samples);

    }

    @Test
    public void testToneDetection() throws IOException {
        double sampleRate = 44100;
        double timeBlankBefore = 1.1333;
        double timeBlankAfter = 2;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        double powerPeak = powerRMS * Math.sqrt(2);
        double noisePeak = Math.pow(10, -50.0 / 20.0); // -26 dBFS
        int samplesBefore = (int)(timeBlankBefore * sampleRate);
        int samplesAfter = (int)(timeBlankAfter * sampleRate);
        Configuration configuration = Configuration.getAudible(sampleRate);
        QRTone qrTone = new QRTone(configuration);
        CSVWriter csvWriter = new CSVWriter();
        csvWriter.open("target/spectrum.csv");
        qrTone.setTriggerCallback(csvWriter);
        final int dataSampleLength = qrTone.setPayload(IPFS_PAYLOAD);
        float[] audio = new float[dataSampleLength];
        float[] samples = new float[samplesBefore + dataSampleLength + samplesAfter];
        qrTone.getSamples(audio, 0, powerPeak);
        System.arraycopy(audio, 0, samples, samplesBefore, qrTone.gateLength * 2);
        Random random = new Random(1337);
        for (int s = 0; s < samples.length; s++) {
            samples[s] += (float)(random.nextGaussian() * noisePeak);
        }
        long start = System.currentTimeMillis();
        int cursor = 0;
        while (cursor < samples.length) {
            int windowSize = Math.min(random.nextInt(115) + 20, samples.length - cursor);
            float[] window = new float[windowSize];
            System.arraycopy(samples, cursor, window, 0, window.length);
            qrTone.pushSamples(window);
            cursor += windowSize;
        }
        System.out.println(String.format("Done in %.3f",(System.currentTimeMillis() - start) /1e3));
        csvWriter.close();
        //writeFloatToFile("target/inputSignal.raw", samples);
    }

    static class CSVWriter implements TriggerAnalyzer.TriggerCallback {
        double[] frequencies;
        Writer writer;
        public void open(String path) throws FileNotFoundException {
            FileOutputStream fos = new FileOutputStream(path);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            writer = new OutputStreamWriter(bos);
        }

        @Override
        public void onNewLevels(TriggerAnalyzer triggerAnalyzer, long location, double[] spl) {
            try {
                if (frequencies == null) {
                    frequencies = triggerAnalyzer.frequencies;
                    writer.write("t");
                    for (double frequency : frequencies) {
                        writer.write(String.format(Locale.ROOT, ",%.0f Hz (L)", frequency));
                        writer.write(String.format(Locale.ROOT, ",%.0f Hz (L50)", frequency));
                    }
                    writer.write("\n");
                }
                writer.write(String.format(Locale.ROOT, "%.3f", location / triggerAnalyzer.sampleRate));
                for (int idFreq=0; idFreq<spl.length; idFreq++) {
                    writer.write(String.format(Locale.ROOT, ",%.2f", spl[idFreq]));
                    writer.write(String.format(Locale.ROOT, ",%.2f", triggerAnalyzer.backgroundNoiseEvaluator[idFreq].result() + 15));
                }
                writer.write("\n");
            } catch (IOException ex) {
                System.err.println(ex.getLocalizedMessage());
            }
        }

        public void close() throws IOException {
            writer.flush();
            writer.close();
        }
    }

    @Test
    public void testPeakFinding() {
        float[] samples = new float[521];
        double sigma = 0.5;
        // Create gaussian
        float maxVal = Float.MIN_VALUE;
        int maxIndex = 0;
        for(int i = 0; i < samples.length; i++) {
            samples[i] = (float)Math.exp(-1.0/2.0 * Math.pow((i - samples.length / 2.) / (sigma * samples.length / 2.), 2));
            if(maxVal < samples[i]) {
                maxVal = samples[i];
                maxIndex = i;
            }
        }
        maxVal = Float.MIN_VALUE;
        int windowIndex = 0;
        int window = 35;
        for(int i=window; i < samples.length; i+=window) {
            if(maxVal < samples[i]) {
                maxVal = samples[i];
                windowIndex = i;
            }
        }
        // Estimation of peak position
        assertEquals(maxIndex, TriggerAnalyzer.findPeakLocation(samples[windowIndex-window], samples[windowIndex], samples[windowIndex+window], windowIndex, window));
        // Estimation of peak height, should be 1.0
        double[] vals = TriggerAnalyzer.quadraticInterpolation(samples[windowIndex-window], samples[windowIndex], samples[windowIndex+window]);
        assertEquals(1.0, vals[1], 1e-3);
    }
}