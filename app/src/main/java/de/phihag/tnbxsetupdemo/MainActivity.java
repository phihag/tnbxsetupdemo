package de.phihag.tnbxsetupdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private BroadcastReceiver wifiScanReceiver;

    private String _displaySsid(String ssid) {
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    private void setSsids(List<String> ssids, String sel) {
        List<String> displaySsids = new ArrayList<String>();
        for (String raw : ssids) {
            displaySsids.add(_displaySsid(raw));
        }

        ArrayAdapter<String> ssidInputAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displaySsids);
        Spinner ssidInput = (Spinner) findViewById(R.id.ssidInput);
        ssidInput.setAdapter(ssidInputAdapter);

        if (sel != null) {
            ssidInput.setSelection(ssids.indexOf(sel));
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = getApplicationContext();
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(context, "Kein WLAN ... wird aktiviert", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }

        // Enter currently connected WLAN
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String connectedSsid = null;
        if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
            connectedSsid = wifiInfo.getSSID();
            List<String> ssidList = new ArrayList<>();
            ssidList.add(ssid);
            setSsids(ssidList, ssid);
        }

        // Scan for more WLANs (including the Toniebox)
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                List<ScanResult> wifiScanList = wifiManager.getScanResults();
                setSsids(wifiScanList, connectedSsid);
            }
        };
        wifiManager.startScan();
    }

    protected void onPause() {
        unregisterReceiver(wifiScanReceiver);
        super.onPause();
    }

    protected void onResume() {
        registerReceiver(
                wifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );
        super.onResume();
    }

}

// TODO scan wifi
// TODO actually configure
// TODO show scanning icon
// TODO switch between visible/invisible password
// TODO store passwords (and predefault if we know it)
// TODO button to scan now
// TODO deal with connection changes (or starting without Wifi)
// TODO scan wifi continuously