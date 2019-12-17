package org.noise_planet.jwarble;

import com.backblaze.erasure.ReedSolomon;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
            audio[s] = Math.cos(OpenWarble.M2PI * signalFrequency * t) * (powerPeak);
        }

        double[] phase = new double[1];
        double[] rms = OpenWarble.generalizedGoertzel(audio,0, audio.length, sampleRate, new double[]{1000.0}, phase, false);

        double signal_rms = OpenWarble.computeRms(audio);

        assertEquals(signal_rms, rms[0], 0.1);
        assertEquals(0, phase[0], 1e-8);
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

    public static void writeDoubleToFile(String path, double[] signal) throws IOException {
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
        // Send Ipfs address
        // Python code:
        // import base58
        // import struct
        // s = struct.Struct('b').unpack
        // payload = map(lambda v : s(v)[0], base58.b58decode("QmXjkFQjnD8i8ntmwehoAHBfJEApETx8ebScyVzAHqgjpD"))
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate, false));
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
        assertTrue(Math.abs(blankSamples - messageCallback.pitchLocation + openWarble.doorLength) < openWarble.doorLength / 4.0);
        assertArrayEquals(payload, messageCallback.payload);
        assertEquals(0, openWarble.getHammingCorrectedErrors());
    }


    @Test
    public void testRecognitionWithoutNoiseRS() throws IOException {
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
        assertTrue(Math.abs(blankSamples - messageCallback.pitchLocation + openWarble.doorLength) < openWarble.doorLength / 4.0);
        assertArrayEquals(payload, messageCallback.payload);
        assertEquals(0, openWarble.getHammingCorrectedErrors());
        //writeDoubleToFile("target/source_mono_16bits_BigEndian_44100Hz.raw", allSignal);
    }
    @Test
    public void testRecognitionWithNoise() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double noisePeak = 0.1;
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate, false));
        UtCallback utCallback = new UtCallback(false);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        double[] signal = openWarble.generateSignal(powerPeak, payload);
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
        assertTrue(Math.abs(blankSamples - messageCallback.pitchLocation + openWarble.doorLength) < openWarble.doorLength / 4.0);
        assertArrayEquals(payload, messageCallback.payload);
        assertEquals(0, openWarble.getHammingCorrectedErrors());
    }


    @Test
    public void testRecognitionWithNoiseRS() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double noisePeak = 0.1;
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
        assertTrue(Math.abs(blankSamples - messageCallback.pitchLocation + openWarble.doorLength) < openWarble.doorLength / 4.0);
        assertArrayEquals(payload, messageCallback.payload);
        assertEquals(0, openWarble.getHammingCorrectedErrors());
    }


    @Test
    public void testWithRecordedAudio() throws IOException {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4,
                31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        UtCallback utCallback = new UtCallback(true);
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
    }

    @Test
    public void testWithRecordedAudioNoTrigger() throws IOException {
        double sampleRate = 44100;
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(9, sampleRate, false));
        UtCallback utCallback = new UtCallback(true);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        short[] signal_short;
        try (InputStream inputStream = OpenWarbleTest.class.getResourceAsStream("notrigger_44100hz_mono_16bits.raw")) {
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
        assertEquals(0, messageCallback.numberOfMessages);
        assertEquals(0, messageCallback.numberOfErrors);
    }

    public static class UtMessageCallback implements MessageCallback {
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

    public static class UtCallback implements OpenWarble.UnitTestCallback {

        boolean print;

        public UtCallback(boolean print) {
            this.print = print;
        }

        @Override
        public void generateWord(double time, byte word, int encodedWord, List<Double> frequencies) {
            if(print) {
                StringBuilder sb = new StringBuilder();
                for(double freq : frequencies) {
                    if (sb.length() != 0) {
                        sb.append(",");
                    }
                    sb.append(String.format(Locale.ROOT, "%.0f", freq));
                }
                System.out.println(String.format(Locale.ROOT,"%.3f,0x%02x,%s", time ,word, sb.toString()));
            }
        }

        @Override
        public void detectWord(double time, Hamming12_8.CorrectResult result, int encodedWord, List<Double> frequencies) {
            if(print) {
                StringBuilder sb = new StringBuilder();
                for(double freq : frequencies) {
                    if (sb.length() != 0) {
                        sb.append(",");
                    }
                    sb.append(String.format(Locale.ROOT, "%.0f", freq));
                }
                System.out.println(String.format(Locale.ROOT, "%.3f,0x%02x,%s", time ,result.value, sb.toString()));
//                if (result.result == Hamming12_8.CorrectResultCode.CORRECTED_ERROR) {
//                    int code = Hamming12_8.encode(result.value);
//                    StringBuilder wrongFrequencies = new StringBuilder();
//                    for(int idfreq = 0; idfreq < OpenWarble.NUM_FREQUENCIES; idfreq++) {
//                        if ((code & (1 << idfreq)) != 0 && !frequencies[idfreq]) {
//                            wrongFrequencies.append(idfreq);
//                        } else if((code & (1 << idfreq)) == 0 && frequencies[idfreq]) {
//                            wrongFrequencies.append(idfreq);
//                        }
//                    }
//                    System.out.println(String.format("Fixed new word %02x %s [%s]", result.value, sb.toString(), wrongFrequencies.toString()));
//                } else {
//                    System.out.println(String.format("New word %02x %s", result.value, sb.toString()));
//                }
            }
        }
    }

    @Test
    public void randTest() {
        // This specific random must give the same results regardless of the platform/compiler
        int[] expected= new int[] {1199,22292,14258,30291,11005,15335,22572,27361,8276,27653};
        AtomicLong seed = new AtomicLong(1337);
        for(int expectedValue : expected) {
            assertEquals(expectedValue, OpenWarble.warbleRand(seed));
        }
    }

    @Test(expected = AssertionError.class)
    public void testShuffle1() {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        expectedPayload = Arrays.copyOf(expectedPayload, openWarble.blockLength);
        byte[] test = Arrays.copyOf(expectedPayload, expectedPayload.length);
        openWarble.swapChars(test, openWarble.shuffleIndex);
        assertArrayEquals(expectedPayload, test);
    }

    @Test
    public void testShuffle() {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        expectedPayload = Arrays.copyOf(expectedPayload, openWarble.blockLength);
        byte[] test = Arrays.copyOf(expectedPayload, expectedPayload.length);
        openWarble.swapChars(test, openWarble.shuffleIndex);
        openWarble.unswapChars(test, openWarble.shuffleIndex);
        assertArrayEquals(expectedPayload, test);
    }

    @Test
    public void testRSEncodeDecode() {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        byte[] blocks = openWarble.encodeReedSolomon(expectedPayload);

        // Check encoding
        OpenWarble.ReedSolomonResult result = openWarble.decodeReedSolomon(blocks);
        assertEquals(OpenWarble.ReedSolomonResultCode.NO_ERRORS,  result.code);
        assertArrayEquals(expectedPayload,  result.payload);
        assertEquals(0, result.fixedErrors);
    }

    @Test
    public void testRSEncodeDecode1Error() {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        byte[] blocks = openWarble.encodeReedSolomon(expectedPayload);

        for(int i=0; i < blocks.length; i++) {
            byte[] alteredBytes = Arrays.copyOf(blocks, blocks.length);
            alteredBytes[i] = 55;
            // Check encoding
            OpenWarble.ReedSolomonResult result = openWarble.decodeReedSolomon(alteredBytes);
            assertNotEquals(String.format("Could not fixed error on location %d",i), OpenWarble.ReedSolomonResultCode.FAIL_CORRECTION,  result.code);
            assertArrayEquals(expectedPayload, result.payload);
            if(result.code == OpenWarble.ReedSolomonResultCode.CORRECTED_ERROR) {
                assertEquals(String.format("Not expected fixes on location %d",i), 1, result.fixedErrors);
            }
        }
    }


    @Test
    public void testRSEncodeDecode2Errors() {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        byte[] blocks = openWarble.encodeReedSolomon(expectedPayload);

        for(int i=0; i < expectedPayload.length  / 2; i++) {
            byte[] alteredBytes = Arrays.copyOf(blocks, blocks.length);
            alteredBytes[i] = 55;
            alteredBytes[alteredBytes.length - i - 1] = 12;
            // Check encoding
            OpenWarble.ReedSolomonResult result = openWarble.decodeReedSolomon(alteredBytes);
            assertNotEquals(String.format("Could not fixed error on location %d",i), OpenWarble.ReedSolomonResultCode.FAIL_CORRECTION,  result.code);
            assertArrayEquals(expectedPayload, result.payload);
            if(result.code == OpenWarble.ReedSolomonResultCode.CORRECTED_ERROR) {
                assertTrue(String.format("Not expected fixes on location %d %d >= 3",i, result.fixedErrors), result.fixedErrors < 3);
            }
        }
    }

    @Test
    public void crcTest() {
        byte[] expectedPayload = new byte[]{18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        byte base = OpenWarble.crc8(expectedPayload, 0, expectedPayload.length);
        AtomicLong next = new AtomicLong(1337);
        for(int i=0; i < expectedPayload.length; i++) {
            byte[] alteredPayload = Arrays.copyOf(expectedPayload, expectedPayload.length);
            alteredPayload[i] = (byte) (OpenWarble.warbleRand(next) % 255);
            assertNotEquals(base, OpenWarble.crc8(alteredPayload, 0, alteredPayload.length));
        }
    }

    /**
     * Test generation of possible error locations for a given number of errors
     */
    @Test
    public void testTryTable() {
        int maximumErrors = 3;
        int maximumShards = 4;
        int[] tryTable = new int[maximumErrors];
        int tryCursor = 0;
        List<String> expectedResults = new ArrayList<>(Arrays.asList("[0]","[1]","[2]","[3]","[0, 1]","[0, 2]","[0, 3]",
                "[1, 2]","[1, 3]","[2, 3]","[0, 1, 2]","[0, 1, 3]","[0, 2, 3]","[1, 2, 3]"));
        while (tryCursor < tryTable.length) {
            assertEquals(expectedResults.remove(0),
                    Arrays.toString(Arrays.copyOfRange(tryTable,0, tryCursor + 1)));
            tryCursor = OpenWarble.nextErrorState(tryCursor, tryTable, maximumShards);
        }
    }

    public void printShards(byte [] [] dataShards) {
        for(int row =0; row < dataShards.length; row++) {
            final byte[] columns = dataShards[row];
            for(int column = 0; column < columns.length; column++) {
                if(row < dataShards.length - 2 && (columns[column] >= (byte)'A' && columns[column] <= (byte)'Z')) {
                    System.out.print(String.format("   %s", new String(new byte[] {columns[column]})));
                } else {
                    System.out.print(String.format("0x%02X", columns[column]));
                }
                if(column < columns.length - 1) {
                    System.out.print(" ");
                } else {
                    System.out.print("\n");
                }
            }
        }
        System.out.print("\n");
    }

    public void printBlocks(byte[] blocks) {

    }
    @Test
    public void sampleTest() {
        // Parameters
        final int payloadSize = 8;
        final int WARBLE_RS_P = 4;
        final int WARBLE_RS_DISTANCE = 1;
        // Init
        byte[] payload = new byte[payloadSize];
        // Fill payload
        for(int i = 0; i < payloadSize; i++) {
            payload[i] = (byte)((byte)'A' + (byte)i);
        }
        final int shardSize = Math.max(1, (int)Math.ceil(payloadSize / (float)(WARBLE_RS_P - 1)));
        final int blockLength = payloadSize + WARBLE_RS_DISTANCE * shardSize + shardSize;
        byte[] blocks = Arrays.copyOf(payload, blockLength);
        byte [] [] dataShards = new byte[WARBLE_RS_P + WARBLE_RS_DISTANCE][];
        // Init empty shards arrays
        for (int block = 0; block < WARBLE_RS_P + WARBLE_RS_DISTANCE; block++) {
            dataShards[block] = new byte[shardSize];
        }
        // Push payload
        for (int payloadIndex = 0; payloadIndex < payload.length; payloadIndex++) {
            final int blockId = payloadIndex % (WARBLE_RS_P - 1);
            final int column = payloadIndex / (WARBLE_RS_P - 1);
            dataShards[blockId][column] = payload[payloadIndex];
        }
        System.out.println("Original matrix");
        printShards(dataShards);

        // push crc bytes
        int parityCursor = payload.length + WARBLE_RS_DISTANCE * shardSize;
        for (int column = 0; column < shardSize; column++) {
            final int startPayload = column * (WARBLE_RS_P - 1);
            final int endPayload = Math.min(payloadSize, startPayload + (WARBLE_RS_P - 1));
            byte crc = OpenWarble.crc8(payload, startPayload, endPayload);
            dataShards[WARBLE_RS_P - 1][column] = crc;
            blocks[parityCursor++] = crc;
        }

        System.out.println("Add crc bytes");
        printShards(dataShards);

        // Compute parity
        ReedSolomon codec = ReedSolomon.create(WARBLE_RS_P, WARBLE_RS_DISTANCE);
        codec.encodeParity(dataShards, 0, shardSize);

        System.out.println("Add parity bytes");
        printShards(dataShards);

        final int totalShards = WARBLE_RS_P+WARBLE_RS_DISTANCE;

        // Copy parity bytes from RS structure to blocks
        int cursor = payload.length;
        for (int column = 0; column < shardSize; column++) {
            for(int row = WARBLE_RS_P; row < totalShards; row++) {
                blocks[cursor++] = dataShards[row][column];
            }
        }
        System.out.print("[");

        for(int column = 0; column < blocks.length; column++) {
            if (column < payloadSize && (blocks[column] >= (byte) 'A' && blocks[column] <= (byte) 'Z')) {
                System.out.print(String.format("%s", new String(new byte[]{blocks[column]})));
            } else {
                System.out.print(String.format("0x%02X", blocks[column]));
            }
            if(column < blocks.length - 1) {
                System.out.print(", ");
            }
        }
        System.out.print("]\n");

        // Random suffling

        // Compute index shuffling of bytes
        int[] shuffleIndex = new int[blockLength];
        byte[] index = new byte[blockLength];
        for(int i = 0; i < blockLength; i++) {
            shuffleIndex[i] = i;
            index[i] = (byte)i;
        }
        OpenWarble.fisherYatesShuffleIndex(blockLength, shuffleIndex);

        OpenWarble.swapChars(blocks, shuffleIndex);
        OpenWarble.swapChars(index, shuffleIndex);

        System.out.print("[");
        for(int column = 0; column < blocks.length; column++) {
            if (index[column] < payloadSize && (blocks[column] >= (byte) 'A' && blocks[column] <= (byte) 'Z')) {
                System.out.print(String.format("%s", new String(new byte[]{blocks[column]})));
            } else {
                System.out.print(String.format("0x%02X", blocks[column]));
            }
            if(column < blocks.length - 1) {
                System.out.print(", ");
            }
        }
        System.out.print("]\n");

        // Hamming code

        System.out.println("Hamming");
        System.out.print("[");
        for(int column = 0; column < blocks.length; column++) {
            int code = Hamming12_8.encode(blocks[column]);
            System.out.print(String.format("0x%03X", code & 0xFFF));
            if(column < blocks.length - 1) {
                System.out.print(", ");
            }
        }
        System.out.print("]\n");

        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payloadSize, 44100));

    }

    @Test
    public void testLeak() {
        double sampleRate = 44100;
        double powerRMS = 1; // 90 dBspl
        float signalFrequency = 1000;
        double powerPeak = powerRMS * Math.sqrt(2);
        double windowTime = 0.086;

        double[] audio = new double[(int)(sampleRate*windowTime)];
        OpenWarble.generatePitch(audio, 0, audio.length, sampleRate, signalFrequency, powerPeak);

        double[] phase = new double[1];
        for(double f = signalFrequency - 200; f < signalFrequency + 200; f+=20) {
            double[] rms = OpenWarble.generalizedGoertzel(audio, 0, audio.length, sampleRate, new double[]{f}, phase, true);
            System.out.println(String.format(Locale.ROOT, "%.1f,%.2f",f, 20*Math.log10(rms[0])));
        }
        double signal_rms = OpenWarble.computeRms(audio);
    }

}