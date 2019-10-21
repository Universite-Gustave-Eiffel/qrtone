package org.noise_planet.jwarble;

import org.junit.Test;

import static org.junit.Assert.*;

public class PercentileTest {

    @Test
    public void test1() {
        org.apache.commons.math3.stat.descriptive.rank.Percentile pref = new org.apache.commons.math3.stat.descriptive.rank.Percentile();
        Percentile p = new Percentile(5);
        p.add(15.25);
        assertEquals(pref.evaluate(new double[]{15.25}, 50), p.getPercentile(0.5), 1e-6);
        p.add(20);
        assertEquals(pref.evaluate(new double[]{15.25, 20}, 50), p.getPercentile(0.5), 1e-6);
        p.add(5);
        assertEquals(pref.evaluate(new double[]{15.25, 20, 5}, 50), p.getPercentile(0.5), 1e-6);
        p.add(1);
        assertEquals(pref.evaluate(new double[]{15.25, 20, 5, 1}, 50), p.getPercentile(0.5), 1e-6);
        p.add(25);
        assertEquals(pref.evaluate(new double[]{15.25, 20, 5, 1, 25}, 50), p.getPercentile(0.5), 1e-6);
        // Bounds limit
        p.add(12);
        assertEquals(pref.evaluate(new double[]{20, 5, 1, 25, 12}, 50), p.getPercentile(0.5), 1e-6);
        p.add(0.5);
        assertEquals(pref.evaluate(new double[]{5, 1, 25, 12, 0.5}, 50), p.getPercentile(0.5), 1e-6);
        p.add(28);
        assertEquals(pref.evaluate(new double[]{1, 25, 12, 0.5, 28}, 50), p.getPercentile(0.5), 1e-6);
    }

    @Test
    public void test2() {
        org.apache.commons.math3.stat.descriptive.rank.Percentile pref = new org.apache.commons.math3.stat.descriptive.rank.Percentile();
        Percentile p = new Percentile(5);
        p.add(20);
        assertEquals(pref.evaluate(new double[]{20}, 50), p.getPercentile(0.5), 1e-6);
        p.add(15);
        assertEquals(pref.evaluate(new double[]{20, 15}, 50), p.getPercentile(0.5), 1e-6);
        p.add(5);
        assertEquals(pref.evaluate(new double[]{20, 15, 5}, 50), p.getPercentile(0.5), 1e-6);
    }

    @Test
    public void test3() {
        org.apache.commons.math3.stat.descriptive.rank.Percentile pref = new org.apache.commons.math3.stat.descriptive.rank.Percentile();
        Percentile p = new Percentile(5);
        p.add(20);
        assertEquals(pref.evaluate(new double[]{20}, 50), p.getPercentile(0.5), 1e-6);
        p.add(25);
        assertEquals(pref.evaluate(new double[]{20, 25}, 50), p.getPercentile(0.5), 1e-6);
        p.add(21);
        assertEquals(pref.evaluate(new double[]{20, 25, 21}, 50), p.getPercentile(0.5), 1e-6);
        p.add(22);
        assertEquals(pref.evaluate(new double[]{20, 25, 21, 22}, 50), p.getPercentile(0.5), 1e-6);
        p.add(23);
        assertEquals(pref.evaluate(new double[]{20, 25, 21, 22, 23}, 50), p.getPercentile(0.5), 1e-6);
    }
}