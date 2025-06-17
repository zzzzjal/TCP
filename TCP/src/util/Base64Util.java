package util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Base64Util {
    private Base64Util() {} // Prevents instantiation

    public static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static String encode(String text) {
        return encode(text.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decode(String base64String) {
        return Base64.getDecoder().decode(base64String);
    }

    public static String decodeToString(String base64String) {
        return new String(decode(base64String), StandardCharsets.UTF_8);
    }

    public static boolean isValidBase64(String str) {
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}