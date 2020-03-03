package org.noise_planet.qrtone;

/**
 * Goertzel algorithm - Compute the RMS power of the selected frequencies for the provided audio signals.
 * http://asp.eurasipjournals.com/content/pdf/1687-6180-2012-56.pdf
 * ipfs://QmdAMfyq71Fm72Rt5u1qtWM7teReGAHmceAtDN5SG4Pt22
 * Sysel and Rajmic:Goertzel algorithm generalized to non-integer multiples of fundamental frequency. EURASIP Journal on Advances in Signal Processing 2012 2012:56.
 * Issue with integer multiples of fundamental frequency:
 * window_size = ( k * sample_frequency ) / target_frequency
 * Find k (1, 2, 3, ..) that provides an integer window_size
 * Bin size:
 * bin_size = sample_frequency / window_size
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

    /**
     * @param sampleRate Sampling rate in Hz
     * @param frequency Array of frequency search in Hz
     * @param windowSize Number of samples to analyse
     */
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

    public IterativeGeneralizedGoertzel processSamples(float[] samples, int from, int to) {
        int length = (to - from);
        if(processedSamples + length > windowSize) {
            throw new IllegalArgumentException("Exceed window length");
        }
        final int size;
        if(processedSamples + length == windowSize) {
            size = length - 1;
            lastSample = samples[from + size];
        } else {
            size = length;
        }
        for(int i=0; i < size; i++) {
            s0 = samples[i+from] + cosPikTerm2 * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        processedSamples+=length;
        return this;
    }

    public GoertzelResult computeRMS(boolean computePhase) {
        if(processedSamples != windowSize) {
            throw new IllegalStateException("Not enough processed samples");
        }

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
