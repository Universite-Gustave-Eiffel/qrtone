package org.noise_planet.qrtone;

public class Header {
    public final int length;
    public final boolean crc;
    Configuration.ECC_LEVEL eccLevel = null;
    public final int payloadSymbolsSize;
    public final int payloadByteSize;
    public final int numberOfBlocks;
    public final int numberOfSymbols;

    public Header(int length, Configuration.ECC_LEVEL eccLevel, boolean crc) {
        this(length, Configuration.getTotalSymbolsForEcc(eccLevel), Configuration.getEccSymbolsForEcc(eccLevel), crc);
        this.eccLevel = eccLevel;
    }
    public Header(int length, final int blockSymbolsSize, final int blockECCSymbols, boolean crc) {
        this.length = length;
        int crcLength = 0;
        if(crc) {
            crcLength = QRTone.CRC_BYTE_LENGTH;
        }
        payloadSymbolsSize = blockSymbolsSize - blockECCSymbols;
        payloadByteSize = payloadSymbolsSize / 2;
        numberOfBlocks = (int)Math.ceil(((length + crcLength) * 2) / (double)payloadSymbolsSize);
        numberOfSymbols = numberOfBlocks * blockECCSymbols + ( length + crcLength) * 2;
        this.crc = crc;
    }

    public byte[] encodeHeader() {
        if(length > QRTone.MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(String.format("Payload length cannot be superior than %d bytes", QRTone.MAX_PAYLOAD_LENGTH));
        }
        // Generate header
        byte[] header = new byte[QRTone.HEADER_SIZE];
        // Payload length
        header[0] = (byte)(length & 0xFF);
        // ECC level
        header[1] = (byte)(0x03 & eccLevel.ordinal());
        // has crc
        if(crc) {
            header[1] = (byte) (header[1] | 0x01 << 3);
        }
        header[2] = QRTone.crc8(header, 0, QRTone.HEADER_SIZE - 1);
        return header;
    }

    public static Header decodeHeader(byte[] data) {
        // Check CRC
        byte crc = QRTone.crc8(data, 0, QRTone.HEADER_SIZE - 1);
        if(crc != data[QRTone.HEADER_SIZE - 1]){
            // CRC error
            return null;
        }
        return new Header(data[0] & 0xFF, Configuration.ECC_LEVEL.values()[data[1] & 0x03], ((data[1] >> 3) & 0x01) == 1);
    }

    public Configuration.ECC_LEVEL getEccLevel() {
        return eccLevel;
    }
}
