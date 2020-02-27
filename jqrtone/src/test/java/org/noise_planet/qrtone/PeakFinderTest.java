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
        while ((line = br.readLine()) != null) {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            int year = Integer.parseInt(tokenizer.nextToken());
            float value = Float.parseFloat(tokenizer.nextToken());
            peakFinder.add(index++, (double)value);
        }
        int[] expectedIndex = new int[]{5,  17,  27,  38,  50,  52,  61,  69,  78,  87, 102, 104, 116, 130, 137, 148, 160, 164, 170, 177, 183, 193, 198, 205, 207, 217, 228, 237, 247, 257, 268, 272, 279, 290, 299};
        List<PeakFinder.Element> results = peakFinder.getPeaks();
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
        while ((line = br.readLine()) != null) {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            int year = Integer.parseInt(tokenizer.nextToken());
            float value = Float.parseFloat(tokenizer.nextToken());
            peakFinder.add(index++, (double)value);
        }
        int[] expectedIndex = new int[]{5, 17 ,27, 38, 50, 61, 69, 78, 87, 104, 116, 130, 137, 148, 160, 170, 183, 193, 205, 217, 228, 237, 247, 257, 268, 279, 290, 299};
        List<PeakFinder.Element> results = PeakFinder.filter(peakFinder.getPeaks(),6);
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
        for(double value : values) {
            peakFinder.add(index++, value);
        }
        int[] expectedIndex = new int[]{3, 12};
        List<PeakFinder.Element> results = peakFinder.getPeaks();
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
        for(double value : values) {
            peakFinder.add(index++, value);
        }
        int[] expectedIndex = new int[]{3, 12};
        List<PeakFinder.Element> results = peakFinder.getPeaks();
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }


    @Test
    public void findPeakEpsilon() throws IOException {
        double[] t = new double[] {1.663,1.683,1.703,1.722,1.742,1.761,1.781,1.8,1.82,1.84,1.859,1.879,1.898,1.918,1.937,1.957,1.976,1.996,2.016,2.035,2.055,2.074,2.094,2.113,2.133,2.153,2.172,2.192,2.211,2.231,2.25,2.27,2.29,2.309,2.329,2.348,2.368,2.387,2.407,2.427,2.446,2.466,2.485,2.505,2.524,2.544,2.564,2.583,2.603,2.622,2.642,2.661,2.681,2.701,2.72
        };
        double[] v = new double[] {-54.86,-52.48,-57,-50.12,-50.69,-47.32,-61.58,-70.16,-71.9,-51.56,-46.97,-55.98,-54.46,-51.05,-65.05,-32.5,-31.67,-32.48,-31.63,-69.89,-58.76,-58.12,-48.7,-54.25,-54.53,-54.8,-52.81,-56.06,-39.28,-31.21,-31.8,-32.31,-35.8,-31.32,-31.98,-31.35,-31.94,-59.09,-48.31,-59.5,-59.78,-50.88,-56.3,-54.65,-72.44,-54.15,-56.22,-51.24,-57.68,-66.53,-48.31,-53.2,-57.56,-58.2,-65.03
        };
        PeakFinder peakFinder = new PeakFinder();
        for(int i = 0; i < t.length; i++) {
            long index = (long)(t[i] * 1000.0);
            peakFinder.add(index, v[i]);
        }
        List<PeakFinder.Element> peaks = PeakFinder.filter(peakFinder.getPeaks(), 87);
        Map<Long, PeakFinder.Element> peaksMap = new HashMap<>(peaks.size());
        for (PeakFinder.Element el : peaks) {
            peaksMap.put(el.index, el);
        }
        try(FileOutputStream fos = new FileOutputStream("target/peaks.csv")) {
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            Writer writer = new OutputStreamWriter(bos);
            for(int i = 0; i < t.length; i++) {
                long index = (long) (t[i] * 1000.0);
                PeakFinder.Element el = peaksMap.get(index);
                if(el != null) {
                    writer.write(String.format(Locale.ROOT, "%.3f,%.1f,%.1f",t[i],v[i], v[i]));
                } else {
                    writer.write(String.format(Locale.ROOT, "%.3f,%.1f,%.1f",t[i],v[i], 0f));
                }
                writer.write("\n");
            }
            writer.flush();
        }
    }
}