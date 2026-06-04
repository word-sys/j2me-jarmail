package maxmail;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.io.file.*;
import java.io.*;
import java.util.*;

public class Midlet extends MIDlet implements CommandListener, Runnable, ItemCommandListener {
    private Display display;
    private List inboxList;
    private Form detailsForm, loadingForm, composeForm, settingsForm, loginForm;
    private TextField toField, subjectField, bodyField, serverUrlField, pairPinField;
    private StringItem loginTitle, loginStatus;
    private Command exitCmd, backCmd, refreshCmd, composeCmd, sendCmd, deleteCmd, replyCmd, sentboxCmd, inboxCmd, searchCmd, settingsCmd, saveSettingsCmd;
    private Command loginCmd, pairCmd;

    private List fileBrowserList, foldersList;
    private Command attachCmd, browseBackCmd, selectFileCmd, foldersCmd, markSpamCmd, emptyFolderCmd;
    private StringItem attachmentsLabel;
    private Vector attachmentsList = new Vector();
    private String currentDirPath = "";

    private String serverUrl = "http://YOUR_SERVER_IP:3000"; 
    private String currentBox = "/inbox";
    private String searchQuery = "";
    private int currentPage = 1;
    private static final int PAGE_LIMIT = 25;

    private int task = 0;
    
    private Vector mailIds = new Vector();
    private Vector attachUrls = new Vector();
    private Vector attachmentItems = new Vector(); 
    private String selectedId = "";
    private Hashtable activeMail;

    private String pairToken;
    private String sessionToken;

    private byte[] httpBuf = new byte[2048];

