package org.noise_planet.qrtone;

import org.junit.Test;

import static org.junit.Assert.*;

public class CRC16Test {

    @Test
    public void testRef() {
        byte[] values = new byte[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'};
        CRC16 crc16 = new CRC16();
        for(byte b : values) {
            crc16.add(b);
        }
        assertEquals(0x0C9E, crc16.crc());
    }
}