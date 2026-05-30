package maxmail;

public class NetworkHelper {
    // Magic string for BlackBerry OS 7.1 to bypass BES/MDS proxy
    public static final String BB_SUFFIX = ";deviceside=true";

    public static String makeBBUrl(String url) {
        return url + BB_SUFFIX;
    }

    public static String urlEncode(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        try {
            byte[] bytes = s.getBytes("UTF-8");
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') || 
                    b == '-' || b == '_' || b == '.' || b == '*') {
                    sb.append((char) b);
                } else if (b == ' ') {
                    sb.append('+');
                } else {
                    sb.append('%');
                    String hex = Integer.toHexString(b);
                    if (hex.length() < 2) {
                        sb.append('0');
                    }
                    sb.append(hex.toUpperCase());
                }
            }
        } catch (Exception e) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || 
                    c == '-' || c == '_' || c == '.') {
                    sb.append(c);
                } else if (c == ' ') {
                    sb.append('+');
                } else {
                    sb.append('%');
                    String hex = Integer.toHexString(c);
                    if (hex.length() < 2) sb.append('0');
                    sb.append(hex.toUpperCase());
                }
            }
        }
        return sb.toString();
    }
}
