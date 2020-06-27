package org.noise_planet.qrtone;

/**
 * tukey window - Hann window with a flat part in the middle
 */
public class IterativeTukey {
    IterativeHann hannWindow;
    long index = 0;
    int indexBeginFlat;
    int indexEndFlat;

    public IterativeTukey(int windowLength, double alpha) {
        indexBeginFlat = (int)(Math.floor(alpha * (windowLength - 1) / 2.0));
        indexEndFlat = windowLength - indexBeginFlat;
        hannWindow = new IterativeHann(indexBeginFlat * 2);
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
        if(index < indexBeginFlat || index >= indexEndFlat) {
            index++;
            return hannWindow.next();
        } else {
            index++;
            return 1.0;
        }
    }
}
