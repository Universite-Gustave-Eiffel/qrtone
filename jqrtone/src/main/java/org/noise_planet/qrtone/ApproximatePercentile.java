package org.noise_planet.qrtone;

import java.util.Arrays;

/**
 * P^2 algorithm as documented in "The P-Square Algorithm for Dynamic Calculation of Percentiles and Histograms
 * without Storing Observations," Communications of the ACM, October 1985 by R. Jain and I. Chlamtac.
 * Converted from Aaron Small C code under MIT License
 * https://github.com/absmall/p2
 *
 * @author Aaron Small
 */
public class ApproximatePercentile {
    private double[] q;
    private double[] dn;
    private double[] np;
    private int[] n;
    private int count;
    private int marker_count;

    public ApproximatePercentile() {
        count = 0;
        addEndMarkers();
    }

    public ApproximatePercentile(double quant) {
        count = 0;
        addEndMarkers();
        addQuantile(quant);
    }

    public void addEqualSpacing(int count) {
        int index = allocateMarkers(count - 1);

        /* Add in appropriate dn markers */
        for (int i = index + 1; i < count; i++) {
            dn[i - 1] = 1.0 * i / count;
        }

        updateMarkers();
    }


    public void addQuantile(double quant) {
        int index = allocateMarkers(3);

        /* Add in appropriate dn markers */
        dn[index] = quant/2.0;
        dn[index + 1] = quant;
        dn[index + 2] = (1.0 + quant) / 2.0;

        updateMarkers();
    }


    private int allocateMarkers(int count) {
        q = Arrays.copyOf(q, marker_count + count);
        dn = Arrays.copyOf(dn, marker_count + count);
        np = Arrays.copyOf(np, marker_count + count);
        n = Arrays.copyOf(n, marker_count + count);

        marker_count += count;

        return marker_count - count;
    }

    private void addEndMarkers() {
        marker_count = 2;
        q = new double[marker_count];
        dn = new double[marker_count];
        np = new double[marker_count];
        n = new int[marker_count];
        dn[0] = 0.0;
        dn[1] = 1.0;

        updateMarkers();
    }

    private void updateMarkers() {
        sort(dn);

        /* Then entirely reset np markers, since the marker count changed */
        for (int i = 0; i < marker_count; i++) {
            np[i] = (marker_count - 1) * dn[i] + 1;
        }
    }

    /**
     * Simple bubblesort, because bubblesort is efficient for small count, and count is likely to be small
     */
    private void sort(double[] q) {
        double k;
        int i, j;
        for (j = 1; j < q.length; j++) {
            k = q[j];
            i = j - 1;

            while (i >= 0 && q[i] > k) {
                q[i + 1] = q[i];
                i--;
            }
            q[i + 1] = k;
        }
    }

    private double parabolic(int i, int d) {
        return q[i] + d / (double) (n[i + 1] - n[i - 1]) * ((n[i] - n[i - 1] + d) * (q[i + 1] - q[i]) /
                (n[i + 1] - n[i]) + (n[i + 1] - n[i] - d) * (q[i] - q[i - 1]) / (n[i] - n[i - 1]));
    }

    private double linear(int i, int d) {
        return q[i] + d * (q[i + d] - q[i]) / (n[i + d] - n[i]);
    }

    public void add(double data) {
        int i;
        int k = 0;
        double d;
        double newq;

        if (count >= marker_count) {
            count++;

            // B1
            if (data < q[0]) {
                q[0] = data;
                k = 1;
            } else if (data >= q[marker_count - 1]) {
                q[marker_count - 1] = data;
                k = marker_count - 1;
            } else {
                for (i = 1; i < marker_count; i++) {
                    if (data < q[i]) {
                        k = i;
                        break;
                    }
                }
            }

            // B2
            for (i = k; i < marker_count; i++) {
                n[i]++;
                np[i] = np[i] + dn[i];
            }
            for (i = 0; i < k; i++) {
                np[i] = np[i] + dn[i];
            }

            // B3
            for (i = 1; i < marker_count - 1; i++) {
                d = np[i] - n[i];
                if ((d >= 1.0 && n[i + 1] - n[i] > 1) || (d <= -1.0 && n[i - 1] - n[i] < -1.0)) {
                    newq = parabolic(i, sign(d));
                    if (q[i - 1] < newq && newq < q[i + 1]) {
                        q[i] = newq;
                    } else {
                        q[i] = linear(i, sign(d));
                    }
                    n[i] += sign(d);
                }
            }
        } else {
            q[count] = data;
            count++;

            if (count == marker_count) {
                // We have enough to start the algorithm, initialize
                sort(q);

                for (i = 0; i < marker_count; i++) {
                    n[i] = i + 1;
                }
            }
        }
    }

    public double result() {
        if (marker_count != 5) {
            throw new IllegalStateException("Multiple quantiles in use");
        }
        return result(dn[(marker_count - 1) / 2]);
    }

    public double result(double quantile) {
        if (count < marker_count) {
            int closest = 1;
            sort(q);
            for (int i = 2; i < count; i++) {
                if (Math.abs(((double) i) / count - quantile) < Math.abs(((double) closest) / marker_count - quantile)) {
                    closest = i;
                }
            }
            return q[closest];
        } else {
            // Figure out which quantile is the one we're looking for by nearest dn
            int closest = 1;
            for (int i = 2; i < marker_count - 1; i++) {
                if (Math.abs(dn[i] - quantile) < Math.abs(dn[closest] - quantile)) {
                    closest = i;
                }
            }
            return q[closest];
        }
    }

    private static int sign(double d) {
        if (d >= 0.0) {
            return 1;
        } else {
            return -1;
        }
    }
}
