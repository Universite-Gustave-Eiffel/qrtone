package org.noise_planet.qrtone;

/**
 * Analyze single tone, looking for expected peaks levels and duration
 */
public class ToneAnalyzer {
    Percentile backgroundNoiseEvaluator = new Percentile(32);
    PeakFinder peakFinder;
    IterativeGeneralizedGoertzel goertzel;

}
