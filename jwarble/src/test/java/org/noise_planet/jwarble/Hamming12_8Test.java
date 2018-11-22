package org.noise_planet.jwarble;

import org.junit.Test;

import static org.junit.Assert.*;

public class Hamming12_8Test {

    @Test
    public void testNoErrors() throws Exception {
        byte[] message = "Hello".getBytes();

        int[] encoded = new int[message.length];

        for(int i=0; i<message.length; i++) {
            encoded[i] = Hamming12_8.encode(message[i]);
        }

        // Do nothing

        // Check
        byte[] decoded_message = new byte[encoded.length];


        for(int i=0; i<encoded.length; i++) {
            Hamming12_8.CorrectResult correctResult = Hamming12_8.decode(encoded[i]);
            assertNotEquals(Hamming12_8.CorrectResultCode.CORRECTED_ERROR, correctResult.result);
            decoded_message[i] = correctResult.value;
        }

        assertArrayEquals(message, decoded_message);
    }


    @Test
    public void testErrorInPayload() throws Exception {
        byte[] message = "Hello".getBytes();

        int[] encoded = new int[message.length];

        for(int i=0; i<message.length; i++) {
            encoded[i] = Hamming12_8.encode(message[i]);
        }

        // Change bit
        //System.out.println(Integer.toBinaryString(encoded[1]));
        encoded[1] = encoded[1] ^ (1 << 6);
        //System.out.println(Integer.toBinaryString(encoded[1]));

        // Check
        byte[] decoded_message = new byte[encoded.length];


        for(int i=0; i<encoded.length; i++) {
            Hamming12_8.CorrectResult correctResult = Hamming12_8.decode(encoded[i]);
            assertNotEquals(Hamming12_8.CorrectResultCode.FAIL_CORRECTION, correctResult.result);
            decoded_message[i] = correctResult.value;
        }

        assertArrayEquals(message, decoded_message);
    }


    @Test
    public void testErrorInParity() throws Exception {
        byte[] message = "Hello".getBytes();

        int[] encoded = new int[message.length];

        for(int i=0; i<message.length; i++) {
            encoded[i] = Hamming12_8.encode(message[i]);
        }

        // Change bit
        encoded[1] = encoded[1] ^ (1 << 3);

        // Check
        byte[] decoded_message = new byte[encoded.length];


        for(int i=0; i<encoded.length; i++) {
            Hamming12_8.CorrectResult correctResult = Hamming12_8.decode(encoded[i]);
            assertNotEquals(Hamming12_8.CorrectResultCode.FAIL_CORRECTION, correctResult.result);
            decoded_message[i] = correctResult.value;
        }

        assertArrayEquals(message, decoded_message);
    }
}