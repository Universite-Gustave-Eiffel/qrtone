package org.noise_planet.qrtone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Analyze trigger tone, overlapped windows
 */
public class ToneAnalyzer {
    private final Percentile backgroundNoiseEvaluator = new Percentile(128);
    private PeakFinder peakFinder = new PeakFinder();
    private IterativeGeneralizedGoertzel goertzel;
    private int toneLength;
    public List<double[]> values = new ArrayList<>();
    private long processed;

    public ToneAnalyzer(double sampleRate, double frequency, int windowSize, int toneLength) {
        goertzel = new IterativeGeneralizedGoertzel(sampleRate, frequency, windowSize);
        this.toneLength = toneLength;
    }

    public void processSamples(float[] samples, int from, int to) {
        goertzel.processSamples(samples, from, to);
        processed+=to-from;
        if(goertzel.getWindowSize() == goertzel.getProcessedSamples()) {
            double spl = 20 * Math.log10(goertzel.computeRMS(false).rms);
            backgroundNoiseEvaluator.add(spl);
            if(!values.isEmpty() && peakFinder.add((long)values.size(), spl)) {
                double[] original = values.get((int)peakFinder.getLastPeak().index);
                if(original[1] < original[0]) {
                    original[2] = 1;
                    values.set((int) peakFinder.getLastPeak().index, original);
                }
            }
            double base = backgroundNoiseEvaluator.getPercentile(0.5) + 15;
            values.add(new double[]{spl, base, 0});
//            backgroundNoiseEvaluator.add(spl);
//            peakFinder.add(processed, spl);
//            double backgroundNoise = backgroundNoiseEvaluator.getPercentile(0.05) + 15.0;
//            List<PeakFinder.Element> peaks = PeakFinder.filter(peakFinder.getPeaks(), backgroundNoise);
//            System.out.println(String.format(Locale.ROOT, "%.3f,%.1f,%.1f", processed / goertzel.getSampleRate(), spl, backgroundNoise));
        }
    }

    public int getWindowSize() {
        return goertzel.getWindowSize();
    }

    public int getProcessedSamples() {
        return goertzel.getProcessedSamples();
    }


}
