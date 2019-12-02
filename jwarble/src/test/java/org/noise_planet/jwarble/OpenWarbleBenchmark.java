package org.noise_planet.jwarble;

import org.junit.Test;

import javax.sound.sampled.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class OpenWarbleBenchmark {

    @Test
    public void runAudioMicLoop () throws Exception {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate, true));
        UtCallback utCallback = new UtCallback(false);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        double[] signal = openWarble.generateSignal(powerPeak, payload);
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
        short[] shortSignal = new short[allSignal.length];
        double maxValue = Double.MIN_VALUE;
        for (double aSignal : allSignal) {
            maxValue = Math.max(maxValue, aSignal);
        }
        maxValue *= 2;
        for(int i=0; i<allSignal.length;i++) {
            shortSignal[i] = (short)((allSignal[i] / maxValue) * Short.MAX_VALUE);
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
            executorService.submit(playerTask);
            System.out.println(String.format("Wait for %.2f", shortSignal.length / sampleRate));
            playerTask.get(Math.round((shortSignal.length / sampleRate) * 2000), TimeUnit.MILLISECONDS);
            Thread.sleep(1000);
            doRecord.set(false);
            double[] samples = recordTask.get(5000, TimeUnit.MILLISECONDS);
            if(samples != null) {
                OpenWarbleTest.writeDoubleToFile("target/recorded.raw", samples);
                // TODO DECODE
            }
        } finally {
            doRecord.set(false);
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

            ByteArrayOutputStream bos = new ByteArrayOutputStream(samples.length * (Short.SIZE / Byte.SIZE));
            DataOutputStream dataOutputStream = new DataOutputStream(bos);
            for (short sample : samples) {
                dataOutputStream.writeShort(sample);
                dataOutputStream.writeShort(0);
            }
            InputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
            System.out.println("started");
            AudioFormat format = new AudioFormat((float)sampleRate, 16, 1, true, true);
            AudioInputStream audioInputStream = new AudioInputStream(byteArrayInputStream, format, samples.length / format.getFrameSize());
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            sourceDataLine.open(format);
            sourceDataLine.start();
            int cnt = 0;
            byte tempBuffer[] = new byte[10000];
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
        public void detectWord(Hamming12_8.CorrectResult result, int encodedWord, boolean[] frequencies) {
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
                if (result.result == Hamming12_8.CorrectResultCode.CORRECTED_ERROR) {
                    int code = Hamming12_8.encode(result.value);
                    StringBuilder wrongFrequencies = new StringBuilder();
                    for(int idfreq = 0; idfreq < OpenWarble.NUM_FREQUENCIES; idfreq++) {
                        if ((code & (1 << idfreq)) != 0 && !frequencies[idfreq]) {
                            wrongFrequencies.append(idfreq);
                        } else if((code & (1 << idfreq)) == 0 && frequencies[idfreq]) {
                            wrongFrequencies.append(idfreq);
                        }
                    }
                    System.out.println(String.format("Fixed new word %02x %s [%s]", result.value, sb.toString(), wrongFrequencies.toString()));
                } else {
                    System.out.println(String.format("New word %02x %s", result.value, sb.toString()));
                }
            }
        }
    }


    private static class UtMessageCallback implements MessageCallback {
        public long pitchLocation = -1;
        public byte[] payload;
        public int numberOfMessages = 0;
        public int numberOfErrors = 0;

        @Override
        public void onNewMessage(byte[] payload, long sampleId) {
            numberOfMessages+=1;
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
            numberOfErrors +=1;
        }
    }
}
