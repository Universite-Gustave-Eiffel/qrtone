package org.noise_planet.qrtone;

/**
 * tukey window - Hann window with a flat part in the middle
 */
public class IterativeTukey {
    IterativeHann hannWindow;
    long index = 0;
    int index_begin_flat;
    int index_end_flat;

    public IterativeTukey(int windowLength, double alpha) {
        index_begin_flat = (int)(Math.floor(alpha * (windowLength - 1) / 2.0));
        index_end_flat = windowLength - index_begin_flat;
        hannWindow = new IterativeHann(index_begin_flat * 2);
    }

    void reset() {
        hannWindow.reset();
        index = 0;
    }

    /**
     * Next sample value
     * @return Sample value [-1;1]
     */
    double next() {
        if(index < index_begin_flat || index >= index_end_flat) {
            index++;
            return hannWindow.next();
        } else {
            index++;
            return 1.0;
        }
    }
}