    public void startApp() {
        if (display == null) {
            serverUrl = SettingsStore.loadServerUrl(serverUrl);
            display = Display.getDisplay(this);

            exitCmd = new Command("Exit", Command.EXIT, 1);
            backCmd = new Command("Back", Command.BACK, 2);
            refreshCmd = new Command("Refresh", Command.SCREEN, 3);
            composeCmd = new Command("Compose", Command.SCREEN, 4);
            sentboxCmd = new Command("Sentbox", Command.SCREEN, 5);
            inboxCmd = new Command("Inbox", Command.SCREEN, 6);
            deleteCmd = new Command("Delete", Command.HELP, 7);
            searchCmd = new Command("Search", Command.SCREEN, 8);
            settingsCmd = new Command("Settings", Command.SCREEN, 9);
            replyCmd = new Command("Reply", Command.OK, 1);
            sendCmd = new Command("Send", Command.OK, 1);
            saveSettingsCmd = new Command("Save", Command.OK, 1);
            loginCmd = new Command("Login", Command.OK, 1);
            pairCmd = new Command("Pair Device", Command.SCREEN, 2);
            
            attachCmd = new Command("Attach File", Command.SCREEN, 10);
            selectFileCmd = new Command("Attach", Command.OK, 1);
            browseBackCmd = new Command("Back", Command.BACK, 2);
            
            foldersCmd = new Command("Folders", Command.SCREEN, 5);
            markSpamCmd = new Command("Spam", Command.SCREEN, 10);
            emptyFolderCmd = new Command("Empty Folder", Command.SCREEN, 11);

            loginForm = new Form("MaxMail");
            loginTitle = new StringItem(null, "\n\nMaxMail\n\n");
            loginTitle.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
            loginStatus = new StringItem(null, "");
            loginForm.append(loginTitle);
            loginForm.append(loginStatus);
            loginForm.addCommand(exitCmd);
            loginForm.setCommandListener(this);

            inboxList = new List("MaxMail", List.IMPLICIT);
            inboxList.setTicker(new Ticker("MaxMail Active Server: " + serverUrl));
            inboxList.addCommand(exitCmd);
            inboxList.addCommand(refreshCmd);
            inboxList.addCommand(composeCmd);
            inboxList.addCommand(deleteCmd);
            inboxList.addCommand(searchCmd);
            inboxList.addCommand(settingsCmd);
            inboxList.addCommand(foldersCmd);
            inboxList.setCommandListener(this);

            detailsForm = new Form("View Mail");
            detailsForm.addCommand(backCmd);
            detailsForm.addCommand(replyCmd);
            detailsForm.addCommand(deleteCmd);
            detailsForm.addCommand(markSpamCmd);
            detailsForm.setCommandListener(this);

            composeForm = new Form("Compose");
            toField = new TextField("To:", "", 150, TextField.EMAILADDR);
            subjectField = new TextField("Sub:", "", 100, TextField.ANY);
            bodyField = new TextField("Msg:", "", 2000, TextField.ANY);
            attachmentsLabel = new StringItem(null, "No attachments.");
            composeForm.append(toField);
            composeForm.append(subjectField);
            composeForm.append(bodyField);
            composeForm.append(attachmentsLabel);
            composeForm.addCommand(backCmd);
            composeForm.addCommand(sendCmd);
            composeForm.addCommand(attachCmd);
            composeForm.setCommandListener(this);

            settingsForm = new Form("Settings");
            serverUrlField = new TextField("Server URL:", serverUrl, 150, TextField.URL);
            pairPinField = new TextField("Pair PIN:", "", 6, TextField.NUMERIC);
            settingsForm.append(serverUrlField);
            settingsForm.append(new StringItem(null, "\n"));
            settingsForm.append(pairPinField);
            settingsForm.append(new StringItem(null, "Start server with PAIR_MODE=true\nto get a PIN for pairing."));
            settingsForm.addCommand(backCmd);
            settingsForm.addCommand(saveSettingsCmd);
            settingsForm.addCommand(pairCmd);
            settingsForm.setCommandListener(this);

            loadingForm = new Form("Connecting");
            loadingForm.append(new Gauge("Syncing...", false, 10, 5));

            fileBrowserList = new List("Select Attachment", List.IMPLICIT);
            fileBrowserList.addCommand(browseBackCmd);
            fileBrowserList.addCommand(selectFileCmd);
            fileBrowserList.setCommandListener(this);

            foldersList = new List("Folders", List.IMPLICIT);
            foldersList.append("Inbox", null);
            foldersList.append("Sent Mail", null);
            foldersList.append("Trash", null);
            foldersList.append("Spam", null);
            foldersList.addCommand(browseBackCmd);
            foldersList.setCommandListener(this);

            pairToken = SettingsStore.loadPairToken();
            sessionToken = SettingsStore.loadSessionToken();

            if (pairToken != null && pairToken.length() > 0) {
                if (sessionToken != null && sessionToken.length() > 0) {
                    doTask(0);
                } else {
                    showLoginScreen("Tap Login to connect.");
                }
            } else {
                showLoginScreen("Device not paired.\nOpen Settings to pair.");
            }
        }
    }

    private void showLoginScreen(String message) {
        loginStatus.setText(message);
        loginForm.removeCommand(loginCmd);
        loginForm.removeCommand(settingsCmd);

        if (pairToken != null && pairToken.length() > 0) {
            loginForm.addCommand(loginCmd);
            loginForm.addCommand(settingsCmd);
        } else {
            loginForm.addCommand(settingsCmd);
        }
        display.setCurrent(loginForm);
    }

    private String boxParam() {
        if (currentBox.equals("/sentbox")) return "sent";
        if (currentBox.equals("/trashbox")) return "trash";
        if (currentBox.equals("/spambox")) return "spam";
        return "inbox";
    }

    private void handleAuthExpired() {
        sessionToken = null;
        SettingsStore.saveSessionToken(null);
        display.callSerially(new Runnable() {
            public void run() {
                showLoginScreen("Session expired.\nTap Login to reconnect.");
            }
        });
    }

    private void doTask(int t) {
        this.task = t;
        display.setCurrent(loadingForm);
        new Thread(this).start();
    }

