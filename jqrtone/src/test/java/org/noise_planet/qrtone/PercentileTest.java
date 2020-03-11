package org.noise_planet.qrtone;

import org.apache.commons.math3.stat.descriptive.rank.PSquarePercentile;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

import static org.junit.Assert.assertEquals;

public class PercentileTest {

    @Test
    public void testApproximatePercentile1() throws IOException {
        PSquarePercentile pSquarePercentile = new PSquarePercentile(50);
        ApproximatePercentile p = new ApproximatePercentile(0.5);
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(PercentileTest.class.getResourceAsStream("sunspot.dat")));
        long index = 1;
        while ((line = br.readLine()) != null) {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            int year = Integer.parseInt(tokenizer.nextToken());
            float value = Float.parseFloat(tokenizer.nextToken());
            pSquarePercentile.increment(value);
            p.add(value);
        }
        assertEquals(pSquarePercentile.getResult(), p.result(), 1e-6);
    }
}