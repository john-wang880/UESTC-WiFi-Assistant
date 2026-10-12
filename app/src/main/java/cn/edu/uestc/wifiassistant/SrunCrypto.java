package cn.edu.uestc.wifiassistant;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Wire compatibility with the campus Portal.js; this is NOT transport encryption. */
public final class SrunCrypto {
    static final String ALPHABET = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA";
    private SrunCrypto() {}

    public static String username(String account, String suffix) {
        String value = account.trim();
        if (!suffix.equals("@cmcc") && !suffix.equals("@dx")) throw new IllegalArgumentException("请选择移动或电信");
        if (value.endsWith(suffix)) value = value.substring(0, value.length() - suffix.length());
        if (value.isEmpty() || value.length() > 100 || value.indexOf('@') >= 0 || value.matches(".*[\\s\\p{Cntrl}].*"))
            throw new IllegalArgumentException("账号格式不正确，请输入学号或所选运营商对应的完整账号");
        return value + suffix;
    }

    public static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    public static String infoJson(String user, String password, String ip, String acid) {
        return "{\"username\":" + quote(user) + ",\"password\":" + quote(password)
                + ",\"ip\":" + quote(ip) + ",\"acid\":" + quote(acid) + ",\"enc_ver\":\"srun_bx1\"}";
    }

    public static String hmacMd5(String password, String token) throws GeneralSecurityException {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacMD5");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacMD5"));
            return hex(mac.doFinal(bytes));
        } finally { Arrays.fill(bytes, (byte) 0); }
    }

    public static String sha1(String value) throws GeneralSecurityException {
        return hex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hex(byte[] value) {
        StringBuilder b = new StringBuilder();
        for (byte v : value) b.append(String.format(java.util.Locale.ROOT, "%02x", v & 255));
        return b.toString();
    }

    private static int[] words(String value, boolean length) {
        int count = (value.length() + 3) / 4;
        int[] words = new int[count + (length ? 1 : 0)];
        for (int i = 0; i < value.length(); i++) words[i / 4] |= ((int) value.charAt(i)) << ((i % 4) * 8);
        if (length) words[count] = value.length();
        return words;
    }

    /** Exact JavaScript UTF-16 code-unit and unsigned shift behavior, including non-ASCII. */
    public static byte[] xencode(String value, String token) {
        if (value.isEmpty()) return new byte[0];
        int[] v = words(value, true);
        int[] k = Arrays.copyOf(words(token, false), Math.max(4, (token.length() + 3) / 4));
        int n = v.length - 1, z = v[n], sum = 0;
        int rounds = 6 + 52 / v.length;
        while (rounds-- > 0) {
            sum += 0x9E3779B9;
            int e = (sum >>> 2) & 3;
            for (int p = 0; p < n; p++) {
                int y = v[p + 1];
                int m = (z >>> 5) ^ (y << 2);
                m += ((y >>> 3) ^ (z << 4)) ^ (sum ^ y);
                m += k[(p & 3) ^ e] ^ z;
                z = v[p] += m;
            }
            int y = v[0];
            int m = (z >>> 5) ^ (y << 2);
            m += ((y >>> 3) ^ (z << 4)) ^ (sum ^ y);
            m += k[(n & 3) ^ e] ^ z;
            z = v[n] += m;
        }
        byte[] result = new byte[v.length * 4];
        for (int i = 0; i < result.length; i++) result[i] = (byte) (v[i / 4] >>> ((i % 4) * 8));
        Arrays.fill(v, 0); Arrays.fill(k, 0);
        return result;
    }

    public static String base64(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bytes.length; i += 3) {
            int a = bytes[i] & 255;
            int b = i + 1 < bytes.length ? bytes[i + 1] & 255 : 0;
            int c = i + 2 < bytes.length ? bytes[i + 2] & 255 : 0;
            out.append(ALPHABET.charAt(a >>> 2)).append(ALPHABET.charAt(((a & 3) << 4) | (b >>> 4)));
            out.append(i + 1 < bytes.length ? ALPHABET.charAt(((b & 15) << 2) | (c >>> 6)) : '=');
            out.append(i + 2 < bytes.length ? ALPHABET.charAt(c & 63) : '=');
        }
        return out.toString();
    }

    public static Map<String, String> loginParameters(String user, char[] secret, String ip, String acid, String token)
            throws GeneralSecurityException {
        // Strings needed by Java crypto/JSON cannot be reliably wiped; never store or log them.
        String password = new String(secret);
        String md5 = hmacMd5(password, token);
        byte[] encoded = xencode(infoJson(user, password, ip, acid), token);
        String info;
        try { info = "{SRBX1}" + base64(encoded); } finally { Arrays.fill(encoded, (byte) 0); }
        String checksum = sha1(token + user + token + md5 + token + acid + token + ip + token + "200" + token + "1" + token + info);
        Map<String, String> p = new LinkedHashMap<>();
        p.put("action", "login"); p.put("username", user); p.put("password", "{MD5}" + md5);
        p.put("ac_id", acid); p.put("ip", ip); p.put("chksum", checksum); p.put("info", info);
        p.put("n", "200"); p.put("type", "1"); p.put("os", "Android"); p.put("name", "Android"); p.put("double_stack", "0");
        return p;
    }
}
