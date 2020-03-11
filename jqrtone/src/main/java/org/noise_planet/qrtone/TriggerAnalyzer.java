package org.noise_planet.qrtone;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Analyse audio samples in order to detect trigger signal
 * Evaluate the exact position of the first tone
 */
public class TriggerAnalyzer {
    public static final double PERCENTILE_BACKGROUND = 0.5;
    private AtomicInteger processedWindowAlpha = new AtomicInteger(0);
    private AtomicInteger processedWindowBeta = new AtomicInteger(0);
    private final int windowOffset;
    private final int gateLength;
    private IterativeGeneralizedGoertzel[] frequencyAnalyzersAlpha;
    private IterativeGeneralizedGoertzel[] frequencyAnalyzersBeta;
    final ApproximatePercentile[] backgroundNoiseEvaluator;
    final CircularArray[] splHistory;
    final PeakFinder peakFinder;
    private final int windowAnalyze;
    private long totalProcessed = 0;
    private TriggerCallback triggerCallback = null;
    final double[] frequencies;
    final double sampleRate;
    public final double triggerSnr;
    private long firstToneLocation = -1;



    public TriggerAnalyzer(double sampleRate, int gateLength, double[] frequencies, double triggerSnr) {
        this.windowAnalyze = gateLength / 3;
        this.frequencies = frequencies;
        this.sampleRate = sampleRate;
        this.triggerSnr = triggerSnr;
        this.gateLength = gateLength;
        if(windowAnalyze < Configuration.computeMinimumWindowSize(sampleRate, frequencies[0], frequencies[1])) {
            throw new IllegalArgumentException("Tone length are not compatible with sample rate and selected frequencies");
        }
        // 50% overlap
        windowOffset = windowAnalyze / 2;
        frequencyAnalyzersAlpha = new IterativeGeneralizedGoertzel[frequencies.length];
        frequencyAnalyzersBeta = new IterativeGeneralizedGoertzel[frequencies.length];
        backgroundNoiseEvaluator = new ApproximatePercentile[frequencies.length];
        splHistory = new CircularArray[frequencies.length];
        peakFinder = new PeakFinder();
        peakFinder.setMinIncreaseCount(Math.max(1, gateLength / windowOffset / 2 - 1));
        peakFinder.setMinDecreaseCount(peakFinder.getMinIncreaseCount());
        for(int i=0; i<frequencies.length; i++) {
            frequencyAnalyzersAlpha[i] = new IterativeGeneralizedGoertzel(sampleRate, frequencies[i], windowAnalyze);
            frequencyAnalyzersBeta[i] = new IterativeGeneralizedGoertzel(sampleRate, frequencies[i], windowAnalyze);
            backgroundNoiseEvaluator[i] = new ApproximatePercentile(PERCENTILE_BACKGROUND);
            splHistory[i] = new CircularArray((gateLength * 3) / windowOffset);
        }
    }

    public void reset() {
        firstToneLocation = -1;
        peakFinder.reset();
        totalProcessed = 0;
        processedWindowAlpha.set(0);
        processedWindowBeta.set(0);
        for(int i=0; i<frequencies.length; i++) {
            frequencyAnalyzersAlpha[i].reset();
            frequencyAnalyzersBeta[i].reset();
            splHistory[i].clear();
        }
    }

    public void setTriggerCallback(TriggerCallback triggerCallback) {
        this.triggerCallback = triggerCallback;
    }

    public long getTotalProcessed() {
        return totalProcessed;
    }

    public long getFirstToneLocation() {
        return firstToneLocation;
    }

