/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2018, Ifsttar
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

package org.noise_planet.jwarble;

/**
 * Hamming 12,8 algorithm . On 12 bits transfer, 8 bits are for data and 4 bits for error correcting code
 * @see "https://github.com/RobotRoom/Hamming"
 * @author nicolas
 */
public class Hamming12_8 {
    private static final byte[] parity128 = new byte[] {
                0,  3,  5,  6,  6,  5,  3,  0,  7,  4,  2,  1,  1,  2,  4,  7,
                9, 10, 12, 15, 15, 12, 10,  9, 14, 13, 11,  8,  8, 11, 13, 14,
                10,  9, 15, 12, 12, 15,  9, 10, 13, 14,  8, 11, 11,  8, 14, 13,
                3,  0,  6,  5,  5,  6,  0,  3,  4,  7,  1,  2,  2,  1,  7,  4,
                11,  8, 14, 13, 13, 14,  8, 11, 12, 15,  9, 10, 10,  9, 15, 12,
                2,  1,  7,  4,  4,  7,  1,  2,  5,  6,  0,  3,  3,  0,  6,  5,
                1,  2,  4,  7,  7,  4,  2,  1,  6,  5,  3,  0,  0,  3,  5,  6,
                8, 11, 13, 14, 14, 13, 11,  8, 15, 12, 10,  9,  9, 10, 12, 15,
                12, 15,  9, 10, 10,  9, 15, 12, 11,  8, 14, 13, 13, 14,  8, 11,
                5,  6,  0,  3,  3,  0,  6,  5,  2,  1,  7,  4,  4,  7,  1,  2,
                6,  5,  3,  0,  0,  3,  5,  6,  1,  2,  4,  7,  7,  4,  2,  1,
                15, 12, 10,  9,  9, 10, 12, 15,  8, 11, 13, 14, 14, 13, 11,  8,
                7,  4,  2,  1,  1,  2,  4,  7,  0,  3,  5,  6,  6,  5,  3,  0,
                14, 13, 11,  8,  8, 11, 13, 14,  9, 10, 12, 15, 15, 12, 10,  9,
                13, 14,  8, 11, 11,  8, 14, 13, 10,  9, 15, 12, 12, 15,  9, 10,
                4,  7,  1,  2,  2,  1,  7,  4,  3,  0,  6,  5,  5,  6,  0,  3,
    };

    private static final byte NO_ERROR = 0x00;
    private static final byte ERROR_IN_PARITY = (byte)0xFE;
    private static final byte UNCORRECTABLE = (byte) 0xFF;
    private static final byte[] correct128Syndrome = new byte[]
    {
                NO_ERROR,			// 0
                ERROR_IN_PARITY,	// 1
                ERROR_IN_PARITY,	// 2
                0x01,				// 3
                ERROR_IN_PARITY,	// 4
                0x02,				// 5
                0x04,				// 6
                0x08,				// 7
                ERROR_IN_PARITY,	// 8
                0x10,				// 9
                0x20,				// 10
                0x40,				// 11
                (byte)0x80,  		// 12
                UNCORRECTABLE,		// 13
                UNCORRECTABLE,		// 14
                UNCORRECTABLE,		// 15
    };

    private static CorrectResult Correct128Syndrome(Byte value, byte syndrome)
    {
        // Using only the lower nibble (& 0x0F), look up the bit
        // to correct in a table
        byte correction = correct128Syndrome[syndrome & 0x0F];

        if (correction != NO_ERROR)
        {
            if (correction == UNCORRECTABLE || value == null)
            {
                return new CorrectResult(CorrectResultCode.FAIL_CORRECTION, (byte)0); // Non-recoverable error
            }
            else
            {
                if ( correction != ERROR_IN_PARITY)
                {
                    return new CorrectResult(CorrectResultCode.CORRECTED_ERROR, (byte)(value^correction));
                } else {
                    return new CorrectResult(CorrectResultCode.CORRECTED_ERROR, value);
                }

            }
        }

        return new CorrectResult(CorrectResultCode.NO_ERRORS, value);
    }

    public static int encode(byte value) {
        return value << 4 | parity128[value - Byte.MIN_VALUE];
    }

    public static CorrectResult decode(int value) {
        return Correct128((byte)(value >> 4), (byte)(value & 0xF));
    }

    private static CorrectResult Correct128(Byte value, byte parity) {
        if (value == null)
        {
            return new CorrectResult(CorrectResultCode.FAIL_CORRECTION, (byte)0); // Non-recoverable error
        }

        final byte syndrome = (byte)(parity128[value - Byte.MIN_VALUE] ^ parity);

        if (syndrome != 0)
        {
            return Correct128Syndrome(value, syndrome);
        }

        return new CorrectResult(CorrectResultCode.NO_ERRORS, value);
    }

    public enum CorrectResultCode {NO_ERRORS, CORRECTED_ERROR, FAIL_CORRECTION}

    public final static class CorrectResult {
        public final CorrectResultCode result;
        public final byte value;

        public CorrectResult(CorrectResultCode result, byte value) {
            this.result = result;
            this.value = value;
        }
    }
}
