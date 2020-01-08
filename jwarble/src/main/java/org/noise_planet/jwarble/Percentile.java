package org.noise_planet.jwarble;

import sun.security.util.ArrayUtil;

import java.util.Arrays;

/**
 * Optimized L90 implementation (for repeated insertion and query)
 * This class is keeping a bounded ordered list of values
 * Oldest values are removed from the list
 */
public class Percentile {
    private final double[] stack;
    private int stackSize;
    private final int[] indexes;
    private int index_cursor;

    public Percentile(int size) {
        stack = new double[size];
        indexes = new int[size];
        Arrays.fill(indexes, -1);
        stackSize = 0;
        index_cursor = 0;
    }

    public void add(double value) {
        if(stackSize == stack.length) {
            // Remove oldest value
            int oldIndex = indexes[index_cursor];
            if(oldIndex > 0) {
                // Overwrite the old value with new values
                // the first element is empty
                System.arraycopy(stack, 0, stack, 1, oldIndex);
                for(int i = 0; i < stackSize; i++) {
                    if(indexes[i] < oldIndex) {
                        indexes[i] += 1;
                    }
                }
            }
        }
        // Find new element insertion location
        int index = 0;
        if(stackSize > 0) {
            index = Arrays.binarySearch(stack, stackSize == stack.length ? 1 : 0, stackSize, value);
            if (index < 0) {
                index = (-(index) - 1);
            }
            if(stackSize == stack.length) {
                index -= 1;
                // Move lowest elements to the left
                if (index > 1) {
                    System.arraycopy(stack, 1, stack, 0, index);
                    for(int i = 0; i < stackSize; i++) {
                        if(indexes[i] <= index) {
                            indexes[i] -= 1;
                        }
                    }
                }
            } else {
                if(index < stackSize) {
                    // Move elements to the right
                    System.arraycopy(stack, index, stack, index + 1, stackSize - index);
                    for(int i = 0; i < stackSize; i++) {
                        if(indexes[i] >= index) {
                            indexes[i] += 1;
                        }
                    }
                }
            }
        }
        stack[index] = value;
        indexes[index_cursor] = index;
        index_cursor += 1;
        if(index_cursor == stack.length) {
            index_cursor = 0;
        }
        if(stackSize < stack.length) {
            stackSize += 1;
        }
    }

    public int getPercentileRank(double percentile) {
        int rank = Math.max(0, Math.min((int) Math.round(stackSize * percentile - 1), stackSize - 1));
        return indexes[rank];
    }

    /**
     *
     * @param percentile (0-1]
     * @return
     */
    public double getPercentile(double percentile) {
        if(stackSize % (1 / percentile) > 0) {
            int rank = Math.max(0, Math.min((int) Math.round(stackSize * percentile - 1), stackSize - 1));
            return stack[rank];
        } else {
            int rank = Math.max(0, Math.min((int) Math.round(stackSize * percentile - 1), stackSize - 1));
            return (stack[rank] + stack[rank + 1]) / 2.0;
        }
    }
}
