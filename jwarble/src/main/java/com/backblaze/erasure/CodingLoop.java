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

public interface CodingLoop {

    /**
     * All of the available coding loop algorithms.
     *
     * The different choices nest the three loops in different orders,
     * and either use the log/exponents tables, or use the multiplication
     * table.
     *
     * The naming of the three loops is (with number of loops in benchmark):
     *
     *    "byte"   - Index of byte within shard.  (200,000 bytes in each shard)
     *
     *    "input"  - Which input shard is being read.  (17 data shards)
     *
     *    "output"  - Which output shard is being computed.  (3 parity shards)
     *
     * And the naming for multiplication method is:
     *
     *    "table"  - Use the multiplication table.
     *
     *    "exp"    - Use the logarithm/exponent table.
     *
     * The ReedSolomonBenchmark class compares the performance of the different
     * loops, which will depend on the specific processor you're running on.
     *
     * This is the inner loop.  It needs to be fast.  Be careful
     * if you change it.
     *
     * I have tried inlining Galois.multiply(), but it doesn't
     * make things any faster.  The JIT compiler is known to inline
     * methods, so it's probably already doing so.
     */
    CodingLoop[] ALL_CODING_LOOPS =
            new CodingLoop[] {
                    new ByteInputOutputExpCodingLoop(),
                    new ByteInputOutputTableCodingLoop(),
                    new ByteOutputInputExpCodingLoop(),
                    new ByteOutputInputTableCodingLoop(),
                    new InputByteOutputExpCodingLoop(),
                    new InputByteOutputTableCodingLoop(),
                    new InputOutputByteExpCodingLoop(),
                    new InputOutputByteTableCodingLoop(),
                    new OutputByteInputExpCodingLoop(),
                    new OutputByteInputTableCodingLoop(),
                    new OutputInputByteExpCodingLoop(),
                    new OutputInputByteTableCodingLoop(),
            };

    /**
     * Multiplies a subset of rows from a coding matrix by a full set of
     * input shards to produce some output shards.
     *
     * @param matrixRows The rows from the matrix to use.
     * @param inputs An array of byte arrays, each of which is one input shard.
     *               The inputs array may have extra buffers after the ones
     *               that are used.  They will be ignored.  The number of
     *               inputs used is determined by the length of the
     *               each matrix row.
     * @param inputCount The number of input byte arrays.
     * @param outputs Byte arrays where the computed shards are stored.  The
     *                outputs array may also have extra, unused, elements
     *                at the end.  The number of outputs computed, and the
     *                number of matrix rows used, is determined by
     *                outputCount.
     * @param outputCount The number of outputs to compute.
     * @param offset The index in the inputs and output of the first byte
     *               to process.
     * @param byteCount The number of bytes to process.
     */
     void codeSomeShards(final byte [] [] matrixRows,
                         final byte [] [] inputs,
                         final int inputCount,
                         final byte [] [] outputs,
                         final int outputCount,
                         final int offset,
                         final int byteCount);

    /**
     * Multiplies a subset of rows from a coding matrix by a full set of
     * input shards to produce some output shards, and checks that the
     * the data is those shards matches what's expected.
     *
     * @param matrixRows The rows from the matrix to use.
     * @param inputs An array of byte arrays, each of which is one input shard.
     *               The inputs array may have extra buffers after the ones
     *               that are used.  They will be ignored.  The number of
     *               inputs used is determined by the length of the
     *               each matrix row.
     * @param inputCount THe number of input byte arrays.
     * @param toCheck Byte arrays where the computed shards are stored.  The
     *                outputs array may also have extra, unused, elements
     *                at the end.  The number of outputs computed, and the
     *                number of matrix rows used, is determined by
     *                outputCount.
     * @param checkCount The number of outputs to compute.
     * @param offset The index in the inputs and output of the first byte
     *               to process.
     * @param byteCount The number of bytes to process.
     * @param tempBuffer A place to store temporary results.  May be null.
     */
     boolean checkSomeShards(final byte [] [] matrixRows,
                             final byte [] [] inputs,
                             final int inputCount,
                             final byte [] [] toCheck,
                             final int checkCount,
                             final int offset,
                             final int byteCount,
                             final byte [] tempBuffer);
}
