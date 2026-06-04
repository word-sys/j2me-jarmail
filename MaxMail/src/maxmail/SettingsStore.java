package maxmail;

import javax.microedition.rms.*;

public class SettingsStore {
    private static final String STORE_NAME = "MaxMailSettings";

    public static String loadServerUrl(String defaultUrl) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                if (data != null && data.length > 0) {
                    return new String(data);
                }
            }
        } catch (Exception e) {
            System.err.println("RMS load error: " + e.getMessage());
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
        return defaultUrl;
    }

    public static void saveServerUrl(String url) {
        if (url == null) return;
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            byte[] data = url.getBytes();
            if (rs.getNumRecords() > 0) {
                rs.setRecord(1, data, 0, data.length);
            } else {
                rs.addRecord(data, 0, data.length);
            }
        } catch (Exception e) {
            System.err.println("RMS save error: " + e.getMessage());
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }

    private static final String AUTH_STORE = "MaxMailAuth";

    /**
     * Load the device pairing token from persistent storage.
     * @return the 64-char hex token, or null if not paired
     */
    public static String loadPairToken() {
        return loadAuthRecord(1);
    }

    /**
     * Save the device pairing token to persistent storage.
     * @param token the 64-char hex token from server pairing
     */
    public static void savePairToken(String token) {
        saveAuthRecord(1, token);
    }

    /**
     * Load the session token from persistent storage.
     * @return the session token, or null if no active session
     */
    public static String loadSessionToken() {
        return loadAuthRecord(2);
    }

    /**
     * Save the current session token to persistent storage.
     * Pass null to clear the stored session.
     * @param token the session token from server login
     */
    public static void saveSessionToken(String token) {
        saveAuthRecord(2, token);
    }


    public static void clearAll() {
        RecordStore rs = null;
        try {
            RecordStore.deleteRecordStore(AUTH_STORE);
        } catch (Exception e) {
        }
    }

    private static String loadAuthRecord(int recordNum) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(AUTH_STORE, true);
            if (rs.getNumRecords() >= recordNum) {
                byte[] data = rs.getRecord(recordNum);
                if (data != null && data.length > 0) {
                    return new String(data);
                }
            }
        } catch (Exception e) {
            System.err.println("RMS auth load error: " + e.getMessage());
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
        return null;
    }

    private static void saveAuthRecord(int recordNum, String value) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(AUTH_STORE, true);
            byte[] data = (value != null) ? value.getBytes() : new byte[0];

            while (rs.getNumRecords() < recordNum) {
                rs.addRecord(new byte[0], 0, 0);
            }

            rs.setRecord(recordNum, data, 0, data.length);
        } catch (Exception e) {
            System.err.println("RMS auth save error: " + e.getMessage());
        } finally {
            if (rs != null) {
                try { rs.closeRecordStore(); } catch (Exception e) {}
            }
        }
    }
}
