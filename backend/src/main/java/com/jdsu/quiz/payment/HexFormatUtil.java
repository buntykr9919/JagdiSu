package com.jdsu.quiz.payment;

public final class HexFormatUtil {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HexFormatUtil() {
    }

    public static String toHex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            output[i * 2] = HEX[value >>> 4];
            output[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(output);
    }
}
