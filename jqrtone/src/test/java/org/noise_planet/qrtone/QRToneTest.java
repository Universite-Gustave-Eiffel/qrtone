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

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.GainProcessor;
import be.tarsos.dsp.effects.DelayEffect;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import com.google.zxing.common.reedsolomon.ReedSolomonException;
import org.junit.Test;
import org.noise_planet.qrtone.utils.ArrayWriteProcessor;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
            alteredPayload[i] = (byte) (QRTone.rand(next) % 255);
            assertNotEquals(base, QRTone.crc8(alteredPayload, 0, alteredPayload.length));
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
                audio.length, false).processSamples(audio, 0, audio.length).computeRMS(true);


        double signal_rms = QRTone.computeRms(audio);

        assertEquals(signal_rms, res.rms, 0.1);
        assertEquals(0, res.phase, 1e-8);
    }

    @Test
    public void generalized_goertzelHann() throws Exception {
        double sampleRate = 44100;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        float signalFrequency = 1000;
        double powerPeak = powerRMS * Math.sqrt(2);

        float[] audio = new float[24];
        for (int s = 0; s < audio.length; s++) {
            double t = s * (1 / sampleRate);
            audio[s] = (float)(Math.cos(QRTone.M2PI * signalFrequency * t) * (powerPeak));
        }

        IterativeGeneralizedGoertzel.GoertzelResult res = new IterativeGeneralizedGoertzel(sampleRate, signalFrequency,
                audio.length, true).processSamples(audio, 0, audio.length).computeRMS(true);

        QRTone.applyHann(audio, 0, audio.length, audio.length, 0);

        IterativeGeneralizedGoertzel.GoertzelResult res2 = new IterativeGeneralizedGoertzel(sampleRate, signalFrequency,
                audio.length, false).processSamples(audio, 0, audio.length).computeRMS(true);

        assertEquals(res.rms, res2.rms, 1e-6);
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
                audio.length, false);
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
            goertzel[idfreq] = new IterativeGeneralizedGoertzel(sampleRate,frequencies[idfreq], windowSize, false);
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
    public void testIntegratedHannWindow() {
        double sampleRate = 44100;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        float signalFrequency = 1000;
        double powerPeak = powerRMS * Math.sqrt(2);

        float[] audio = new float[4410];
        for (int s = 0; s < audio.length; s++) {
            double t = s * (1 / sampleRate);
            audio[s] = (float)(Math.cos(QRTone.M2PI * signalFrequency * t) * (powerPeak));
        }

        // test with goertzel with integrated hann windowing
        IterativeGeneralizedGoertzel g = new IterativeGeneralizedGoertzel(sampleRate, signalFrequency,
                audio.length, true);
        g.processSamples(audio, 0, audio.length);
        IterativeGeneralizedGoertzel.GoertzelResult res1 = g.computeRMS(true);

        // test with goertzel with separated hann windowing
        QRTone.applyHann(audio, 0, audio.length, audio.length, 0);
        IterativeGeneralizedGoertzel.GoertzelResult res = new IterativeGeneralizedGoertzel(sampleRate, signalFrequency,
                audio.length, false).processSamples(audio, 0, audio.length).computeRMS(true);

        assertEquals(res1.rms, res.rms, 0.001);
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

    public static short[] convertBytesToShort(byte[] buffer, int length, ByteOrder byteOrder) {
        ShortBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, length).order(byteOrder).asShortBuffer();
        short[] samplesShort = new short[byteBuffer.capacity()];
        byteBuffer.order();
        byteBuffer.get(samplesShort);
        return samplesShort;
    }

    public static float[] loadShortStream(InputStream inputStream, ByteOrder byteOrder) throws IOException {
        float[] fullArray = new float[0];
        byte[] buffer = new byte[4096];
        int read;
        // Read input signal up to buffer.length
        while ((read = inputStream.read(buffer)) != -1) {
            // Convert bytes into double values. Samples array size is 8 times inferior than buffer size
            if (read < buffer.length) {
                buffer = Arrays.copyOfRange(buffer, 0, read);
            }
            short[] signal = convertBytesToShort(buffer, buffer.length, byteOrder);
            float[] nextFullArray = new float[fullArray.length + signal.length];
            if(fullArray.length > 0) {
                System.arraycopy(fullArray, 0, nextFullArray, 0, fullArray.length);
            }
            for(int i = 0; i < signal.length; i++) {
                nextFullArray[fullArray.length + i] = signal[i] / (float)Short.MAX_VALUE;
            }
            fullArray = nextFullArray;
        }
        return fullArray;
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

    @Test
    public void randTest() {
        // This specific random must give the same results regardless of the platform/compiler
        int[] expected= new int[] {1199,22292,14258,30291,11005,15335,22572,27361,8276,27653};
        AtomicLong seed = new AtomicLong(1337);
        for(int expectedValue : expected) {
            assertEquals(expectedValue, QRTone.rand(seed));
        }
    }

    @Test
    public void testEncodeDecodeHeader() {
        Header expectedHeader = new Header(QRTone.MAX_PAYLOAD_LENGTH, Configuration.ECC_LEVEL.ECC_H, true);
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        byte[] headerBytes = expectedHeader.encodeHeader();
        Header decodedHeader = Header.decodeHeader(headerBytes);
        assertNotNull(decodedHeader);
        assertEquals(expectedHeader.length, decodedHeader.length);
        assertEquals(expectedHeader.getEccLevel(), decodedHeader.getEccLevel());
    }

    @Test
    public void testEncodeDecodeHeaderCRC() {
        Header expectedHeader = new Header(QRTone.MAX_PAYLOAD_LENGTH, Configuration.ECC_LEVEL.ECC_L, true);
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        byte[] headerBytes = expectedHeader.encodeHeader();
        headerBytes[1] = (byte)(headerBytes[1] | 0x04);
        Header decodedHeader = Header.decodeHeader(headerBytes);
        assertNull(decodedHeader);
    }

    @Test
    public void testEncodeDecodeHeaderCRC2() {
        Header expectedHeader = new Header(QRTone.MAX_PAYLOAD_LENGTH, Configuration.ECC_LEVEL.ECC_H, true);
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        byte[] headerBytes = expectedHeader.encodeHeader();
        headerBytes[0] = (byte)(0xFE);
        Header decodedHeader = Header.decodeHeader(headerBytes);
        assertNull(decodedHeader);
    }

    @Test
    public void testEncodeDecodeMessage() throws ReedSolomonException {
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        qrTone.setPayload(IPFS_PAYLOAD);
        byte[] symbols = qrTone.symbolsToDeliver;
        qrTone.symbolsCache = Arrays.copyOfRange(symbols, 0, QRTone.HEADER_SYMBOLS);
        qrTone.cachedSymbolsToHeader();
        assertNotNull(qrTone.headerCache);
        assertEquals(IPFS_PAYLOAD.length, qrTone.headerCache.length);
        qrTone.symbolsCache = Arrays.copyOfRange(symbols, QRTone.HEADER_SYMBOLS, symbols.length);
        qrTone.cachedSymbolsToPayload();
        byte[] payloadData = qrTone.getPayload();
        assertNotNull(payloadData);
        assertArrayEquals(IPFS_PAYLOAD, payloadData);
        assertEquals(0, qrTone.getFixedErrors());
    }

    @Test
    public void testEncodeDecodeMessageM() throws ReedSolomonException {
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        qrTone.setPayload(IPFS_PAYLOAD, Configuration.ECC_LEVEL.ECC_L, false);
        byte[] symbols = qrTone.symbolsToDeliver;
        qrTone.symbolsCache = Arrays.copyOfRange(symbols, 0, QRTone.HEADER_SYMBOLS);
        qrTone.cachedSymbolsToHeader();
        assertNotNull(qrTone.headerCache);
        assertEquals(IPFS_PAYLOAD.length, qrTone.headerCache.length);
        qrTone.symbolsCache = Arrays.copyOfRange(symbols, QRTone.HEADER_SYMBOLS, symbols.length);
        qrTone.cachedSymbolsToPayload();
        byte[] payloadData = qrTone.getPayload();
        assertNotNull(payloadData);
        assertArrayEquals(IPFS_PAYLOAD, payloadData);
        assertEquals(0, qrTone.getFixedErrors());
    }

    @Test
    public void testEncodeDecodeMessageL() throws ReedSolomonException {
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        byte[] payload = new byte[] {5,6};
        qrTone.setPayload(payload, Configuration.ECC_LEVEL.ECC_L, false);
        byte[] symbols = qrTone.symbolsToDeliver;
        qrTone.symbolsCache = Arrays.copyOfRange(symbols, 0, QRTone.HEADER_SYMBOLS);
        qrTone.cachedSymbolsToHeader();
        assertNotNull(qrTone.headerCache);
        assertEquals(payload.length, qrTone.headerCache.length);
        qrTone.symbolsCache = Arrays.copyOfRange(symbols, QRTone.HEADER_SYMBOLS, symbols.length);
        qrTone.cachedSymbolsToPayload();
        byte[] payloadData = qrTone.getPayload();
        assertNotNull(payloadData);
        assertArrayEquals(payload, payloadData);
        assertEquals(0, qrTone.getFixedErrors());
    }

    @Test
    public void testEncodeDecodeMessageErrorInHeader() throws ReedSolomonException {
        QRTone qrTone = new QRTone(Configuration.getAudible(44100));
        qrTone.setPayload(IPFS_PAYLOAD);
        byte[] symbols = qrTone.symbolsToDeliver;
        qrTone.symbolsCache = Arrays.copyOfRange(symbols, 0, QRTone.HEADER_SYMBOLS);
        qrTone.symbolsCache[1] = 0xC;
        qrTone.cachedSymbolsToHeader();
        assertNotNull(qrTone.headerCache);
        assertEquals(IPFS_PAYLOAD.length, qrTone.headerCache.length);
        qrTone.symbolsCache = Arrays.copyOfRange(symbols, QRTone.HEADER_SYMBOLS, symbols.length);
        qrTone.cachedSymbolsToPayload();
        byte[] payloadData = qrTone.getPayload();
        assertNotNull(payloadData);
        assertArrayEquals(IPFS_PAYLOAD, payloadData);
        assertEquals(1, qrTone.getFixedErrors());
    }

    @Test
    public void testSymbolEncodingDecodingL() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_L;
        byte[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel, false);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel, false, null);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingLCRC() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_L;
        byte[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel, true);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel, true, null);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingM() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_M;
        byte[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel, true);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel, true, null);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingQ() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_Q;
        byte[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel, true);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel, true, null);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingH() throws ReedSolomonException {
        String payloadStr = "Hello world !";
        byte[] payloadBytes = payloadStr.getBytes();
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_H;
        byte[] symbols = QRTone.payloadToSymbols(payloadBytes, eccLevel, true);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel, true, null);
        assertArrayEquals(payloadBytes, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingCRC1() throws ReedSolomonException {
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_Q;
        byte[] symbols = QRTone.payloadToSymbols(IPFS_PAYLOAD, eccLevel, true);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel, true, null);
        assertArrayEquals(IPFS_PAYLOAD, processedBytes);
    }

    @Test
    public void testSymbolEncodingDecodingCRC2() throws ReedSolomonException {
        Configuration.ECC_LEVEL eccLevel = Configuration.ECC_LEVEL.ECC_L;
        byte[] symbols = QRTone.payloadToSymbols(IPFS_PAYLOAD, eccLevel, true);
        // Push error
        symbols[1] = 8;
        AtomicInteger fixedErros = new AtomicInteger(0);
        byte[] processedBytes = QRTone.symbolsToPayload(symbols, eccLevel, true, fixedErros);
        assertArrayEquals(IPFS_PAYLOAD, processedBytes);
        assertEquals(1, fixedErros.get());
    }

    @Test
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
        byte payload[] = {0x00, 0x04, 'n', 'i' , 'c' , 'o', 0x01, 0x05, 'h', 'e', 'l', 'l', 'o' };
        final int dataSampleLength = qrTone.setPayload(payload);
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
        //writeFloatToFile("target/toneSignal.raw", samples);
    }

    @Test
    public void testToneDetection() throws IOException {
        boolean writeCSV = false;
        double sampleRate = 16000;
        double timeBlankBefore = 0.35;
        double timeBlankAfter = 0.35;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        double powerPeak = powerRMS * Math.sqrt(2);
        double noisePeak = Math.pow(10, -50.0 / 20.0); // -26 dBFS
        int samplesBefore = (int)(timeBlankBefore * sampleRate);
        int samplesAfter = (int)(timeBlankAfter * sampleRate);
        Configuration configuration = Configuration.getAudible(sampleRate);
        QRTone qrTone = new QRTone(configuration);
        QRToneCallback csvWriter = new QRToneCallback(qrTone);
        qrTone.setTriggerCallback(csvWriter);
        if(writeCSV) {
            csvWriter.open("target/spectrum.csv");
        }
        final int dataSampleLength = qrTone.setPayload(IPFS_PAYLOAD);
        float[] audio = new float[dataSampleLength];
        float[] samples = new float[samplesBefore + dataSampleLength + samplesAfter];
        qrTone.getSamples(audio, 0, powerPeak);
        System.arraycopy(audio, 0, samples, samplesBefore, dataSampleLength);
        QRTone.generatePitch(samples, 0, samples.length, 0, sampleRate, 125, noisePeak);
        if(writeCSV) {
            writeFloatToFile("target/inputSignal.raw", samples);
        }
        long start = System.currentTimeMillis();
        int cursor = 0;
        while (cursor < samples.length) {
            int windowSize = Math.min(qrTone.getMaximumWindowLength(), samples.length - cursor);
            float[] window = new float[windowSize];
            System.arraycopy(samples, cursor, window, 0, window.length);
            if(qrTone.pushSamples(window)) {
                break;
            }
            cursor += windowSize;
        }
        System.out.println(String.format("Done in %.3f",(System.currentTimeMillis() - start) /1e3));
        if(writeCSV) {
            csvWriter.close();
        }
        assertArrayEquals(IPFS_PAYLOAD, qrTone.getPayload());
        assertEquals(timeBlankBefore, qrTone.gePayloadSampleIndex() / sampleRate, 0.001);
    }

    @Test
    public void testShortToneDetection() throws IOException {
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
        byte[] payload = new byte[] {0x41, 0x33};
        final int dataSampleLength = qrTone.setPayload(payload, Configuration.ECC_LEVEL.ECC_L, false);
        float[] audio = new float[dataSampleLength];
        float[] samples = new float[samplesBefore + dataSampleLength + samplesAfter];
        qrTone.getSamples(audio, 0, powerPeak);
        System.arraycopy(audio, 0, samples, samplesBefore, dataSampleLength);
        Random random = new Random(1337);
        for (int s = 0; s < samples.length; s++) {
            samples[s] += (float)(random.nextGaussian() * noisePeak);
        }
        int cursor = 0;
        List<byte[]> payloads = new ArrayList<>();
        while (cursor < samples.length) {
            int windowSize = Math.min(qrTone.getMaximumWindowLength() ,Math.min(random.nextInt(115) + 20, samples.length - cursor));
            float[] window = new float[windowSize];
            System.arraycopy(samples, cursor, window, 0, window.length);
            if(qrTone.pushSamples(window)) {
                payloads.add(qrTone.getPayload());
            }
            cursor += windowSize;
        }
        assertArrayEquals(payload, payloads.get(0));
    }
    @Test
    public void testInterleave() {
        byte[] data = new byte[] {'a', 'b', 'c', '1', '2', '3', 'd', 'e', 'f', '4', '5', '6', 'g', 'h'};
        byte[] interleavedExpected = new byte[] {'a', '1', 'd', '4', 'g', 'b', '2', 'e', '5', 'h', 'c', '3', 'f', '6'};
        byte[] interleaved = Arrays.copyOf(data, data.length);
        QRTone.interleaveSymbols(interleaved, 3);
        assertArrayEquals(interleavedExpected, interleaved);
        QRTone.deinterleaveSymbols(interleaved, 3);
        assertArrayEquals(data, interleaved);
    }


    @Test
    public void testToneDetectionWithNoise() throws IOException, UnsupportedAudioFileException {
        boolean writeCSV = false;
        double sampleRate = 44100;
        double timeBlankBefore = 1.1333;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        double powerPeak = powerRMS * Math.sqrt(2);
        int samplesBefore = (int)(timeBlankBefore * sampleRate);
        Configuration configuration = Configuration.getAudible(sampleRate);
        QRTone qrTone = new QRTone(configuration);
        QRToneCallback csvWriter = new QRToneCallback(qrTone);
        if(writeCSV) {
            csvWriter.open("target/spectrum.csv");
            qrTone.setTriggerCallback(csvWriter);
        }
        final int dataSampleLength = qrTone.setPayload(IPFS_PAYLOAD);
        int numberOfSymbols = qrTone.symbolsToDeliver.length;
        float[] audio = new float[dataSampleLength];
        float[] samples;
        try(InputStream fileInputStream = QRToneTest.class.getResourceAsStream("noisy_10sec_44100_16bitsPCMMono.raw")) {
            samples = loadShortStream(fileInputStream, ByteOrder.LITTLE_ENDIAN);
        }
        qrTone.getSamples(audio, 0, powerPeak);
        // Add audio effects
        AudioDispatcher d = AudioDispatcherFactory.fromFloatArray(audio, (int)sampleRate, 1024, 0);
        d.addAudioProcessor(new DelayEffect(0.04, 0.1, sampleRate));
        d.addAudioProcessor(new LowPassFS((float)qrTone.getFrequencies()[QRTone.FREQUENCY_ROOT], (float)sampleRate));
        d.addAudioProcessor(new GainProcessor(Math.pow(10, -1 / 20.0)));
        ArrayWriteProcessor writer = new ArrayWriteProcessor((int)sampleRate);
        d.addAudioProcessor(writer);
        d.run();
        audio = writer.getData();
        for(int i = 0; i < audio.length; i++) {
            samples[i+samplesBefore] += audio[i];
        }
        // writeFloatToFile("target/testToneDetectionWithNoise.raw", samples);
        long start = System.currentTimeMillis();
        int cursor = 0;
        Random random = new Random(QRTone.PERMUTATION_SEED);
        try {
            while (cursor < samples.length) {
                int windowSize = Math.min(random.nextInt(115) + 20, samples.length - cursor);
                float[] window = new float[windowSize];
                System.arraycopy(samples, cursor, window, 0, window.length);
                if (qrTone.pushSamples(window)) {
                    break;
                }
                cursor += windowSize;
            }
            System.out.println(String.format("Done in %.3f", (System.currentTimeMillis() - start) / 1e3));
        } finally {
            if(writeCSV) {
                csvWriter.close();
            }
        }
        assertArrayEquals(IPFS_PAYLOAD, qrTone.getPayload());
        System.out.println(qrTone.getFixedErrors()+" errors have been fixed on "+numberOfSymbols+" symbols");
    }

    static class QRToneCallback implements TriggerAnalyzer.TriggerCallback {
        double[] frequencies;
        Writer writer = null;
        QRTone qrTone;

        public QRToneCallback(QRTone qrTone) {
            this.qrTone = qrTone;
        }

        public void open(String path) throws FileNotFoundException {
            FileOutputStream fos = new FileOutputStream(path);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            writer = new OutputStreamWriter(bos);
        }

        @Override
        public void onTrigger(TriggerAnalyzer triggerAnalyzer, long messageStartLocation) {
            long firstTone = messageStartLocation;
            System.out.println(String.format(Locale.ROOT, "Found trigger at %.3f",firstTone / triggerAnalyzer.sampleRate));
        }

        @Override
        public void onNewLevels(TriggerAnalyzer triggerAnalyzer, long location, double[] spl) {
            long realLocation = location;
            if(writer != null) {
                try {
                    if (frequencies == null) {
                        frequencies = triggerAnalyzer.frequencies;
                        writer.write("t");
                        for (double frequency : frequencies) {
                            writer.write(String.format(Locale.ROOT, ",%.0f Hz (L)", frequency));
                            writer.write(String.format(Locale.ROOT, ",%.0f Hz (L50+15)", frequency));
                        }
                        writer.write("\n");
                    }
                    writer.write(String.format(Locale.ROOT, "%.3f", realLocation / triggerAnalyzer.sampleRate));
                    for (int idFreq = 0; idFreq < spl.length; idFreq++) {
                        writer.write(String.format(Locale.ROOT, ",%.2f", spl[idFreq]));
                        writer.write(String.format(Locale.ROOT, ",%.2f", triggerAnalyzer.backgroundNoiseEvaluator.result() + 15));
                    }
                    writer.write("\n");
                } catch (IOException ex) {
                    System.err.println(ex.getLocalizedMessage());
                }
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

    @Test
    public void testSymbolsEncodeDecode() throws ReedSolomonException {
        byte payload[] = {0x00, 0x04, 'n', 'i' , 'c' , 'o', 0x01, 0x05, 'h', 'e', 'l', 'l', 'o' };
        byte[] expectedSymbols = {0, 0, 6, 0, 1, 15, 0, 0, 8, 4, 5, 12, 6, 6, 13, 14, 8, 0, 6, 6, 6, 9, 5, 14, 6, 6, 3, 12, 6, 6, 15, 12, 2, 9, 6, 7};
        byte[] symbols = QRTone.payloadToSymbols(payload, Configuration.ECC_LEVEL.ECC_L, true);

        assertArrayEquals(expectedSymbols, symbols);

        byte[] decodedPayload = QRTone.symbolsToPayload(symbols, Configuration.ECC_LEVEL.ECC_L, true, null);

        assertArrayEquals(payload, decodedPayload);
    }

    @Test
    public void testToneDetectionArduino() throws IOException, UnsupportedAudioFileException {
        boolean writeCSV = true;
        double sampleRate = 16000;
        Configuration configuration = Configuration.getAudible(sampleRate);
        QRTone qrTone = new QRTone(configuration);
        qrTone.setPayload(IPFS_PAYLOAD);
        QRToneCallback csvWriter = new QRToneCallback(qrTone);
        if(writeCSV) {
            csvWriter.open("target/spectrum.csv");
            qrTone.setTriggerCallback(csvWriter);
        }
        float[] samples;
        try(InputStream fileInputStream = QRToneTest.class.getResourceAsStream("ipfs_16khz_16bits_mono.raw")) {
            samples = loadShortStream(fileInputStream, ByteOrder.LITTLE_ENDIAN);
        }
        //writeFloatToFile("target/inputSignal.raw", samples);
        long start = System.currentTimeMillis();
        int cursor = 0;
        try {
            while (cursor < samples.length) {
                int windowSize = Math.min(128, samples.length - cursor);
                float[] window = new float[windowSize];
                System.arraycopy(samples, cursor, window, 0, window.length);
                if (qrTone.pushSamples(window)) {
                    break;
                }
                cursor += windowSize;
            }
            System.out.println(String.format("Done in %.3f", (System.currentTimeMillis() - start) / 1e3));
        } finally {
            if(writeCSV) {
                csvWriter.close();
            }
        }
        assertArrayEquals(IPFS_PAYLOAD, qrTone.getPayload());
        System.out.println(qrTone.getFixedErrors()+" errors have been fixed");
    }




    // Test adaptative geortzel window
    // @Test
    public void generalized_goertzel_width() throws Exception {
        double sampleRate = 32000;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        long totalTime = 0;
        double powerPeak = powerRMS * Math.sqrt(2);
        Configuration c = Configuration.getAudible(sampleRate);
        double[] freqs = c.computeFrequencies(32, 0);
        double[] freqsLimits = c.computeFrequencies(32, QRTone.WINDOW_WIDTH);
        double[] testfrequencies = new double[512];
        double limit = -32 - 15; //20 * Math.log10(powerRMS) - 15.0;
        for(int i = 0; i < testfrequencies.length; i++) {
            testfrequencies[i] = 1500 + (8000 - 1500) * ((double)i / testfrequencies.length);
        }
        double[][] columns = new double[freqs.length][];
        for(int freq_Index = 0; freq_Index < freqs.length; freq_Index++) {
            columns[freq_Index] = new double[testfrequencies.length];
            double signalFrequency = freqs[freq_Index];
            double closestFrequency = freqsLimits[freq_Index];
            int window_length = Configuration.computeMinimumWindowSize(sampleRate, signalFrequency, closestFrequency);
            System.out.println(String.format("Window length is %d, analyzed frequency is %f, closest frequency is %f", window_length, signalFrequency, closestFrequency));
            double last_limit = Double.NEGATIVE_INFINITY;
            int idrow = 0;
            for (double testFrequency : testfrequencies) {
                float[] audio = new float[window_length];
                for (int s = 0; s < audio.length; s++) {
                    double t = s * (1 / sampleRate);
                    audio[s] = (float) (Math.cos(QRTone.M2PI * testFrequency * t) * (powerPeak));
                }
                QRTone.applyHann(audio, 0, audio.length, audio.length, 0);
                IterativeGeneralizedGoertzel.GoertzelResult res = new IterativeGeneralizedGoertzel(sampleRate, signalFrequency,
                        audio.length, false).processSamples(audio, 0, audio.length).computeRMS(true);
                double new_limit = 20 * Math.log10(res.rms) - limit;
                if (last_limit < 0 && new_limit > 0 || last_limit > 0 && new_limit < 0) {
                    System.out.println(testFrequency);
                }
                last_limit = new_limit;
                columns[freq_Index][idrow++] = 20 * Math.log10(res.rms);
            }
        }
        try (FileWriter fileWriter = new FileWriter("target/goertzel_test.csv")) {
            fileWriter.write("frequency, limit");
            for (double freq : freqs) {
                fileWriter.write(String.format(Locale.ROOT, ", %.0f Hz", freq));
            }
            fileWriter.write("\n");
            for(int idrow = 0; idrow < testfrequencies.length; idrow++) {
                fileWriter.write(String.format(Locale.ROOT, "%.0f", testfrequencies[idrow]));
                fileWriter.write(String.format(Locale.ROOT, ",%.0f", limit));
                for(int idfreq = 0; idfreq < freqs.length; idfreq++) {
                    fileWriter.write(String.format(Locale.ROOT, ", %.2f", columns[idfreq][idrow]));
                }
                fileWriter.write("\n");
            }
        }
    }
}