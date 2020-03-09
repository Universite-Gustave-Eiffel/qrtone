/*
 * BSD 3-Clause License
 *
 * Copyright (c) Ifsttar
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.noise_planet.qrtone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


/**
 * PeakFinder will find highest values
 * This class results are equivalent with Octave(R) findpeaks function
 */
public class PeakFinder {
    private float epsilon = 1;
    private boolean increase = true;
    private double oldVal = Double.MIN_VALUE;
    private long oldIndex = 0;
    boolean added = false;
    private Element lastPeak = null;
    private int increaseCount = 0;
    private int decreaseCount = 0;
    private int minIncreaseCount = -1;
    private int minDecreaseCount = -1;

    public Element getLastPeak() {
        return lastPeak;
    }

    /**
     * @return Remove peaks where increase steps count are less than this number
     */
    public int getMinIncreaseCount() {
        return minIncreaseCount;
    }

    /**
     * @param minIncreaseCount Remove peaks where increase steps count are less than this number
     */
    public void setMinIncreaseCount(int minIncreaseCount) {
        this.minIncreaseCount = minIncreaseCount;
    }

    /**
     * @return Remove peaks where decrease steps count are less than this number
     */
    public int getMinDecreaseCount() {
        return minDecreaseCount;
    }

    /**
     * @return Remove peaks where decrease steps count are less than this number
     */
    public void setMinDecreaseCount(int minDecreaseCount) {
        this.minDecreaseCount = minDecreaseCount;
    }

    public boolean add(Long index, double value) {
        boolean ret = false;
        double diff = value - oldVal;
        // Detect switch from increase to decrease/stall
        if(diff <= 0 && increase) {
            if(increaseCount >= minIncreaseCount) {
                lastPeak = new Element(oldIndex, oldVal);
                added = true;
                if(minDecreaseCount <= 1 ) {
                    ret = true;
                }
            }
        } else if(diff > 0 && !increase) {
            // Detect switch from decreasing to increase
            if(added && minDecreaseCount != -1 && decreaseCount < minDecreaseCount) {
                lastPeak = null;
                added = false;
            }
        }
        increase = diff > 0;
        if(increase) {
            increaseCount++;
            decreaseCount = 0;
        } else {
            decreaseCount++;
            if(decreaseCount >= minDecreaseCount && added) {
                // condition for decrease fulfilled
                added = false;
                ret = true;
            }
            increaseCount=0;
        }
        oldVal = value;
        oldIndex = index;
        return ret;
    }

    /**
     * Remove peaks where distance to other peaks are less than provided argument
     * @param minWidth Minium width in index
     */
    public static List<Element> filter(List<Element> peaks, int minWidth) {
        // Sort peaks by value
        List<Element> sortedPeaks = new ArrayList<>(peaks);
        Collections.sort(sortedPeaks);
        for(int i = 0; i < sortedPeaks.size(); i++) {
            Element topPeak = sortedPeaks.get(i);
            int j = i + 1;
            while(j < sortedPeaks.size()) {
                Element otherPeak = sortedPeaks.get(j);
                if(Math.abs(otherPeak.index - topPeak.index) <= minWidth) {
                    sortedPeaks.remove(j);
                } else {
                    j += 1;
                }
            }
        }
        // Sort peaks by index
        Collections.sort(sortedPeaks, new ElementSortByIndex());
        return sortedPeaks;
    }

    /**
     * Remove peaks where value is less than provided argument
     * @param minValue Minium peak value
     */
    public static List<Element> filter(List<Element> peaks, double minValue) {
        // Sort peaks by value
        List<Element> filteredPeaks = new ArrayList<>(peaks);
        int j = 0;
        while(j < filteredPeaks.size()) {
            Element peak = filteredPeaks.get(j);
            if(peak.value < minValue) {
                filteredPeaks.remove(j);
            } else {
                j += 1;
            }
        }
        return filteredPeaks;
    }

    public static class Element implements Comparable<Element> {
        public final long index;
        public final double value;

        public Element(long index, double value) {
            this.index = index;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Element element = (Element) o;
            return index == element.index &&
                    Double.compare(element.value, value) == 0;
        }

        // Natural order is Descendant values
        @Override
        public int compareTo(Element element) {
            return Double.compare(element.value, this.value);
        }

        @Override
        public int hashCode() {
            return Long.valueOf(index).hashCode() + Double.valueOf(value).hashCode();
        }
    }

    public static class ElementSortByIndex implements Comparator<Element> {
        @Override
        public int compare(Element element, Element t1) {
            return Long.valueOf(element.index).compareTo(t1.index);
        }
    }

}
