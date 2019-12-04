package org.noise_planet.jwarble;

import java.util.ArrayList;
import java.util.List;

public class PeakFinder {
    final int precision;
    boolean increase = true;
    double oldVal = Double.MIN_VALUE;
    int index = 0;
    List<Integer> peaks = new ArrayList<>();

    public PeakFinder(int precision) {
        this.precision = precision;
    }

    public List<Integer> getPeaks() {
        return peaks;
    }

    public boolean add(double value) {
        boolean ret = false;
        double diff = (int)((value - oldVal) * Math.pow(10, precision)) / Math.pow(10, precision);
        if(diff < 0 && increase) {
            peaks.add(index);
            ret = true;
        }
        increase = diff >= 0;
        oldVal = value;
        index++;
        return ret;
    }
}
