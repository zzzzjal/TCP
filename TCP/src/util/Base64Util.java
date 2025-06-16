package util;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

public final class Base64Util {
    private static final Base64.Encoder encoder = Base64.getEncoder();
    private static final Base64.Decoder decoder = Base64.getDecoder();

    private Base64Util() {} // 防止实例化

    public static String encode(String data) {
        if (data == null) {
            throw new IllegalArgumentException("输入数据不能为null");
        }
        return encoder.encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String base64Data) {
        if (base64Data == null) {
            throw new IllegalArgumentException("Base64数据不能为null");
        }
        return new String(decoder.decode(base64Data), StandardCharsets.UTF_8);
    }

    public static String encodeBytes(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("字节数组不能为null");
        }
        return encoder.encodeToString(data);
    }

    public static byte[] decodeToBytes(String base64Data) {
        if (base64Data == null) {
            throw new IllegalArgumentException("Base64数据不能为null");
        }
        return decoder.decode(base64Data);
    }
}