    public void run() {
        try {
            if (task == 0) {
                StringBuffer urlBuf = new StringBuffer(128);
                urlBuf.append(serverUrl).append(currentBox);
                urlBuf.append("?q=").append(NetworkHelper.urlEncode(searchQuery));
                urlBuf.append("&page=").append(currentPage);
                urlBuf.append("&limit=").append(PAGE_LIMIT);

                final String res = fetchHttp(urlBuf.toString(), null);
                if (res == null) { handleAuthExpired(); return; }
                display.callSerially(new Runnable() { public void run() { updateUI(res); } });

            } else if (task == 4) {
                String b = boxParam();
                StringBuffer urlBuf = new StringBuffer(96);
                urlBuf.append(serverUrl).append("/detail?id=").append(selectedId).append("&box=").append(b);

                final String res = fetchHttp(urlBuf.toString(), null);
                if (res == null) { handleAuthExpired(); return; }
                display.callSerially(new Runnable() { public void run() { 
                    try { activeMail = (Hashtable) MiniJSON.parse(res); showDetailUI(); } catch(Exception e){}
                } });

            } else if (task == 1) {
                StringBuffer serverAttaches = new StringBuffer();
                for (int idx = 0; idx < attachmentsList.size(); idx++) {
                    String filePath = (String) attachmentsList.elementAt(idx);
                    int lastSlash = filePath.lastIndexOf('/');
                    String name = (lastSlash != -1) ? filePath.substring(lastSlash + 1) : "file";
                    
                    StringBuffer uploadUrlBuf = new StringBuffer(96);
                    uploadUrlBuf.append(serverUrl).append("/upload?name=").append(NetworkHelper.urlEncode(name));
                    String uniqueName = uploadFile(uploadUrlBuf.toString(), filePath);
                    if (uniqueName != null && uniqueName.trim().length() > 0) {
                        if (serverAttaches.length() > 0) serverAttaches.append(",");
                        serverAttaches.append(uniqueName.trim());
                    }
                }
                
                StringBuffer postBody = new StringBuffer(256);
                postBody.append("to=").append(NetworkHelper.urlEncode(toField.getString()));
                postBody.append("&subject=").append(NetworkHelper.urlEncode(subjectField.getString()));
                postBody.append("&body=").append(NetworkHelper.urlEncode(bodyField.getString()));
                postBody.append("&attachments=").append(NetworkHelper.urlEncode(serverAttaches.toString()));

                String sendRes = fetchHttp(serverUrl + "/send", postBody.toString());
                if (sendRes == null) { handleAuthExpired(); return; }
                display.callSerially(new Runnable() {
                    public void run() {
                        Alert success = new Alert("Sent", "Mail sent successfully.", null, AlertType.CONFIRMATION);
                        success.setTimeout(2000);
                        display.setCurrent(success, inboxList);
                    }
                });
                searchQuery = ""; 
                currentPage = 1; 
                doTask(0);

            } else if (task == 2) {
                String b = boxParam();
                String delRes = fetchHttp(serverUrl + "/delete", "id=" + selectedId + "&box=" + b);
                if (delRes == null) { handleAuthExpired(); return; }
                display.callSerially(new Runnable() {
                    public void run() {
                        removeMailLocally(selectedId);
                        Alert success = new Alert("Deleted", "Email deleted successfully.", null, AlertType.CONFIRMATION);
                        success.setTimeout(1500);
                        display.setCurrent(success, inboxList);
                    }
                });

            } else if (task == 5) {
                String b = boxParam();
                String spamRes = fetchHttp(serverUrl + "/spam", "id=" + selectedId + "&box=" + b);
                if (spamRes == null) { handleAuthExpired(); return; }
                display.callSerially(new Runnable() {
                    public void run() {
                        removeMailLocally(selectedId);
                        Alert success = new Alert("Spam Reported", "Email moved to Spam.", null, AlertType.CONFIRMATION);
                        success.setTimeout(2000);
                        display.setCurrent(success, inboxList);
                    }
                });

            } else if (task == 6) {
                String b = currentBox.equals("/trashbox") ? "trash" : "spam";
                String emptyRes = fetchHttp(serverUrl + "/empty", "box=" + b);
                if (emptyRes == null) { handleAuthExpired(); return; }
                display.callSerially(new Runnable() {
                    public void run() {
                        inboxList.deleteAll();
                        mailIds.removeAllElements();
                        inboxList.append("Empty", null);
                        Alert success = new Alert("Emptied", "Folder cleared successfully.", null, AlertType.CONFIRMATION);
                        success.setTimeout(2000);
                        display.setCurrent(success, inboxList);
                    }
                });

            } else if (task == 7) {
                String pin = pairPinField.getString().trim();
                final String res = fetchHttp(serverUrl + "/pair", "pin=" + NetworkHelper.urlEncode(pin));
                if (res == null) {
                    display.callSerially(new Runnable() {
                        public void run() {
                            Alert error = new Alert("Pair Failed",
                                "Invalid PIN or pairing not active.\nStart server with PAIR_MODE=true",
                                null, AlertType.ERROR);
                            error.setTimeout(Alert.FOREVER);
                            display.setCurrent(error, settingsForm);
                        }
                    });
                    return;
                }
                Hashtable result = (Hashtable) MiniJSON.parse(res);
                pairToken = result.get("token").toString();
                SettingsStore.savePairToken(pairToken);
                doTask(8);
                return;

            } else if (task == 8) {
                String minuteStr = AuthHelper.getUtcMinuteString();
                String authId = AuthHelper.generateAuthId(pairToken, minuteStr);
                final String res = fetchHttp(serverUrl + "/login", "authId=" + NetworkHelper.urlEncode(authId));
                if (res == null) {
                    display.callSerially(new Runnable() {
                        public void run() {
                            showLoginScreen("Authentication failed.\nCheck device clock.");
                        }
                    });
                    return;
                }
                Hashtable result = (Hashtable) MiniJSON.parse(res);
                sessionToken = result.get("session").toString();
                SettingsStore.saveSessionToken(sessionToken);
                searchQuery = "";
                currentPage = 1;
                currentBox = "/inbox";
                doTask(0);
                return;
            }

        } catch (final Exception e) {
            display.callSerially(new Runnable() { 
                public void run() { 
                    Alert alert = new Alert("Connection Error", 
                        "Could not connect to server.\nVerify IP address and settings.\n\nError: " + e.getMessage(), 
                        null, AlertType.ERROR);
                    alert.setTimeout(Alert.FOREVER);

                    Displayable fallback = (sessionToken != null && sessionToken.length() > 0) ? 
                        (Displayable) inboxList : (Displayable) loginForm;
                    display.setCurrent(alert, fallback); 
                } 
            });
        }
    }

