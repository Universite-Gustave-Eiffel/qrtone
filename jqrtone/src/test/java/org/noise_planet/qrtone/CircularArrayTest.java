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
        a.clear();
        assertEquals(0, a.size());
    }

}