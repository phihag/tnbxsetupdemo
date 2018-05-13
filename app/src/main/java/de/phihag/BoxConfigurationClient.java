package de.phihag;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;

public abstract class BoxConfigurationClient extends AsyncTask<BoxConfigurationClient.WifiConfig, String, Boolean> {
    public final static int TYPE_OPEN = 0;
    public final static int TYPE_WEP = 1;
    public final static int TYPE_WPA1 = 2;
    public final static int TYPE_WPA2 = 3;

    public static class WifiConfig {
        public final int wifiType;
        public final String ssid;
        public final String password;

        public WifiConfig(String ssid, int wifiType, String password) {
            this.ssid = ssid;
            this.wifiType = wifiType;
            this.password = password;
        }
    }

    private final int CONNECT_TIMEOUT = 2500;
    private final int READ_TIMEOUT = 20000;
    private final int RETRY_TIME = 1000;
    private final int TRY_COUNT = 10;

    private String craftPostData(WifiConfig wc) {
        try {
            return ("__SL_P_S.R=index.html" +
                    "&__SL_P_USL=" + URLEncoder.encode(wc.ssid,"UTF-8") +
                    "&__SL_P_USM=" + URLEncoder.encode(String.valueOf(wc.wifiType), "UTF-8") +
                    "&__SL_P_USN=" + URLEncoder.encode(wc.password, "UTF-8") +
                    "&__SL_P_USP=1&__SL_P_USQ=&__SL_P_USR=1&__SL_P_USS=&__SL_P_USO=0" // A bunch of crap required by the POST
                    );
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendRequest(WifiConfig wc) throws IOException {
        URL url;
        try {
            url = new URL("http://192.168.1.1/index.html");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        HttpURLConnection conn =  (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);

        OutputStream os = conn.getOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(os);
        writer.write(craftPostData(wc));
        writer.flush();
        writer.close();
        os.close();
        int responseCode = conn.getResponseCode();

        if (responseCode != 302) {
            throw new IOException("Unexpected response code: " + responseCode);
        }
    }

    @Override
    protected Boolean doInBackground(WifiConfig... wifiConfigs) {
        assert(wifiConfigs.length == 1);
        WifiConfig wc = wifiConfigs[0];
        for (int tries = 0;tries < TRY_COUNT;tries++) {
            try {
                Thread.sleep(RETRY_TIME);
            } catch (InterruptedException e1) {
                // Ignore, just retry immediately
            }

            try {
                publishProgress("Versuch " + (tries + 1) + " / " + TRY_COUNT);
                sendRequest(wc);
                publishProgress("... erfolgreich!");
                return true;
            } catch (SocketTimeoutException e) {
                ; // Ignore, Toniebox is booting
            } catch (ConnectException e) {
                ; // Ignore, Toniebox is booting
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    protected abstract void onPostExecute(Boolean result);
    protected abstract void onProgressUpdate(String... progress);
}
