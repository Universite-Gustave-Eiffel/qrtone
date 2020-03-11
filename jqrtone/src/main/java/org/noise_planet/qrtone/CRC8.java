package org.noise_planet.qrtone;

public class CRC8 {
    private int crc8 = 0;

    void add(byte[] data, int from, int to) {
        for (int i=from; i < to; i++) {
            add(data[i]);
        }
    }

    void add(byte data) {
        int crc = 0;
        int accumulator = (crc8 ^ data) & 0x0FF;
        for (int j = 0; j < 8; j++) {
            if (((accumulator ^ crc) & 0x01) == 0x01) {
                crc = ((crc ^ 0x18) >> 1) | 0x80;
            } else {
                crc = crc >> 1;
            }
            accumulator = accumulator >> 1;
        }
        crc8 = (byte) crc;
    }

    byte crc() {
        return (byte) (crc8 & 0x0FF);
    }
}
