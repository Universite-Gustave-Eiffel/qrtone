package org.noise_planet.jwarble;

import org.jtransforms.fft.DoubleFFT_1D;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

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

        double[] rms = OpenWarble.generalized_goertzel(audio, sampleRate, new double[]{1000.0});

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
        writeToFile("jwarble/target/test.raw", shortSignal);
    }

    @Test
    public void testConvolution() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        System.out.println("Chirp location :"+blankSamples);
        byte[] payload = "correlation".getBytes();
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate));
        double[] signal = openWarble.generate_signal(powerPeak, payload);
        double[] allSignal = new double[blankSamples+signal.length];
        System.arraycopy(signal, 0, allSignal, blankSamples, signal.length);
        UtCallback utCallback = new UtCallback();
        openWarble.setUnitTestCallback(utCallback);
        int cursor = 0;
        while (cursor < allSignal.length) {
            int len = Math.min(openWarble.getMaxPushSamplesLength(), allSignal.length - cursor);
            if(len == 0) {
                break;
            }
            openWarble.pushSamples(Arrays.copyOfRange(allSignal, cursor, cursor+len));
            cursor+=len;
        }
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
        double[] in2 = new double[OpenWarble.nextFastSize(allSignal.length + chirp.length - 1) * 2];
        double[] in1 = new double[in2.length];
        System.arraycopy(allSignal, 0, in1, 0, allSignal.length);
        System.arraycopy(chirp, 0, in2, 0, chirp.length);
        OpenWarble.reverse(in2, chirp.length);
        DoubleFFT_1D fftTool = new DoubleFFT_1D(in1.length / 2);
        fftTool.realForwardFull(in2);
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
    }



    private static class UtCallback implements OpenWarble.UnitTestCallback {
        public double[] convResult;
        @Override
        public void onConvolution(double[] convolutionResult) {
            convResult = convolutionResult;
        }
    }
}