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
}
