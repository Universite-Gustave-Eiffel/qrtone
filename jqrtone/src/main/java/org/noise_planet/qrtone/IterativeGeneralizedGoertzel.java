package org.noise_planet.qrtone;

/**
 * GeneralizedGoertzel without cached samples
 */
public class IterativeGeneralizedGoertzel {
    public static final double M2PI = Math.PI * 2;

    private double s0 = 0;
    private double s1 = 0.;
    private double s2 = 0.;
    private double cosPikTerm2;
    private double pikTerm;
    private float lastSample = 0;
    private double sampleRate;
    private int windowSize;
    private int processedSamples = 0;
    private Complex cc;

    public IterativeGeneralizedGoertzel(double sampleRate, double frequency, int windowSize) {
        this.sampleRate = sampleRate;
        this.windowSize = windowSize;
        // Fix frequency using the sampleRate of the signal
        double samplingRateFactor = windowSize / sampleRate;
        pikTerm = M2PI * (frequency * samplingRateFactor) / windowSize;
        cosPikTerm2 = Math.cos(pikTerm) * 2.0;
        cc = new Complex(pikTerm, 0).exp();
    }

    public void reset() {
        s0 = 0;
        s1 = 0;
        s2 = 0;
        processedSamples = 0;
        lastSample = 0;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public int getProcessedSamples() {
        return processedSamples;
    }

    public void processSamples(float[] samples) {
        if(processedSamples + samples.length > windowSize) {
            throw new IllegalArgumentException("Exceed window length");
        }
        final int size;
        if(processedSamples + samples.length == windowSize) {
            lastSample = samples[samples.length - 1];
            size = samples.length - 1;
        } else {
            size = samples.length;
        }
        for(int i=0; i < size; i++) {
            s0 = samples[i] + cosPikTerm2 * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
    }

    public GoertzelResult computeRMS(boolean computePhase) {
        // final computations
        s0 = lastSample + cosPikTerm2 * s1 - s2;

        // complex multiplication substituting the last iteration
        // and correcting the phase for (potentially) non - integer valued
        // frequencies at the same time
        Complex parta = new Complex(s0, 0).sub(new Complex(s1, 0).mul(cc));
        Complex partb = new Complex(pikTerm * (windowSize - 1.), 0).exp();
        Complex y = parta.mul(partb);

        double rms = Math.sqrt((y.r * y.r  + y.i * y.i) * 2) / windowSize;

        double phase = 0;
        if(computePhase) {
            phase = Math.atan2(y.i, y.r);
        }
        reset();
        return new GoertzelResult(rms, phase);
    }

    public static class GoertzelResult {
        public final double rms;
        public final double phase;

        public GoertzelResult(double rms, double phase) {
            this.rms = rms;
            this.phase = phase;
        }
    }
}
