package org.noise_planet.qrtone;

/**
 * Generate tone wave without using sin function at each sample
 * @ref http://ww1.microchip.com/downloads/en/appnotes/00543c.pdf https://ipfs.io/ipfs/QmdfpU2ziBrEg1WgzBXFqvrgRNse7btHoyVCfw8cEv5qgU
 */
public class IterativeTone {
    final double k1;
    final double originalK2;
    double k2;
    double k3;
    long index = 0;

    public IterativeTone(double frequency, double sampleRate) {
        double ffs = frequency / sampleRate;
        k1 = 2 * Math.cos(QRTone.M2PI * ffs);
        originalK2 = Math.sin(QRTone.M2PI * ffs);
        reset();
    }

    public void reset() {
        index = 0;
        k2 = originalK2;
        k3 = 0;
    }

    /**
     * Next sample value
     * @return Sample value [-1;1]
     */
    double next() {
        if(index >= 2) {
            double tmp = k2;
            k2 = k1 * k2 - k3;
            k3 = tmp;
            index++;
            return k2;
        } else if(index == 1) {
            index++;
            return k2;
        } else {
            index++;
            return 0;
        }
    }
}
