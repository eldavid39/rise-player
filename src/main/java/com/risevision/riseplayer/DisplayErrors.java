// Copyright 2010 - May 2014 Rise Vision Incorporated.
// Use of this software is governed by the GPLv3 license
// (reproduced in the LICENSE file).

package com.risevision.riseplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.risevision.riseplayer.externallogger.ExternalLogger;
import com.risevision.riseplayer.externallogger.InsertSchema;
import com.risevision.riseplayer.utils.Utils;

public class DisplayErrors {


    private static final String FILE_DISPLAY_ERRORS = "display.err";
    private static final String FIELD_DELIMMITER = ",";

    private List<DisplayError> displayErrorList = new ArrayList<DisplayError>();
    static PrintStream log = null;

    public static final int DISPLAYERROR_VIEWER_NOT_RESPONDING = 1004;
    public static final int DISPLAYERROR_VIEWER_NOT_RESPONDING_MAX = 10;

    private static DisplayErrors instance;

    public static synchronized DisplayErrors getInstance() {
        if (instance == null) {
            instance = new DisplayErrors();
        }
        return instance;
    }

    public synchronized void reportDisplayErrorstoCore() {
        String displayErrUrl = Config.coreBaseUrl + "/player/update?";
        int errCode = 0;
        boolean sendToCore = false;
        for (int i = 0; i < displayErrorList.size(); i++) {
            sendToCore = false;
            DisplayError dispErr = displayErrorList.get(i);

            if (DISPLAYERROR_VIEWER_NOT_RESPONDING == dispErr.code && dispErr.occourances > DISPLAYERROR_VIEWER_NOT_RESPONDING_MAX) {
                errCode = dispErr.code;
                sendToCore = true;
                break;
            }

            if (DISPLAYERROR_VIEWER_NOT_RESPONDING == dispErr.code && dispErr.occourances < (-1 * DISPLAYERROR_VIEWER_NOT_RESPONDING_MAX)) {
                errCode = 0;
                deleteError(dispErr.code);
                sendToCore = true;
            }
        }

        if (sendToCore && !Config.displayId.isEmpty()) {
            ExternalLogger.logExternal(InsertSchema.withEvent("report errors core"));

            if (sendToCore(displayErrUrl + "id=" + Config.displayId + "&st=" + Integer.toString(errCode)))
                updateError(errCode, -1);
        }
        writeErrorsToFile();
    }

    private boolean sendToCore(String address) {

        boolean retStatus = false;

        try {
            URL url = new java.net.URL(address);
            HttpURLConnection connection;

            if (Config.useProxy) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Config.proxyAddr, Config.proxyPort));
                connection = (HttpURLConnection) url.openConnection(proxy);
            } else {
                connection = (HttpURLConnection) url.openConnection();
            }

            connection.setRequestMethod("HEAD");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                Log.info("Server not responding. Response code " + responseCode + " received for URL " + address);
                return false;
            }

            retStatus = true;
        } catch (ConnectException e) {
            e.printStackTrace();
            Log.error(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Log.error(e.getMessage());
        }

        return retStatus;

    }

    private synchronized void updateError(int code, int occourances) {
        boolean found = false;
        if (code != 0) {
            for (int i = 0; i < displayErrorList.size(); i++) {
                if (displayErrorList.get(i).code == code) {
                    if (occourances == -1) {
                        if (displayErrorList.get(i).occourances >= 0)
                            displayErrorList.get(i).occourances = -1;
                        else
                            displayErrorList.get(i).occourances += occourances;
                    } else {
                        if (displayErrorList.get(i).occourances <= 0)
                            displayErrorList.get(i).occourances = 1;
                        else
                            displayErrorList.get(i).occourances += occourances;
                    }
                    found = true;
                }
            }

            //The error code didn't exist before
            if (!found)
                displayErrorList.add(new DisplayError(code, occourances));
        }
    }

    private synchronized void deleteError(int code) {
        if (code != 0) {
            for (int i = 0; i < displayErrorList.size(); i++) {
                if (displayErrorList.get(i).code == code) {
                    displayErrorList.remove(i);
                    break;
                }
            }
        }
    }

    public synchronized void loadErrorsFromFile() {

        String fileName = FILE_DISPLAY_ERRORS;
        try {
            File f = new File(Config.appPath, fileName);
            if (f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line = "";

                while ((line = br.readLine()) != null) {
                    try {
                        // use comma as separator
                        String[] retList = line.split(FIELD_DELIMMITER);

                        updateError(Utils.StrToInt(retList[0], 0), Utils.StrToInt(retList[1], 0));
                    } catch (Exception e) {
                    }
                }

                br.close();
            }

        } catch (Exception e) {
            Log.warn("Error loading display errors file. File name: " + fileName + ". Error: " + e.getMessage());
        }
    }

    public synchronized void writeErrorsToFile() {

        String fileName = FILE_DISPLAY_ERRORS;
        try {
            File f = new File(Config.appPath, fileName);
            FileWriter writer = new FileWriter(f);

            for (int i = 0; i < displayErrorList.size(); i++) {
                DisplayError dispErr = displayErrorList.get(i);
                writer.append(Integer.toString(dispErr.code));
                writer.append(FIELD_DELIMMITER);
                writer.append(Integer.toString(dispErr.occourances));
                writer.append('\n');
            }

            writer.flush();
            writer.close();
        } catch (Exception e) {
            Log.warn("Error updating display errors file. File name: " + fileName + ". Error: " + e.getMessage());
        }
    }

    public void viewerNotResponding(int value) {
        //updateError(DISPLAYERROR_VIEWER_NOT_RESPONDING, value);
    }
}
