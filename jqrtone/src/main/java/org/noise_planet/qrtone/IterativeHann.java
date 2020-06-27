package org.noise_planet.qrtone;

/**
 * Generate Hann window without using cos function at each samples
 * https://dsp.stackexchange.com/questions/68528/compute-hann-window-without-cos-function
 */
public class IterativeHann {
    final double k1;
    double k2;
    double k3;
    int index = 0;

    public IterativeHann(int windowSize) {
        double wT = 2* Math.PI / (windowSize - 1);
        k1 = 2.0 * Math.cos(wT);
        reset();
    }

    void reset() {
        index = 0;
        k2 = k1 / 2.0;
        k3 = 1.0;
    }

    /**
     * Next sample value
     * @return Sample value [-1;1]
     */
    double next() {
        if(index >= 2) {
            double tmp = k2;
            k2 = k1 * k2 - k3;
            k3 = tmp;
            return 0.5 - 0.5 * k2;
        } else if(index == 1) {
            index++;
            return 0.5 - 0.5 * k2;
        } else {
            index++;
            return 0;
        }
    }
}
