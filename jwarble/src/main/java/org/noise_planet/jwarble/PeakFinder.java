package org.noise_planet.jwarble;

import java.util.ArrayList;
import java.util.List;

public class PeakFinder {
    private boolean increase = true;
    private double oldVal = Double.MIN_VALUE;
    private long oldIndex = 0;
    private List<Long> peaks = new ArrayList<>();
    private List<Integer> peaksIndex = new ArrayList<>();
    double[] clockRmsHistory;

    public PeakFinder(int maxHistory) {
        this.clockRmsHistory = new double[maxHistory];
    }

    public List<Long> getPeaks() {
        return peaks;
    }

    public List<Integer> getPeaksIndex() {
        return peaksIndex;
    }

    public double getPeakValue(int index) {
        return clockRmsHistory[index];
    }

    public void clearPeaks(long upTo) {
        while(!peaks.isEmpty() && peaks.get(0) < upTo) {
            peaks.remove(0);
            peaksIndex.remove(0);
        }
    }

    public boolean add(Long index, double value) {
        System.arraycopy(clockRmsHistory, 1, clockRmsHistory, 0, clockRmsHistory.length - 1);
        clockRmsHistory[clockRmsHistory.length - 1] = value;
        for(int i = 0; i < peaksIndex.size(); i++) {
            peaksIndex.set(i, peaksIndex.get(i) - 1);
        }
        boolean ret = false;
        double diff = value - oldVal;
        // Detect switch from increase/stall to decrease
        if(diff < 0 && increase) {
            // Count the number of elements with stall values
            int stallCount = 0;
            for(int i = clockRmsHistory.length - 2; i >= 0; i--) {
                if(clockRmsHistory[i] >= clockRmsHistory[i + 1]) {
                    stallCount++;
                } else {
                    break;
                }
            }
            stallCount = Math.max(1, stallCount);
            peaks.add(index - stallCount * (index - oldIndex));
            peaksIndex.add(clockRmsHistory.length - stallCount);
            ret = true;
        }
        increase = diff >= 0;
        oldVal = value;
        oldIndex = index;
        return ret;
    }
}
