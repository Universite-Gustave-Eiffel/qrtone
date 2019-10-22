package org.noise_planet.jwarble;

import org.junit.Assert;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class OpenWarbleTest {

    @Test
    public void generalized_goertzel() throws Exception {
        double sampleRate = 44100;
        double powerRMS = 500; // 90 dBspl
        float signalFrequency = 1000;
        double powerPeak = powerRMS * Math.sqrt(2);

        double[] audio = new double[4410];
        for (int s = 0; s < audio.length; s++) {
            double t = s * (1 / sampleRate);
            audio[s] = Math.sin(OpenWarble.M2PI * signalFrequency * t) * (powerPeak);
        }

        double[] rms = OpenWarble.generalized_goertzel(audio,0, audio.length, sampleRate, new double[]{1000.0});

        double signal_rms = OpenWarble.compute_rms(audio);

        Assert.assertEquals(signal_rms, rms[0], 0.1);
    }


    private static void writeShortToFile(String path, short[] signal) throws IOException {
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

    private static void writeDoubleToFile(String path, double[] signal) throws IOException {
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

    public static short[] convertBytesToShort(byte[] buffer, int length, ByteOrder byteOrder) {
        ShortBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, length).order(byteOrder).asShortBuffer();
        short[] samplesShort = new short[byteBuffer.capacity()];
        byteBuffer.order();
        byteBuffer.get(samplesShort);
        return samplesShort;
    }

    public static short[] loadShortStream(InputStream inputStream, ByteOrder byteOrder) throws IOException {
        short[] fullArray = new short[0];
        byte[] buffer = new byte[4096];
        int read;
        // Read input signal up to buffer.length
        while ((read = inputStream.read(buffer)) != -1) {
            // Convert bytes into double values. Samples array size is 8 times inferior than buffer size
            if (read < buffer.length) {
                buffer = Arrays.copyOfRange(buffer, 0, read);
            }
            short[] signal = convertBytesToShort(buffer, buffer.length, byteOrder);
            short[] nextFullArray = new short[fullArray.length + signal.length];
            if(fullArray.length > 0) {
                System.arraycopy(fullArray, 0, nextFullArray, 0, fullArray.length);
            }
            System.arraycopy(signal, 0, nextFullArray, fullArray.length, signal.length);
            fullArray = nextFullArray;
        }
        return fullArray;
    }

    @Test
    public void testRecognitionWithoutNoise() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate));
        UtCallback utCallback = new UtCallback(false);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        double[] signal = openWarble.generate_signal(powerPeak, payload);
        double[] allSignal = new double[blankSamples+signal.length+blankSamples];
        System.arraycopy(signal, 0, allSignal, blankSamples, signal.length);
        int cursor = 0;
        while (cursor < allSignal.length) {
            int len = Math.min(openWarble.getMaxPushSamplesLength(), allSignal.length - cursor);
            if(len == 0) {
                break;
            }
            openWarble.pushSamples(Arrays.copyOfRange(allSignal, cursor, cursor+len));
            cursor+=len;
        }
        assertTrue(Math.abs(blankSamples - messageCallback.pitchLocation) < openWarble.door_length / 4.0);
        assertArrayEquals(payload, messageCallback.payload);
        assertEquals(0, openWarble.getCorrectedErrors());
        //writeDoubleToFile("target/source.raw", allSignal);
    }


    @Test
    public void testRecognitionWithNoise() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double noisePeak = 0.1;
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate));
        UtCallback utCallback = new UtCallback(false);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        double[] signal = openWarble.generate_signal(powerPeak, payload);
        double[] allSignal = new double[blankSamples+signal.length+blankSamples];
        System.arraycopy(signal, 0, allSignal, blankSamples, signal.length);
        // Average with noise
        Random rand = new Random(1337);
        for(int i = 0; i < allSignal.length; i++) {
            allSignal[i] = (allSignal[i] + rand.nextGaussian() * noisePeak) / 2.0;
        }
        long start = System.nanoTime();
        int cursor = 0;
        while (cursor < allSignal.length) {
            int len = Math.min(openWarble.getMaxPushSamplesLength(), allSignal.length - cursor);
            if (len == 0) {
                break;
            }
            openWarble.pushSamples(Arrays.copyOfRange(allSignal, cursor, cursor + len));
            cursor += len;
        }
        System.out.println(String.format("Execution time %.3f seconds", (System.nanoTime() - start) / 1e9));
        //writeDoubleToFile("target/test.raw", allSignal);
        assertTrue(Math.abs(blankSamples - messageCallback.pitchLocation) < openWarble.door_length / 4.0);
        assertArrayEquals(payload, messageCallback.payload);
        assertEquals(0, openWarble.getCorrectedErrors());
    }


    @Test
    public void testWithRecordedAudio() throws IOException {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        UtCallback utCallback = new UtCallback(false);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        short[] signal_short;
        try (InputStream inputStream = OpenWarbleTest.class.getResourceAsStream("with_noise_44100hz_mono_16bits.raw")) {
            signal_short = loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
        }
        // Push audio samples to OpenWarble
        int cursor = 0;
        while (cursor < signal_short.length) {
            int len = Math.min(openWarble.getMaxPushSamplesLength(), signal_short.length - cursor);
            if(len == 0) {
                break;
            }
            double[] window = new double[len];
            for(int i = cursor; i < cursor + len; i++) {
                window[i - cursor] = signal_short[i] / (double)Short.MAX_VALUE;
            }
            openWarble.pushSamples(window);
            cursor+=len;
        }
        assertArrayEquals(expectedPayload, messageCallback.payload);
        assertEquals(0, openWarble.getCorrectedErrors());
    }

    private static class UtMessageCallback implements MessageCallback {
        public long pitchLocation = -1;
        public byte[] payload;

        @Override
        public void onNewMessage(byte[] payload, long sampleId) {
            this.payload = payload;
        }

        @Override
        public void onPitch(long sampleId) {
            if(pitchLocation < 0) {
                pitchLocation = sampleId;
            }
        }

        @Override
        public void onError(long sampleId) {

        }
    }

    private static class UtCallback implements OpenWarble.UnitTestCallback {

        boolean print;

        public UtCallback(boolean print) {
            this.print = print;
        }

        @Override
        public void generateWord(byte word, int encodedWord, boolean[] frequencies) {
            if(print) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < frequencies.length; i++) {
                    if (frequencies[i]) {
                        if (sb.length() != 0) {
                            sb.append(", ");
                        }
                        sb.append(i);
                    }
                }
                System.out.println(String.format("New word %02x %s", word, sb.toString()));
            }
        }

        @Override
        public void detectWord(byte word, int encodedWord, boolean[] frequencies) {
            if(print) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < frequencies.length; i++) {
                    if (frequencies[i]) {
                        if (sb.length() != 0) {
                            sb.append(", ");
                        }
                        sb.append(i);
                    }
                }
                System.out.println(String.format("Find word %02x %s", word, sb.toString()));
            }
        }
    }
}