/*
 * BSD 3-Clause License
 *
 * Copyright (c) Unité Mixte de Recherche en Acoustique Environnementale (univ-gustave-eiffel)
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

import org.junit.Test;

import java.io.*;
import java.util.*;


import static org.junit.Assert.assertArrayEquals;

public class PeakFinderTest {

    @Test
    public void findPeaks() throws IOException {
        PeakFinder peakFinder = new PeakFinder();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(PeakFinderTest.class.getResourceAsStream("sunspot.dat")));
        long index = 1;
        List<PeakFinder.Element> results = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            int year = Integer.parseInt(tokenizer.nextToken());
            float value = Float.parseFloat(tokenizer.nextToken());
            if(peakFinder.add(index++, (double)value)) {
                results.add(peakFinder.getLastPeak());
            }
        }
        int[] expectedIndex = new int[]{5,  17,  27,  38,  50,  52,  61,  69,  78,  87, 102, 104, 116, 130, 137, 148, 160, 164, 170, 177, 183, 193, 198, 205, 207, 217, 228, 237, 247, 257, 268, 272, 279, 290, 299};
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }

    @Test
    public void findPeaksMinimumWidth() throws IOException {
        PeakFinder peakFinder = new PeakFinder();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(PeakFinderTest.class.getResourceAsStream("sunspot.dat")));
        long index = 1;
        List<PeakFinder.Element> res = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            int year = Integer.parseInt(tokenizer.nextToken());
            float value = Float.parseFloat(tokenizer.nextToken());
            if(peakFinder.add(index++, (double)value)) {
                res.add(peakFinder.getLastPeak());
            }
        }
        int[] expectedIndex = new int[]{5, 17 ,27, 38, 50, 61, 69, 78, 87, 104, 116, 130, 137, 148, 160, 170, 183, 193, 205, 217, 228, 237, 247, 257, 268, 279, 290, 299};
        List<PeakFinder.Element> results = PeakFinder.filter(res,6);
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }

    @Test
    public void findPeaksIncreaseCondition() throws IOException {
        PeakFinder peakFinder = new PeakFinder();
        peakFinder.setMinIncreaseCount(3);
        double[] values = new double[] {4, 5, 7, 13, 10, 9, 9, 10, 4, 6, 7, 8, 11 , 3, 2, 2};
        long index = 0;
        List<PeakFinder.Element> results = new ArrayList<>();
        for(double value : values) {
            if(peakFinder.add(index++, (double)value)) {
                results.add(peakFinder.getLastPeak());
            }
        }
        int[] expectedIndex = new int[]{3, 12};
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }

    @Test
    public void findPeaksDecreaseCondition() throws IOException {
        PeakFinder peakFinder = new PeakFinder();
        peakFinder.setMinDecreaseCount(2);
        double[] values = new double[] {4, 5, 7, 13, 10, 9, 9, 10, 4, 6, 7, 8, 11 , 3, 2, 2};
        long index = 0;
        List<PeakFinder.Element> results = new ArrayList<>();
        for(double value : values) {
            if(peakFinder.add(index++, (double)value)) {
                results.add(peakFinder.getLastPeak());
            }
        }
        int[] expectedIndex = new int[]{3, 12};
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }
}