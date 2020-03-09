package org.noise_planet.qrtone;

import org.junit.Test;

import static org.junit.Assert.*;

public class CircularArrayTest {

    @Test
    public void testAddGet() {
        CircularArray a = new CircularArray(5);
        assertEquals(0, a.size());
        a.add(0.5f);
        assertEquals(1, a.size());
        assertEquals(0.5f, a.get(a.size() - 1), 1e-6);
        a.add(0.1f);
        assertEquals(2, a.size());
        assertEquals(0.5f, a.get(a.size() - 2), 1e-6);
        assertEquals(0.1f, a.get(a.size() - 1), 1e-6);
        a.add(0.2f);
        assertEquals(3, a.size());
        a.add(0.3f);
        assertEquals(4, a.size());
        a.add(0.7f);
        assertEquals(5, a.size());
        a.add(0.9f);
        assertEquals(5, a.size());
        assertEquals(0.9f, a.get(a.size() - 1), 1e-6);
        assertEquals(0.7f, a.get(a.size() - 2), 1e-6);
        assertEquals(0.3f, a.get(a.size() - 3), 1e-6);
        assertEquals(0.2f, a.get(a.size() - 4), 1e-6);
        assertEquals(0.1f, a.get(a.size() - 5), 1e-6);
    }

}