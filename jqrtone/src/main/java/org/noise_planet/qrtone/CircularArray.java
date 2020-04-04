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

import java.util.AbstractList;

/**
 * Keep fixed sized array of element, without moving elements at each insertion
 */
public class CircularArray extends AbstractList<Float> {
    private float[] values;
    private int cursor = 0;
    private int inserted = 0;

    public CircularArray(int size) {
        values = new float[size];
    }

    @Override
    public Float get(int index) {
        int cicularIndex = cursor - inserted + index;
        if (cicularIndex < 0) {
            cicularIndex += values.length;
        }
        return values[cicularIndex];
    }

    @Override
    public void clear() {
        cursor = 0;
        inserted = 0;
    }

    public Float last() {
        if(inserted == 0) {
            return null;
        }
        return get(size() - 1);
    }

    @Override
    public boolean add(Float value) {
        values[cursor] = value;
        cursor += 1;
        if(cursor == values.length) {
            cursor = 0;
        }
        inserted = Math.min(values.length, inserted + 1);
        return true;
    }

    @Override
    public int size() {
        return inserted;
    }
}