    private void updateUI(String json) {
        inboxList.deleteAll();
        mailIds.removeAllElements();
        
        inboxList.removeCommand(inboxCmd);
        inboxList.removeCommand(sentboxCmd);
        inboxList.removeCommand(emptyFolderCmd);
        
        if (currentBox.equals("/inbox")) {
            inboxList.setTitle("Inbox (P. " + currentPage + ")");
        } else if (currentBox.equals("/sentbox")) {
            inboxList.setTitle("Sent (P. " + currentPage + ")");
        } else if (currentBox.equals("/trashbox")) {
            inboxList.setTitle("Trash (P. " + currentPage + ")");
            inboxList.addCommand(emptyFolderCmd);
        } else if (currentBox.equals("/spambox")) {
            inboxList.setTitle("Spam (P. " + currentPage + ")");
            inboxList.addCommand(emptyFolderCmd);
        }

        if (currentPage > 1) {
            inboxList.append("[Previous Page]", null);
            mailIds.addElement("PREV");
        }

        int count = 0;
        try {
            Vector mails = (Vector) MiniJSON.parse(json);
            count = mails.size();
            boolean isInbox = currentBox.equals("/inbox");

            for (int i = 0; i < count; i++) {
                Hashtable m = (Hashtable) mails.elementAt(i);
                mailIds.addElement(m.get("i"));

                String attachFlag = "true".equals(m.get("a")) ? "[A] " : "    ";
                String unreadFlag = (isInbox && "false".equals(m.get("r"))) ? "(!) " : "    ";
                
                Object dateObj = m.get("d");
                String date = (dateObj != null && dateObj != MiniJSON.JSON_NULL) ? dateObj.toString() : "";
                
                StringBuffer line = new StringBuffer(80);
                line.append(unreadFlag).append(attachFlag).append(m.get("f"));
                if (date.length() > 0) {
                    line.append(" - ").append(date);
                }
                line.append('\n').append(m.get("s"));
                inboxList.append(line.toString(), null);
            }
        } catch (Exception e) { 
            if (inboxList.size() == 0) {
                inboxList.append("Empty", null); 
            }
        }
        
        if (count >= PAGE_LIMIT) {
            inboxList.append("[Next Page]", null);
            mailIds.addElement("NEXT");
        }
        
        display.setCurrent(inboxList);
    }