    private void doProcess(float[] samples, AtomicInteger windowProcessed,
                           IterativeGeneralizedGoertzel[] frequencyAnalyzers) {
        int processed = 0;
        while(processed < samples.length) {
            int toProcess = Math.min(samples.length - processed,windowAnalyze - windowProcessed.get());
            QRTone.applyHann(samples,processed, processed + toProcess, windowAnalyze, windowProcessed.get());
            for(int idfreq = 0; idfreq < frequencyAnalyzers.length; idfreq++) {
                frequencyAnalyzers[idfreq].processSamples(samples, processed, processed + toProcess);
            }
            processed += toProcess;
            windowProcessed.addAndGet(toProcess);
            if(windowProcessed.get() == windowAnalyze) {
                windowProcessed.set(0);
                double[] splLevels = new double[frequencies.length];
                for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                    double splLevel = 20 * Math.log10(frequencyAnalyzers[idfreq].
                            computeRMS(false).rms);
                    splLevels[idfreq] = splLevel;
                    backgroundNoiseEvaluator[idfreq].add(splLevel);
                    splHistory[idfreq].add((float)splLevel);
                }
                final long location = totalProcessed + processed - windowAnalyze;
                if(peakFinder.add(location, splHistory[frequencies.length - 1].last())) {
                    // Find peak
                    PeakFinder.Element element = peakFinder.getLastPeak();
                    // Check if peak value is greater than specified Signal Noise ratio
                    double backgroundNoiseSecondPeak = backgroundNoiseEvaluator[frequencies.length - 1].result();
                    if(element.value > backgroundNoiseSecondPeak + triggerSnr) {
                        // Check if the level on other triggering frequencies is below triggering level (at the same time)
                        int peakIndex = splHistory[frequencies.length - 1].size() - 1 -
                                (int)(location / windowOffset - element.index / windowOffset);
                        double backgroundNoiseFirstPeak = backgroundNoiseEvaluator[0].result();
                        if(peakIndex >= 0 && peakIndex < splHistory[0].size() &&
                                splHistory[0].get(peakIndex) < backgroundNoiseFirstPeak + triggerSnr) {
                            int firstPeakIndex = peakIndex - (gateLength / windowOffset);
                            // Check if for the first peak the level was
                            if(firstPeakIndex >= 0 && firstPeakIndex < splHistory[0].size()
                                    && splHistory[0].get(firstPeakIndex) > backgroundNoiseFirstPeak + triggerSnr &&
                                    splHistory[frequencies.length - 1].get(firstPeakIndex) < backgroundNoiseSecondPeak + triggerSnr) {
                                // All trigger conditions are met
                                // Evaluate the exact position of the first tone
                                long peakLocation = findPeakLocation(splHistory[frequencies.length - 1].get(peakIndex-1)
                                        ,element.value,splHistory[frequencies.length - 1].get(peakIndex+1),element.index,windowOffset);
                                firstToneLocation = peakLocation + gateLength / 2 + windowOffset;
                                if(triggerCallback != null) {
                                    triggerCallback.onTrigger(this, peakLocation - gateLength / 2 - gateLength + windowOffset);
                                }
                            }
                        }
                    }
                }
                if(triggerCallback != null) {
                    triggerCallback.onNewLevels(this, location, splLevels);
                }
            }
        }
    }

    /**
     * @return Maximum window length in order to have not more than 1 processed window
     */
    public int getMaximumWindowLength() {
        return Math.min(windowAnalyze - processedWindowAlpha.get(), windowAnalyze - processedWindowBeta.get());
    }

    public void processSamples(float[] samples) {
        doProcess(Arrays.copyOf(samples, samples.length), processedWindowAlpha, frequencyAnalyzersAlpha);
        if(totalProcessed > windowOffset) {
            doProcess(Arrays.copyOf(samples, samples.length), processedWindowBeta, frequencyAnalyzersBeta);
        } else if(windowOffset - totalProcessed < samples.length){
            // Start to process on the part used by the offset window
            doProcess(Arrays.copyOfRange(samples, windowOffset - (int)totalProcessed,
                    samples.length), processedWindowBeta,
                    frequencyAnalyzersBeta);
        }
        totalProcessed+=samples.length;
    }

    /**
     * Quadratic interpolation of three adjacent samples
     * @param p0 y value of left point
     * @param p1 y value of center point (maximum height)
     * @param p2 y value of right point
     * @return location [-1; 1] relative to center point, height and half-curvature of a parabolic fit through
     * @link https://www.dsprelated.com/freebooks/sasp/Sinusoidal_Peak_Interpolation.html
     * three points
     */
     static double[] quadraticInterpolation(double p0, double p1, double p2) {
        double location;
        double height;
        double halfCurvature;
        location = (p2 - p0) / (2.0 * (2 * p1 - p2 - p0));
        height = p1 - 0.25 * (p0 - p2) * location;
        halfCurvature = 0.5 * (p0 - 2 * p1 + p2);
        return new double[]{location, height, halfCurvature};
    }

    /**
     * Evaluate peak location of a gaussian
     * @param p0 y value of left point
     * @param p1 y value of center point (maximum height)
     * @param p2 y value of right point
     * @param p1Location x value of p1
     * @param windowLength x delta between points
     * @return Peak x value
     */
    public static long findPeakLocation(double p0, double p1, double p2, long p1Location, int windowLength) {
        double location = quadraticInterpolation(p0, p1, p2)[0];
        return p1Location + (int)(location*windowLength);
    }

    public interface TriggerCallback {
        void onNewLevels(TriggerAnalyzer triggerAnalyzer, long location, double[] spl);
        void onTrigger(TriggerAnalyzer triggerAnalyzer, long messageStartLocation);
    }
}
