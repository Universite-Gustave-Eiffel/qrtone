package org.noise_planet.qrtone;

public class Complex {
    public final double r;
    public final double i;

    public Complex(double r, double i) {
        this.r = r;
        this.i = i;
    }

    Complex add(Complex c2) {
        return new Complex(r + c2.r, i + c2.i);
    }

    Complex sub(Complex c2) {
        return new Complex(r - c2.r, i - c2.i);
    }

    Complex mul(Complex c2) {
        return new Complex(r * c2.r - i * c2.i, r * c2.i + i * c2.r);
    }

    Complex exp() {
        return new Complex(Math.cos(r), -Math.sin(r));
    }
}
