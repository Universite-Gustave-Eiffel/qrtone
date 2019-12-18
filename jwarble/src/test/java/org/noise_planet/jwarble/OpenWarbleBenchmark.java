package org.noise_planet.jwarble;

import org.junit.Test;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class OpenWarbleBenchmark {

    @Test
    public void runAudioMicLoop () throws Exception {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate, true));
        OpenWarbleTest.UtCallback utCallback = new OpenWarbleTest.UtCallback(false, openWarble);
        OpenWarbleTest.UtMessageCallback messageCallback = new OpenWarbleTest.UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        double[] signal = openWarble.generateSignal(powerPeak, payload);
        short[] shortSignal = new short[signal.length];
        double maxValue = Double.MIN_VALUE;
        for (double aSignal : signal) {
            maxValue = Math.max(maxValue, Math.abs(aSignal));
        }
        maxValue *= 8;
        for(int i=0; i<signal.length;i++) {
            shortSignal[i] = (short)((signal[i] / maxValue) * Short.MAX_VALUE);
        }
        AtomicBoolean doRecord = new AtomicBoolean(true);
        AtomicBoolean micOpen = new AtomicBoolean(false);
        try {
            FutureTask<double[]> recordTask = new FutureTask<>(new Recorder(doRecord, micOpen));
            FutureTask<Boolean> playerTask = new FutureTask<>(new Player(shortSignal, sampleRate));
            ExecutorService executorService = Executors.newFixedThreadPool(2);
            executorService.submit(recordTask);
            while(!micOpen.get()) {
                Thread.sleep(120);
            }
            Thread.sleep(2500);
            executorService.submit(playerTask);
            playerTask.get(Math.round((shortSignal.length / sampleRate) * 2000), TimeUnit.MILLISECONDS);
            Thread.sleep(2500);
            doRecord.set(false);
            double[] samples = recordTask.get(5000, TimeUnit.MILLISECONDS);
            assertNotNull(samples);
            OpenWarbleTest.writeDoubleToFile("target/recorded.raw", samples);
            int cursor = 0;
            while (cursor < samples.length) {
                int len = Math.min(openWarble.getMaxPushSamplesLength(), samples.length - cursor);
                if(len == 0) {
                    break;
                }
                openWarble.pushSamples(Arrays.copyOfRange(samples, cursor, cursor+len));
                cursor+=len;
            }
            assertArrayEquals(payload, messageCallback.payload);
        } finally {
            doRecord.set(false);
        }
    }

    @Test
    public void testWithRecordedAudio() throws IOException {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4,
                31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        OpenWarbleTest.UtCallback utCallback = new OpenWarbleTest.UtCallback(true, openWarble);
        OpenWarbleTest.UtMessageCallback messageCallback = new OpenWarbleTest.UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        openWarble.generateSignal(1.0, expectedPayload);
        System.out.println("\nGot");
        short[] signal_short;
        try (InputStream inputStream = new FileInputStream("target/recorded.raw")) {
            signal_short = OpenWarbleTest.loadShortStream(inputStream, ByteOrder.BIG_ENDIAN);
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
                window[i - cursor] = signal_short[i];
            }
            openWarble.pushSamples(window);
            cursor+=len;
        }
        assertArrayEquals(expectedPayload, messageCallback.payload);
    }

    @Test
    public void testWithRecordedAudioMethod() throws IOException {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4,
                31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        short[] signal_short;
        try (InputStream inputStream = new FileInputStream("target/recorded.raw")) {
            signal_short = OpenWarbleTest.loadShortStream(inputStream, ByteOrder.BIG_ENDIAN);
        }
        // Push audio samples to OpenWarble
        //openWarble.triggerSampleIndexBegin = 121800;
        Percentile background = new Percentile(80);
        int windowLength = (openWarble.wordLength / 2) / 3;
        int windowOffset = windowLength / 3;
        double[] window = new double[windowLength];
        Percentile denoise = new Percentile((openWarble.wordLength / 2) / windowOffset);
        Percentile denoise2 = new Percentile((openWarble.wordLength / 2) / windowOffset);
        int cursor = 0;
        while (cursor < (int)(3.5 * sampleRate)) {
            int len = Math.min(windowOffset, signal_short.length - cursor);
            if(len == 0) {
                break;
            }
            System.arraycopy(window, len, window, 0, window.length - len);
            for(int i = cursor; i < cursor + len; i++) {
                window[window.length - len + (i - cursor)] = signal_short[i];
            }
            double[] rms = OpenWarble.generalizedGoertzel(window, 0, window.length, sampleRate, new double[]{openWarble.frequencyDoor1, openWarble.frequencies[openWarble.frequencies.length - 1]}, null, false);
            background.add(rms[0]);
            denoise.add(rms[0]);
            denoise2.add(rms[1]);
            if(cursor > (int)(2.4 * sampleRate)) {
                System.out.println(String.format(Locale.ROOT, "%.3f,%.3f,%.3f", cursor / sampleRate, 20 * Math.log10(denoise.getPercentile(0.9) + 1), 20 * Math.log10(denoise2.getPercentile(0.9))));
            }
            cursor+=len;
        }
    }

    public static class Player implements Callable<Boolean> {
        short[] samples;
        double sampleRate;

        public Player(short[] samples, double sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }

        @Override
        public Boolean call() throws Exception {

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(bos);
            for (short sample : samples) {
                dataOutputStream.writeShort(sample);
            }
            InputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
            System.out.println("started");
            AudioFormat format = new AudioFormat((float)sampleRate, Short.SIZE, 1, true, true);
            AudioInputStream audioInputStream = new AudioInputStream(byteArrayInputStream, format, samples.length);
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            sourceDataLine.open(format);
            sourceDataLine.start();
            int cnt = 0;
            byte tempBuffer[] = new byte[10000];
            long start = System.currentTimeMillis();
            try {
                System.out.println(String.format("Input length %.2fs", samples.length / sampleRate));
                while (!sourceDataLine.isActive()) {
                    System.out.println("Not ready");
                    Thread.sleep(125);
                }
                while ((cnt = audioInputStream.read(tempBuffer, 0,tempBuffer.length)) != -1) {
                    if (cnt > 0) {
                        // Write data to the internal buffer of
                        // the data line where it will be
                        // delivered to the speaker.
                        sourceDataLine.write(tempBuffer, 0, cnt);
                    }// end if
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Block and wait for internal buffer of the
            // data line to empty.
            sourceDataLine.drain();
            sourceDataLine.close();
            return true;
        }
    }

    public static class Recorder implements Callable<double[]> {
        AtomicBoolean run;
        AtomicBoolean micOpen;

        public Recorder(AtomicBoolean run, AtomicBoolean micOpen) {
            this.run = run;
            this.micOpen = micOpen;
        }

        @Override
        public double[] call() throws Exception {
            System.out.println("Open Microphone");

            AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, true);
            TargetDataLine microphone;
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int numBytesRead;
                int CHUNK_SIZE = format.getFrameSize() * 125;
                byte[] data = new byte[CHUNK_SIZE];
                microphone.start();
                micOpen.set(true);
                int bytesRead = 0;
                try {
                    while (run.get()) {
                        while(microphone.available() < CHUNK_SIZE) {
                            Thread.sleep(125);
                        }
                        numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
                        bytesRead = bytesRead + numBytesRead;
                        out.write(data, 0, numBytesRead);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                byte[] audioData = out.toByteArray();
                double[] audioOut = new double[audioData.length / (Short.SIZE / Byte.SIZE)];
                DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(audioData));
                int cursor = 0;
                while(cursor < audioOut.length) {
                    short sample = dataInputStream.readShort();
                    audioOut[cursor++] = sample / (double)Short.MAX_VALUE;
                }
                return audioOut;
            } catch (LineUnavailableException ex) {
                // Ignore
            }
            return new double[0];
        }
    }
}
