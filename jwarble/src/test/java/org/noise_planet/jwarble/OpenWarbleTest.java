package org.noise_planet.jwarble;

import org.jtransforms.fft.DoubleFFT_1D;
import org.jtransforms.utils.CommonUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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


    private static void writeToFile(String path, short[] signal) throws IOException {
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



    private static void writeToFile(String path, double[] signal) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(path);
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(Double.SIZE / Byte.SIZE);
            for(int i = 0; i < signal.length; i++) {
                byteBuffer.putDouble(0, signal[i]);
                fileOutputStream.write(byteBuffer.array());
            }
        } finally {
            fileOutputStream.close();
        }
    }

    public void signalWrite() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate));
        double[] signal = openWarble.generate_signal(powerPeak, payload);
        short[] shortSignal = new short[signal.length];
        double maxValue = Double.MIN_VALUE;
        for (double aSignal : signal) {
            maxValue = Math.max(maxValue, aSignal);
        }
        maxValue *= 2;
        for(int i=0; i<signal.length;i++) {
            shortSignal[i] = (short)((signal[i] / maxValue) * Short.MAX_VALUE);
        }
        writeToFile("target/test.raw", shortSignal);
    }

    @Test
    public void testConvolution() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        String expectedPayload = "correlation";
        byte[] payload = expectedPayload.getBytes();
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
        assertEquals(blankSamples, messageCallback.pitchLocation);
        assertEquals(expectedPayload, new String(messageCallback.payload));
    }


    @Test
    public void testWithBackgroundNoise() throws IOException {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        UtCallback utCallback = new UtCallback(true);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        InputStream inputStream = OpenWarbleTest.class.getResourceAsStream("with_noise_44100hz_mono_16bits.raw");
        short[] signal_short = loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);

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
        int totsize = 0;
        for(double[] res : utCallback.convResults) {
            totsize += res.length;
        }
        double[] values = new double[totsize];
        cursor = 0;
        for(double[] res : utCallback.convResults) {
            System.arraycopy(res, 0, values, cursor, res.length);
            cursor += res.length;
        }
        assertEquals(23870, messageCallback.pitchLocation);
        writeToFile("target/convolve.dat", values);
    }


    @Test
    public void testPureConvolution() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        byte[] payload = "correlation".getBytes();
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate));
        double[] signal = openWarble.generate_signal(powerPeak, payload);
        double[] allSignal = new double[blankSamples+signal.length];
        System.arraycopy(signal, 0, allSignal, blankSamples, signal.length);
        double[] chirp = new double[openWarble.getChirp_length()];
        OpenWarble.generate_chirp(chirp, 0, chirp.length, sampleRate, openWarble.frequencies[0], openWarble.frequencies[openWarble.frequencies.length - 1], 1);
        int realSize = allSignal.length + chirp.length - 1;
        double[] in2 = new double[CommonUtils.nextPow2(allSignal.length + chirp.length - 1) * 2];
        double[] in1 = new double[in2.length];
        System.arraycopy(allSignal, 0, in1, 0, allSignal.length);
        System.arraycopy(chirp, 0, in2, 0, chirp.length);
        OpenWarble.reverse(in2, chirp.length);
        DoubleFFT_1D fftTool = new DoubleFFT_1D(in1.length / 2);
        fftTool.realForwardFull(in2);
        long start = System.currentTimeMillis();
        fftTool.realForwardFull(in1);
        for(int i = 0; i < in1.length / 2; i++) {
            OpenWarble.Complex c1 = new OpenWarble.Complex(in1[i * 2], in1[i * 2 + 1]);
            OpenWarble.Complex c2 = new OpenWarble.Complex(in2[i * 2], in2[i * 2 + 1]);
            OpenWarble.Complex cc = c1.mul(c2);
            in1[i * 2] = cc.r;
            in1[i * 2 + 1] = cc.i;
        }
        fftTool.complexInverse(in1, true);
        int startIndex = (realSize - allSignal.length);
        double[] result = new double[allSignal.length];
        double maxValue = Double.MIN_VALUE;
        int maxIndex = -1;
        for(int i=0; i < result.length; i++) {
            result[i] = in1[startIndex + i * 2];
            if(result[i] > maxValue) {
                maxValue = result[i];
                maxIndex = i;
            }
        }
        Assert.assertEquals(blankSamples, maxIndex - openWarble.chirp_length / 2);
        // Find peaks
        int avg = 5;
        double oldWeightedAvg = 0;
        boolean increase = false;
        double[] weighteddata = new double[result.length];
        double[] peaks = new double[result.length];
        for(int i = Math.max(0, maxIndex - openWarble.chirp_length / 2); i < Math.min(maxIndex + openWarble.chirp_length / 2, result.length); i++) {
            double weightedAvg = result[i];
            //for(int iavg = 0; iavg < avg; iavg++) {
            //    weightedAvg += result[i-iavg];
            //}
            //weightedAvg /= avg;
            double value = weightedAvg - oldWeightedAvg;
            weighteddata[i] = weightedAvg;
            if(result[i] > 0 && ((value > 0  && !increase) || (value < 0 && increase))) {
                // Slope change
                //System.out.println("Slope change at " + i);
                peaks[i - 1] = 400;
            }
            increase = value > 0;
            oldWeightedAvg = weightedAvg;
        }
        System.out.println(String.format("Done in %d", System.currentTimeMillis() - start));
        //writeToFile("/home/nicolas/ownCloud/ifsttar/documents/projets/noisecapture/android/openwarble/intercorrelatetest/weighteddata.raw", weighteddata);
        //writeToFile("/home/nicolas/ownCloud/ifsttar/documents/projets/noisecapture/android/openwarble/intercorrelatetest/peaks.raw", peaks);
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
        public List<double[]> convResults = new ArrayList<>();
        boolean print;

        public UtCallback(boolean print) {
            this.print = print;
        }

        @Override
        public void onConvolution(double[] convolutionResult) {
            convResults.add(convolutionResult.clone());
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
                System.out.println(String.format("New word %d %s", encodedWord, sb.toString()));
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
                System.out.println(String.format("Find word %d %s", encodedWord, sb.toString()));
            }
        }
    }
}