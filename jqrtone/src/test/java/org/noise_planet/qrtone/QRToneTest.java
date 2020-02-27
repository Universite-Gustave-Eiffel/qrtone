package org.noise_planet.qrtone;

import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class QRToneTest {

    @Test
    public void crcTest() {
        byte[] expectedPayload = new byte[]{18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        byte base = QRTone.crc8(expectedPayload, 0, expectedPayload.length);
        AtomicLong next = new AtomicLong(1337);
        for(int i=0; i < expectedPayload.length; i++) {
            byte[] alteredPayload = Arrays.copyOf(expectedPayload, expectedPayload.length);
            alteredPayload[i] = (byte) (QRTone.warbleRand(next) % 255);
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
            //float[] window = new float[windowSize];
            //System.arraycopy(audio, cursor, window, 0, window.length);
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
            maxValue = Math.max(maxValue, aSignal);
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
        QRTone.applyTukey(tone, 0, tone.length, 0.15, tone.length, 0);
        for(int i=0; i < tone.length; i++) {
            signal[location+i] += tone[i];
        }
    }


    @Test
    public void testToneDetection() throws IOException {
        double sampleRate = 44100;
        double powerRMS = Math.pow(10, -26.0 / 20.0); // -26 dBFS
        double powerPeak = powerRMS * Math.sqrt(2);
        double noisePeak = powerPeak; // -26 dBFS
        Configuration configuration = Configuration.getAudible(sampleRate);
        QRTone qrTone = new QRTone(configuration);
        double[] frequencies = configuration.computeFrequencies(QRTone.NUM_FREQUENCIES);

        float[] audio = new float[(int)(sampleRate * 4.0)];
        Random random = new Random(1337);
        for (int s = 0; s < audio.length; s++) {
            audio[s] = (float)(random.nextGaussian() * noisePeak);
        }
        int toneDuration = (int)(configuration.wordTime * sampleRate);
        int toneLocation = audio.length / 2 - toneDuration / 2;

        int checkFrequency = 0;
        pushTone(audio, toneLocation, frequencies[checkFrequency], qrTone, powerPeak);
        pushTone(audio, toneLocation + 3 * toneDuration, frequencies[checkFrequency], qrTone, powerPeak);
        pushTone(audio, toneLocation + 4 * toneDuration, frequencies[checkFrequency], qrTone, powerPeak);
        pushTone(audio, toneLocation + toneDuration, frequencies[checkFrequency + 1], qrTone, powerPeak);
        pushTone(audio, toneLocation + 2 * toneDuration, frequencies[checkFrequency + 2], qrTone, powerPeak);

        writeFloatToFile("target/inputSignal.raw", Arrays.copyOfRange(audio,toneLocation - toneDuration * 3, toneLocation + toneDuration * 8));
        long start = System.currentTimeMillis();
        int cursor = 0;
        while (cursor < audio.length) {
            int windowSize = Math.min(random.nextInt(115) + 20, audio.length - cursor);
            float[] window = new float[windowSize];
            System.arraycopy(audio, cursor, window, 0, window.length);
            qrTone.pushSamples(window);
            cursor += windowSize;
        }
        System.out.println(String.format("Done in %.3f",(System.currentTimeMillis() - start) /1e3));
        qrTone.writeCSV("target/spectrum.csv");
    }
}