    private void markMailAsReadLocally(String id) {
        if (!currentBox.equals("/inbox")) return;
        int idx = mailIds.indexOf(id);
        if (idx != -1 && idx < inboxList.size()) {
            String originalText = inboxList.getString(idx);
            if (originalText.startsWith("(!) ")) {
                String updatedText = "    " + originalText.substring(4);
                inboxList.set(idx, updatedText, null);
            }
        }
    }

    private void removeMailLocally(String id) {
        int idx = mailIds.indexOf(id);
        if (idx != -1) {
            mailIds.removeElementAt(idx);
            if (idx < inboxList.size()) {
                inboxList.delete(idx);
            }
            if (inboxList.size() == 0) {
                inboxList.append("Empty", null);
            }
        }
    }

    private void showDetailUI() {
        markMailAsReadLocally(selectedId);
        detailsForm.deleteAll();
        attachUrls.removeAllElements();
        attachmentItems.removeAllElements();
        
        detailsForm.setTitle(activeMail.get("s").toString());
        
        Object toObj = activeMail.get("t");
        String toStr = (toObj != null && toObj != MiniJSON.JSON_NULL) ? toObj.toString() : "";
        StringItem header = new StringItem(null, "From: " + activeMail.get("f") + 
                                                "\nTo: " + toStr + 
                                                "\nDate: " + activeMail.get("d") + "\n\n");
        header.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        detailsForm.append(header);
        detailsForm.append(new StringItem(null, activeMail.get("b").toString() + "\n\n"));
        
        Vector attaches = (Vector) activeMail.get("at");
        if (attaches != null && attaches.size() > 0) {
            detailsForm.append(new StringItem(null, "--- ATTACHMENTS ---"));
            for (int i = 0; i < attaches.size(); i++) {
                Hashtable at = (Hashtable) attaches.elementAt(i);
                attachUrls.addElement(at.get("u"));
                StringItem fileLink = new StringItem(null, "[" + at.get("n") + "]", Item.BUTTON);
                fileLink.setDefaultCommand(new Command("Open", Command.ITEM, 1));
                fileLink.setItemCommandListener(this);
                
                attachmentItems.addElement(fileLink);
                detailsForm.append(fileLink);
            }
        }
        display.setCurrent(detailsForm);
    }

    public void commandAction(Command c, Item item) {
        if (c.getLabel().equals("Open")) {
            int idx = attachmentItems.indexOf(item);
            if (idx != -1 && idx < attachUrls.size()) {
                try { 
                    String url = (String) attachUrls.elementAt(idx);
                    platformRequest(serverUrl + url + NetworkHelper.BB_SUFFIX); 
                } catch(Exception e){}
            }
        }
    }

    private void showFileBrowser(String path) {
        currentDirPath = path;
        fileBrowserList.deleteAll();
        fileBrowserList.setTitle(path.length() == 0 ? "Roots" : path);
        
        if (path.length() == 0) {
            Enumeration e = FileSystemRegistry.listRoots();
            while (e.hasMoreElements()) {
                fileBrowserList.append((String) e.nextElement(), null);
            }
        } else {
            FileConnection fc = null;
            try {
                fc = (FileConnection) Connector.open(path);
                Enumeration list = fc.list("*", true);
                while (list.hasMoreElements()) {
                    String name = (String) list.nextElement();
                    fileBrowserList.append(name, null);
                }
            } catch (Exception e) {
                Alert err = new Alert("Folder Error", "Could not open folder:\n" + e.getMessage(), null, AlertType.ERROR);
                err.setTimeout(Alert.FOREVER);
                display.setCurrent(err, composeForm);
                return;
            } finally {
                if (fc != null) { try { fc.close(); } catch(Exception e){} }
            }
        }
        display.setCurrent(fileBrowserList);
    }

