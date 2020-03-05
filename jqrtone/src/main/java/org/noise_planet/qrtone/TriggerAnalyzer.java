package org.noise_planet.qrtone;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TriggerAnalyzer {
    private AtomicInteger processedWindowAlpha = new AtomicInteger(0);
    private AtomicInteger processedWindowBeta = new AtomicInteger(0);
    private final Percentile[] backgroundNoiseEvaluator;
    private final int startProcessBeta;
    private IterativeGeneralizedGoertzel[] frequencyAnalyzersAlpha;
    private IterativeGeneralizedGoertzel[] frequencyAnalyzersBeta;
    private final int windowAnalyze;
    private long totalProcessed = 0;
    private TriggerCallback triggerCallback = null;
    final double[] frequencies;
    final double sampleRate;

    public TriggerAnalyzer(double sampleRate, int windowAnalyze, double[] frequencies) {
        this.windowAnalyze = windowAnalyze;
        this.frequencies = frequencies;
        this.sampleRate = sampleRate;
        // 50% overlap
        startProcessBeta = windowAnalyze / 2;
        frequencyAnalyzersAlpha = new IterativeGeneralizedGoertzel[frequencies.length];
        frequencyAnalyzersBeta = new IterativeGeneralizedGoertzel[frequencies.length];
        backgroundNoiseEvaluator = new Percentile[frequencies.length];
        for(int i=0; i<frequencies.length; i++) {
            frequencyAnalyzersAlpha[i] = new IterativeGeneralizedGoertzel(sampleRate, frequencies[i], windowAnalyze);
            frequencyAnalyzersBeta[i] = new IterativeGeneralizedGoertzel(sampleRate, frequencies[i], windowAnalyze);
            backgroundNoiseEvaluator[i] = new Percentile(64);
        }
    }

    public void setTriggerCallback(TriggerCallback triggerCallback) {
        this.triggerCallback = triggerCallback;
    }

    public long getTotalProcessed() {
        return totalProcessed;
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
                if(triggerCallback != null) {
                    double[] splLevels = new double[frequencies.length];
                    for(int idfreq = 0; idfreq < frequencies.length; idfreq++) {
                        splLevels[idfreq] = 20 * Math.log10(frequencyAnalyzers[idfreq].
                                computeRMS(false).rms);
                    }
                    triggerCallback.onNewLevels(this, totalProcessed + processed, splLevels);
                }
                // TODO process data
            }
        }
    }

    public void processSamples(float[] samples) {
        doProcess(Arrays.copyOf(samples, samples.length), processedWindowAlpha, frequencyAnalyzersAlpha);
        if(totalProcessed > startProcessBeta) {
            doProcess(Arrays.copyOf(samples, samples.length), processedWindowBeta, frequencyAnalyzersBeta);
        } else if(startProcessBeta - totalProcessed < samples.length){
            // Start to process on the part used by the offset window
            doProcess(Arrays.copyOfRange(samples, startProcessBeta - (int)totalProcessed,
                    samples.length), processedWindowBeta,
                    frequencyAnalyzersBeta);
        }
        totalProcessed+=samples.length;
    }

    public interface TriggerCallback {
        void onNewLevels(TriggerAnalyzer triggerAnalyzer, long location, double[] spl);
    }
}
