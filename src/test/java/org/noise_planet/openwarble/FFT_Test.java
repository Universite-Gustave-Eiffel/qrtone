package org.noise_planet.openwarble;

import org.junit.Test;
import org.renjin.gcc.runtime.DoublePtr;
import org.renjin.gcc.runtime.Ptr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FFT_Test {


  private static final double TONE_RATIO = 1.0594630943591;

  private double squareAbsoluteFFTToRMS(double squareAbsoluteFFT, int sampleSize) {
    return Math.sqrt(squareAbsoluteFFT / 2) / (sampleSize / 2);
  }

  @Test
  public void core1khzTest() {



    // Make 1000 Hz signal
    final int sampleRate = 44100;
    final int window = 4410;
    final int signalFrequency = 1000;
    double powerRMS = 2500; // 90 dBspl
    double powerPeak = powerRMS * Math.sqrt(2);
    double[] signal = new double[sampleRate];
    for (int s = 0; s < signal.length; s++) {
      double t = s * (1 / (double) sampleRate);
      signal[s] = (Math.sin(2 * Math.PI * signalFrequency * t) * (powerPeak));
    }

    Ptr cfg = kiss_fft.kiss_fft_alloc(window, 0, new DoublePtr(null, 0), new DoublePtr(null, 0));
    long begin = System.currentTimeMillis();


    float lastFreq = 0;

    double[] windowData = Arrays.copyOfRange(signal, 0, window);

    Ptr input = kiss_fft.createInput(window, new DoublePtr(windowData));

    kiss_fft.kiss_fft(cfg, input, input);


    Ptr rmsPtr = kiss_fft.computeRMS(cfg, input);

    assertTrue(rmsPtr instanceof DoublePtr);

    double[] rms = ((DoublePtr)rmsPtr).array;

    int freqBand = (int)Math.round(signalFrequency / (sampleRate / (double)window));
    double rmsAtSignal = squareAbsoluteFFTToRMS(rms[freqBand], window);

    assertEquals(powerRMS, rmsAtSignal , 0.1);

  }



  public static double computeRms(short[] inputSignal) {
    double sampleSum = 0;
    for (short sample : inputSignal) {
      sampleSum += sample * sample;
    }
    return Math.sqrt(sampleSum / inputSignal.length);
  }

  public static double todBspl(double rms, double refSoundPressure ) {
    return 20 * Math.log10(rms / refSoundPressure);
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

  double[] computeFrequencyBands(double start,double factor, int n) {
    double[] freqs = new double[n];
    for(int i=0; i < n; i++) {
      freqs[i] = start * Math.pow(factor, i);
    }
    return freqs;
  }

  // Test parsing a recording of chirp sounds

  public void coreRecording1Test() throws IOException {
    InputStream inputStream = FFT_Test.class.getResourceAsStream("raw1_44100_16bitPCM.raw");
    short[] signal = loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);

    double windowTime = 0.01;
    final int sampleRate = 22050;
    int window = (int) Math.ceil(sampleRate * windowTime);

    double[] pitchesTestTime = new double[]{0.237, 0.328, 0.411, 0.505, 0.584, 0.673, 0.761, 0.855, 0.940, 1.023,
            1.109, 1.201, 1.286, 1.461, 1.551, 1.629, 1.725, 1.825, 1.9};
    double[] pitchesTestFrequencies = new double[]{4698.6362866638, 5274.0409105875, 1760, 2349.3181433371, 1760,
            1760, 2093.0045224036, 2637.0204552996, 1760,1760, 1864.655046072, 2093.0045224036, 2959.9553816882,
            3322.4375806328, 4698.6362866638, 1760, 7458.6201842551, 6644.875161251, 2489.0158697739};

    double[] refFreqs = computeFrequencyBands(1760, TONE_RATIO, 32);
    assertEquals(pitchesTestFrequencies.length, pitchesTestTime.length);

    int testId = 0;

    float lastFreq = 0;
    long begin = System.currentTimeMillis();

    List<Double> freqsSequences = new ArrayList<Double>();

    for(int i = 0; i < signal.length; i++) {
      if (i % window == 0) {
        lastFreq = 0;

        int id = Arrays.binarySearch(refFreqs, lastFreq);
        if (id < 0) {
          id = (-id - 1);
          if (id == refFreqs.length || (id > 0 && Math.abs(refFreqs[id] - lastFreq) > Math.abs(refFreqs[id - 1] - lastFreq))) {
            id -= 1;
          }
          double delta = (refFreqs[id] * (256. / 243.) - refFreqs[id]) / 3;
          if (Math.abs(refFreqs[id] - lastFreq) > delta) {
            id = -1;
          }
        }
        if (id >= 0) {
          if (freqsSequences.isEmpty() || freqsSequences.get(freqsSequences.size() - 1) != refFreqs[id]) {
            System.out.println(String.format("%.3f s : %.0f Hz (%.0f Hz)", (double) (i - window) / sampleRate, refFreqs[id], lastFreq));
            freqsSequences.add(refFreqs[id]);
          }
        }
        // Accept error of a third of semitone
        //double delta = (pitchesTestFrequencies[testId] * (256. / 243.) - lastFreq) / 3;
        //assertEquals(pitchesTestFrequencies[testId], lastFreq, delta);
      }
    }
    System.out.println(String.format("Done in %d ms for a %.2f s audio file",System.currentTimeMillis() - begin, (double)signal.length / sampleRate));

  }

}