    private void updateAttachmentsLabel() {
        if (attachmentsList.size() == 0) {
            attachmentsLabel.setText("No attachments.");
        } else {
            StringBuffer sb = new StringBuffer("Attachments:\n");
            for (int i = 0; i < attachmentsList.size(); i++) {
                String path = (String) attachmentsList.elementAt(i);
                int lastSlash = path.lastIndexOf('/');
                String name = (lastSlash != -1) ? path.substring(lastSlash + 1) : path;
                sb.append("- ").append(name).append("\n");
            }
            attachmentsLabel.setText(sb.toString().trim());
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (d == fileBrowserList) {
            if (c == List.SELECT_COMMAND) {
                int idx = fileBrowserList.getSelectedIndex();
                if (idx != -1) {
                    String selected = fileBrowserList.getString(idx);
                    if (currentDirPath.length() == 0) {
                        showFileBrowser("file:///" + selected);
                    } else {
                        if (selected.endsWith("/")) {
                            showFileBrowser(currentDirPath + selected);
                        } else {
                            String fullPath = currentDirPath + selected;
                            if (attachmentsList.indexOf(fullPath) == -1) {
                                attachmentsList.addElement(fullPath);
                                updateAttachmentsLabel();
                            }
                            display.setCurrent(composeForm);
                        }
                    }
                }
            } else if (c == browseBackCmd) {
                if (currentDirPath.length() == 0 || currentDirPath.equals("file:///")) {
                    display.setCurrent(composeForm);
                } else {
                    String path = currentDirPath;
                    if (path.endsWith("/")) {
                        path = path.substring(0, path.length() - 1);
                    }
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash != -1 && lastSlash >= 7) {
                        showFileBrowser(path.substring(0, lastSlash + 1));
                    } else {
                        showFileBrowser("");
                    }
                }
            } else if (c == selectFileCmd) {
                int idx = fileBrowserList.getSelectedIndex();
                if (idx != -1) {
                    String selected = fileBrowserList.getString(idx);
                    if (!selected.endsWith("/") && currentDirPath.length() > 0) {
                        String fullPath = currentDirPath + selected;
                        if (attachmentsList.indexOf(fullPath) == -1) {
                            attachmentsList.addElement(fullPath);
                            updateAttachmentsLabel();
                        }
                        display.setCurrent(composeForm);
                    }
                }
            }
        } else if (d == foldersList) {
            if (c == List.SELECT_COMMAND) {
                int idx = foldersList.getSelectedIndex();
                if (idx != -1) {
                    if (idx == 0) currentBox = "/inbox";
                    else if (idx == 1) currentBox = "/sentbox";
                    else if (idx == 2) currentBox = "/trashbox";
                    else if (idx == 3) currentBox = "/spambox";
                    
                    currentPage = 1;
                    searchQuery = "";
                    doTask(0);
                }
            } else if (c == browseBackCmd) {
                display.setCurrent(inboxList);
            }
        } else if (d == loginForm) {
            if (c == exitCmd) {
                notifyDestroyed();
            } else if (c == loginCmd) {
                if (pairToken != null && pairToken.length() > 0) {
                    loginStatus.setText("Authenticating...");
                    doTask(8);
                }
            } else if (c == settingsCmd) {
                serverUrlField.setString(serverUrl);
                pairPinField.setString("");
                display.setCurrent(settingsForm);
            }
        } else {
            if (c == exitCmd) {
                notifyDestroyed();
            } else if (c == backCmd) {
                if (d == settingsForm) {
                    if (pairToken == null || pairToken.length() == 0) {
                        showLoginScreen("Device not paired.\nOpen Settings to pair.");
                    } else if (sessionToken == null || sessionToken.length() == 0) {
                        showLoginScreen("Tap Login to connect.");
                    } else {
                        display.setCurrent(inboxList);
                    }
                } else {
                    display.setCurrent(inboxList);
                }
            } else if (c == refreshCmd) {
                searchQuery = ""; 
                currentPage = 1; 
                doTask(0); 
            } else if (c == foldersCmd) { 
                display.setCurrent(foldersList); 
            } else if (c == composeCmd) { 
                toField.setString(""); 
                subjectField.setString(""); 
                bodyField.setString(""); 
                attachmentsList.removeAllElements();
                updateAttachmentsLabel();
                display.setCurrent(composeForm); 
            } else if (c == sendCmd) {
                doTask(1);
            } else if (c == attachCmd) {
                showFileBrowser("");
            } else if (c == markSpamCmd) {
                doTask(5);
            } else if (c == emptyFolderCmd) {
                doTask(6);
            } else if (c == settingsCmd) {
                serverUrlField.setString(serverUrl);
                pairPinField.setString("");
                display.setCurrent(settingsForm);
            } else if (c == saveSettingsCmd) {
                String newUrl = serverUrlField.getString().trim();
                if (newUrl.length() > 0) {
                    if (newUrl.endsWith("/")) {
                        newUrl = newUrl.substring(0, newUrl.length() - 1);
                    }
                    serverUrl = newUrl;
                    inboxList.setTicker(new Ticker("MaxMail Active Server: " + serverUrl));
                    SettingsStore.saveServerUrl(serverUrl);
                    
                    Alert success = new Alert("Settings Saved", "Server URL has been saved persistently.", null, AlertType.CONFIRMATION);
                    success.setTimeout(2000);
                    display.setCurrent(success, settingsForm);
                } else {
                    Alert error = new Alert("Error", "Server URL cannot be empty.", null, AlertType.ERROR);
                    error.setTimeout(2000);
                    display.setCurrent(error, settingsForm);
                }
            } else if (c == pairCmd) {
                String pin = pairPinField.getString().trim();
                if (pin.length() > 0) {
                    doTask(7);
                } else {
                    Alert error = new Alert("Error", "Enter the 6-digit PIN\nfrom server console.", null, AlertType.ERROR);
                    error.setTimeout(3000);
                    display.setCurrent(error, settingsForm);
                }
            } else if (c == searchCmd) {
                final TextBox tb = new TextBox("Search Query", searchQuery, 50, TextField.ANY);
                Command okCmd = new Command("Search", Command.OK, 1);
                Command cancelCmd = new Command("Cancel", Command.CANCEL, 2);
                tb.addCommand(okCmd);
                tb.addCommand(cancelCmd);
                tb.setCommandListener(new CommandListener() {
                    public void commandAction(Command c, Displayable d) {
                        if (c.getPriority() == 1) {
                            searchQuery = tb.getString().trim(); 
                            currentPage = 1; 
                            doTask(0); 
                        } else {
                            display.setCurrent(inboxList); 
                        }
                    }
                });
                display.setCurrent(tb);
            } else if (c == deleteCmd && mailIds.size() > 0) {
                if (d == inboxList) {
                    int selectedIndex = inboxList.getSelectedIndex();
                    if (selectedIndex != -1 && selectedIndex < mailIds.size()) {
                        String id = (String) mailIds.elementAt(selectedIndex);
                        if (id.equals("PREV") || id.equals("NEXT")) {
                            return;
                        }
                        selectedId = id;
                    }
                }
                doTask(2);
            } else if (c == replyCmd) {
                String replyTo = "";
                if (activeMail.containsKey("e")) {
                    Object emailObj = activeMail.get("e");
                    if (emailObj != null && emailObj != MiniJSON.JSON_NULL) {
                        replyTo = emailObj.toString().trim();
                    }
                }
                if (replyTo.length() == 0) {
                    String sender = activeMail.get("f").toString();
                    int start = sender.indexOf('<');
                    int end = sender.indexOf('>');
                    if (start != -1 && end != -1 && end > start) {
                        replyTo = sender.substring(start + 1, end).trim();
                    } else {
                        replyTo = sender.trim();
                    }
                }
                toField.setString(replyTo);
                subjectField.setString("Re: " + activeMail.get("s"));
                bodyField.setString("\n\n---Original---\n" + activeMail.get("b"));
                attachmentsList.removeAllElements();
                updateAttachmentsLabel();
                display.setCurrent(composeForm);
            } else if (c == List.SELECT_COMMAND && mailIds.size() > 0) {
                int selectedIndex = inboxList.getSelectedIndex();
                if (selectedIndex != -1 && selectedIndex < mailIds.size()) {
                    String id = (String) mailIds.elementAt(selectedIndex);
                    if (id.equals("PREV")) {
                        currentPage--;
                        doTask(0);
                    } else if (id.equals("NEXT")) {
                        currentPage++;
                        doTask(0);
                    } else {
                        selectedId = id;
                        doTask(4);
                    }
                }
            }
        }
    }

