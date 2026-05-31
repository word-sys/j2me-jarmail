package maxmail;

import java.util.Calendar;
import java.util.TimeZone;

public class AuthHelper {
    private static final int[] K = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
        0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
        0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
        0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    private static final int[] H0 = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };

    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Compute SHA-256 hash of the input byte array.
     * Pure-Java implementation — no platform crypto dependencies.
     *
     * @param message the input bytes
     * @return 32-byte hash
     */
    public static byte[] sha256(byte[] message) {
        int msgLen = message.length;
        int bitLen = msgLen * 8;


        int padLen = 64 - ((msgLen + 9) % 64);
        if (padLen == 64) padLen = 0;
        int totalLen = msgLen + 1 + padLen + 8;

        byte[] padded = new byte[totalLen];
        System.arraycopy(message, 0, padded, 0, msgLen);
        padded[msgLen] = (byte) 0x80;

        padded[totalLen - 4] = (byte) (bitLen >>> 24);
        padded[totalLen - 3] = (byte) (bitLen >>> 16);
        padded[totalLen - 2] = (byte) (bitLen >>> 8);
        padded[totalLen - 1] = (byte) bitLen;

        int h0 = H0[0], h1 = H0[1], h2 = H0[2], h3 = H0[3];
        int h4 = H0[4], h5 = H0[5], h6 = H0[6], h7 = H0[7];

        int[] w = new int[64]; 
        for (int block = 0; block < totalLen; block += 64) {
            for (int i = 0; i < 16; i++) {
                int j = block + i * 4;
                w[i] = ((padded[j] & 0xFF) << 24)
                     | ((padded[j + 1] & 0xFF) << 16)
                     | ((padded[j + 2] & 0xFF) << 8)
                     |  (padded[j + 3] & 0xFF);
            }
            for (int i = 16; i < 64; i++) {
                int s0 = ror(w[i - 15], 7) ^ ror(w[i - 15], 18) ^ (w[i - 15] >>> 3);
                int s1 = ror(w[i - 2], 17) ^ ror(w[i - 2], 19)  ^ (w[i - 2] >>> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }

            int a = h0, b = h1, c = h2, d = h3;
            int e = h4, f = h5, g = h6, h = h7;

            for (int i = 0; i < 64; i++) {
                int S1 = ror(e, 6) ^ ror(e, 11) ^ ror(e, 25);
                int ch = (e & f) ^ ((~e) & g);
                int temp1 = h + S1 + ch + K[i] + w[i];
                int S0 = ror(a, 2) ^ ror(a, 13) ^ ror(a, 22);
                int maj = (a & b) ^ (a & c) ^ (b & c);
                int temp2 = S0 + maj;

                h = g;
                g = f;
                f = e;
                e = d + temp1;
                d = c;
                c = b;
                b = a;
                a = temp1 + temp2;
            }

            h0 += a; h1 += b; h2 += c; h3 += d;
            h4 += e; h5 += f; h6 += g; h7 += h;
        }

        byte[] hash = new byte[32];
        intToBytes(h0, hash, 0);
        intToBytes(h1, hash, 4);
        intToBytes(h2, hash, 8);
        intToBytes(h3, hash, 12);
        intToBytes(h4, hash, 16);
        intToBytes(h5, hash, 20);
        intToBytes(h6, hash, 24);
        intToBytes(h7, hash, 28);
        return hash;
    }

    private static int ror(int v, int n) {
        return (v >>> n) | (v << (32 - n));
    }
    private static void intToBytes(int val, byte[] out, int off) {
        out[off]     = (byte) (val >>> 24);
        out[off + 1] = (byte) (val >>> 16);
        out[off + 2] = (byte) (val >>> 8);
        out[off + 3] = (byte) val;
    }

    /**
     * Convert a byte array to its lowercase hexadecimal string representation.
     *
     * @param bytes input bytes
     * @return hex string (2 chars per byte)
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            hex[i * 2]     = HEX[(bytes[i] >> 4) & 0x0F];
            hex[i * 2 + 1] = HEX[bytes[i] & 0x0F];
        }
        return new String(hex);
    }

    /**
     * Generate the 64-character authentication ID.
     * Computes SHA-256(token + minuteStr) and returns the hex digest.
     * This must produce the exact same output as the server's
     * crypto.createHash('sha256').update(token + minuteStr).digest('hex')
     *
     * @param token     the pairing token (64 hex chars stored on device)
     * @param minuteStr UTC time truncated to minute, e.g. "2026-05-31T14:04"
     * @return 64-character hex string
     */
    public static String generateAuthId(String token, String minuteStr) {
        String input = token + minuteStr;
        byte[] inputBytes;
        try {
            inputBytes = input.getBytes("UTF-8");
        } catch (Exception e) {
            inputBytes = input.getBytes();
        }
        byte[] hash = sha256(inputBytes);
        return bytesToHex(hash);
    }

    /**
     * Get the current UTC time truncated to the minute.
     * Format: "YYYY-MM-DDTHH:MM"
     * Must match the server's getMinuteStr(0) output exactly.
     *
     * @return formatted UTC minute string
     */
    public static String getUtcMinuteString() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        int year   = cal.get(Calendar.YEAR);
        int month  = cal.get(Calendar.MONTH) + 1;
        int day    = cal.get(Calendar.DAY_OF_MONTH);
        int hour   = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        StringBuffer sb = new StringBuffer(16);
        appendPad4(sb, year);
        sb.append('-');
        appendPad2(sb, month);
        sb.append('-');
        appendPad2(sb, day);
        sb.append('T');
        appendPad2(sb, hour);
        sb.append(':');
        appendPad2(sb, minute);
        return sb.toString();
    }

    private static void appendPad2(StringBuffer sb, int n) {
        if (n < 10) sb.append('0');
        sb.append(n);
    }

    private static void appendPad4(StringBuffer sb, int n) {
        if (n < 10) sb.append("000");
        else if (n < 100) sb.append("00");
        else if (n < 1000) sb.append('0');
        sb.append(n);
    }
}
