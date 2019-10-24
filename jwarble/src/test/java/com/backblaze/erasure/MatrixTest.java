/**
 * One specific ordering/nesting of the coding loops.
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Backblaze
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.backblaze.erasure;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MatrixTest {

    @Test
    public void testIdentity() {
        assertEquals(
                "[[1, 0, 0], [0, 1, 0], [0, 0, 1]]",
                Matrix.identity(3).toString()
        );
    }

    @Test
    public void testBigString() {
        assertEquals("01 00 \n00 01 \n", Matrix.identity(2).toBigString());
    }

    @Test
    public void testMultiply() {
        Matrix m1 = new Matrix(
                new byte [] [] {
                        new byte [] { 1, 2 },
                        new byte [] { 3, 4 }
                });
        Matrix m2 = new Matrix(
                new byte [] [] {
                        new byte [] { 5, 6 },
                        new byte [] { 7, 8 }
                });
        Matrix actual = m1.times(m2);
        // correct answer from java_tables.py
        assertEquals("[[11, 22], [19, 42]]", actual.toString());
    }

    @Test
    public void inverse() {
        Matrix m = new Matrix(
                new byte [] [] {
                    new byte [] { 56, 23, 98 },
                    new byte [] { 3, 100, (byte)200 },
                    new byte [] { 45, (byte)201, 123 }
                });
        assertEquals(
                "[[175, 133, 33], [130, 13, 245], [112, 35, 126]]",
                m.invert().toString()
        );
        assertEquals(
                Matrix.identity(3).toString(),
                m.times(m.invert()).toString()
        );
    }

    @Test
    public void inverse2() {
        Matrix m = new Matrix(
            new byte [] [] {
                new byte [] { 1, 0, 0, 0, 0 },
                new byte [] { 0, 1, 0, 0, 0 },
                new byte [] { 0, 0, 0, 1, 0 },
                new byte [] { 0, 0, 0, 0, 1 },
                new byte [] { 7, 7, 6, 6, 1 }
            }
        );
        assertEquals(
                "[[1, 0, 0, 0, 0]," +
                " [0, 1, 0, 0, 0]," +
                " [123, 123, 1, 122, 122]," +
                " [0, 0, 1, 0, 0]," +
                " [0, 0, 0, 1, 0]]",
                m.invert().toString()
        );
        assertEquals(
                Matrix.identity(5).toString(),
                m.times(m.invert()).toString()
        );
    }
}