    private String fetchHttp(String url, String post) throws IOException {
        if (sessionToken != null && sessionToken.length() > 0) {
            url += (url.indexOf('?') >= 0 ? "&" : "?") + "token=" + NetworkHelper.urlEncode(sessionToken);
        }

        HttpConnection h = null; 
        InputStream i = null; 
        OutputStream o = null;
        try {
            h = (HttpConnection) Connector.open(NetworkHelper.makeBBUrl(url));
            if (post != null) {
                h.setRequestMethod(HttpConnection.POST);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                o = h.openOutputStream(); 
                o.write(post.getBytes("UTF-8")); 
                o.flush();
            }
            int rc = h.getResponseCode();
            if (rc == 401) {
                return null;
            }
            if (rc == HttpConnection.HTTP_OK) {
                i = h.openInputStream();
                StringBuffer s = new StringBuffer(4096);
                int len;
                while ((len = i.read(httpBuf)) != -1) {
                    s.append(new String(httpBuf, 0, len, "UTF-8"));
                }
                return s.toString();
            } else {
                throw new IOException("HTTP " + rc);
            }
        } finally { 
            if (o != null) { try { o.close(); } catch(Exception e){} }
            if (i != null) { try { i.close(); } catch(Exception e){} } 
            if (h != null) { try { h.close(); } catch(Exception e){} } 
        }
    }

