package org.noise_planet.jwarble;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class PlayRecord {

    public void playSound(short[] samples, double sampleRate) throws IOException, LineUnavailableException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(samples.length * (Short.SIZE / Byte.SIZE));
        DataOutputStream dataOutputStream = new DataOutputStream(bos);
        for(short sample : samples) {
            dataOutputStream.writeShort(sample);
        }
        InputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        bos = null;
        AudioFormat format = new AudioFormat((float)sampleRate, 16, 1, true, true);
        AudioInputStream audioInputStream = new AudioInputStream(byteArrayInputStream, format, samples.length / format.getFrameSize());
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
        sourceDataLine.open(format);
        sourceDataLine.start();
        int cnt = 0;
        byte tempBuffer[] = new byte[10000];
        try {
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
    }

    public void run() {

        AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, true);
        TargetDataLine microphone;
        AudioInputStream audioInputStream;
        SourceDataLine sourceDataLine;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int numBytesRead;
            int CHUNK_SIZE = 1024;
            byte[] data = new byte[microphone.getBufferSize() / 5];
            microphone.start();

            int bytesRead = 0;
            System.out.println("Open Microphone");
            try {
                while (bytesRead < 100000) { // Just so I can test if recording
                    // my mic works...
                    numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
                    bytesRead = bytesRead + numBytesRead;
                    out.write(data, 0, numBytesRead);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Close Microphone");
            microphone.close();
            byte audioData[] = out.toByteArray();
            // Get an input stream on the byte array
            // containing the data
            InputStream byteArrayInputStream = new ByteArrayInputStream(
                    audioData);
            System.out.println("Open Speaker");
            audioInputStream = new AudioInputStream(byteArrayInputStream,format, audioData.length / format.getFrameSize());
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            sourceDataLine.open(format);
            sourceDataLine.start();
            int cnt = 0;
            byte tempBuffer[] = new byte[10000];
            try {
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
        } catch (LineUnavailableException e) {
            e.printStackTrace();
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
                microphone = AudioSystem.getTargetDataLine(format);

                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int numBytesRead;
                int CHUNK_SIZE = 1024;
                byte[] data = new byte[microphone.getBufferSize() / 5];
                microphone.start();
                micOpen.set(true);
                int bytesRead = 0;
                try {
                    while (run.get()) {
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