package maxmail;

import java.util.Hashtable;
import java.util.Vector;

public class MiniJSON {

    public static final Object JSON_NULL = new Object();

    public static Object parse(String jsonString) throws Exception {
        if (jsonString == null) return null;
        int[] pos = new int[]{0}; 
        return parseValue(jsonString, pos);
    }

    private static Object parseValue(String json, int[] pos) throws Exception {
        skipWhitespace(json, pos);
        if (pos[0] >= json.length()) return null;

        char c = json.charAt(pos[0]);

        if (c == '"') return parseString(json, pos);
        else if (c == '{') return parseObject(json, pos);
        else if (c == '[') return parseArray(json, pos);
        else return parseLiteral(json, pos); 
    }

    private static Hashtable parseObject(String json, int[] pos) throws Exception {
        Hashtable obj = new Hashtable();
        pos[0]++; 
        skipWhitespace(json, pos);
        
        if (pos[0] < json.length() && json.charAt(pos[0]) == '}') {
            pos[0]++;
            return obj; 
        }

        while (pos[0] < json.length()) {
            skipWhitespace(json, pos);
            String key = parseString(json, pos);
            
            skipWhitespace(json, pos);
            if (pos[0] >= json.length() || json.charAt(pos[0]) != ':') throw new Exception("Expected ':'");
            pos[0]++; 
            
            Object value = parseValue(json, pos);
            if (value == null) value = JSON_NULL;
            obj.put(key, value);
            
            skipWhitespace(json, pos);
            if (pos[0] >= json.length()) throw new Exception("Unterminated object");
            char next = json.charAt(pos[0]);
            if (next == '}') {
                pos[0]++;
                break;
            } else if (next == ',') {
                pos[0]++;
            } else {
                throw new Exception("Expected ',' or '}'");
            }
        }
        return obj;
    }

    private static Vector parseArray(String json, int[] pos) throws Exception {
        Vector arr = new Vector();
        pos[0]++; 
        skipWhitespace(json, pos);
        
        if (pos[0] < json.length() && json.charAt(pos[0]) == ']') {
            pos[0]++;
            return arr; 
        }

        while (pos[0] < json.length()) {
            Object value = parseValue(json, pos);
            if (value == null) value = JSON_NULL;
            arr.addElement(value);
            
            skipWhitespace(json, pos);
            if (pos[0] >= json.length()) throw new Exception("Unterminated array");
            char next = json.charAt(pos[0]);
            if (next == ']') {
                pos[0]++;
                break;
            } else if (next == ',') {
                pos[0]++;
            } else {
                throw new Exception("Expected ',' or ']'");
            }
        }
        return arr;
    }

    private static String parseString(String json, int[] pos) throws Exception {
        pos[0]++; 
        StringBuffer sb = new StringBuffer();
        int len = json.length();
        while (pos[0] < len) {
            char c = json.charAt(pos[0]++);
            if (c == '"') return sb.toString();
            else if (c == '\\') {
                if (pos[0] >= len) throw new Exception("Unterminated escape sequence");
                char next = json.charAt(pos[0]++);
                if (next == 'n') sb.append('\n');
                else if (next == 'r') sb.append('\r');
                else if (next == 't') sb.append('\t');
                else if (next == '"') sb.append('"');
                else if (next == '\\') sb.append('\\');
                else sb.append(next);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Object parseLiteral(String json, int[] pos) {
        StringBuffer sb = new StringBuffer();
        int len = json.length();
        while (pos[0] < len) {
            char c = json.charAt(pos[0]);
            if (c == ',' || c == '}' || c == ']' || c <= ' ') break;
            sb.append(c);
            pos[0]++;
        }
        String s = sb.toString();
        if (s.equals("true")) return new Boolean(true);
        if (s.equals("false")) return new Boolean(false);
        if (s.equals("null")) return JSON_NULL;
        return s; 
    }

    private static void skipWhitespace(String json, int[] pos) {
        int len = json.length();
        while (pos[0] < len && json.charAt(pos[0]) <= ' ') pos[0]++;
    }
}