    private String uploadFile(String url, String filePath) throws IOException {
        if (sessionToken != null && sessionToken.length() > 0) {
            url += (url.indexOf('?') >= 0 ? "&" : "?") + "token=" + NetworkHelper.urlEncode(sessionToken);
        }

        HttpConnection h = null; 
        InputStream fileInput = null;
        OutputStream o = null; 
        InputStream responseInput = null;
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(filePath);
            if (!fc.exists()) throw new IOException("File does not exist");
            long size = fc.fileSize();
            fileInput = fc.openInputStream();

            h = (HttpConnection) Connector.open(NetworkHelper.makeBBUrl(url));
            h.setRequestMethod(HttpConnection.POST);
            h.setRequestProperty("Content-Type", "application/octet-stream");
            h.setRequestProperty("Content-Length", String.valueOf(size));
            
            o = h.openOutputStream();
            int len;
            while ((len = fileInput.read(httpBuf)) != -1) {
                o.write(httpBuf, 0, len);
            }
            o.flush();
            
            int rc = h.getResponseCode();
            if (rc == 401) {
                return null;
            }
            if (rc == HttpConnection.HTTP_OK) {
                responseInput = h.openInputStream();
                StringBuffer s = new StringBuffer(128);
                int resLen;
                while ((resLen = responseInput.read(httpBuf)) != -1) {
                    s.append(new String(httpBuf, 0, resLen, "UTF-8"));
                }
                return s.toString();
            } else {
                throw new IOException("HTTP " + rc);
            }
        } finally { 
            if (fileInput != null) { try { fileInput.close(); } catch(Exception e){} }
            if (fc != null) { try { fc.close(); } catch(Exception e){} }
            if (o != null) { try { o.close(); } catch(Exception e){} }
            if (responseInput != null) { try { responseInput.close(); } catch(Exception e){} } 
            if (h != null) { try { h.close(); } catch(Exception e){} } 
        }
    }

    public void pauseApp() {}
    public void destroyApp(boolean b) {}
}
