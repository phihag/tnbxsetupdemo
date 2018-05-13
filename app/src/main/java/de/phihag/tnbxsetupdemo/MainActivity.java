package de.phihag.tnbxsetupdemo;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private BroadcastReceiver wifiScanReceiver;
    private WLANScanRequestThread wlanScanRequestThread;
    private boolean updatedWLANList = false;

    private String _displaySsid(String ssid) {
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    private void setSsids(Spinner spinner, List<String> ssids, String sel) {
        List<String> displaySsids = new ArrayList<String>();
        for (String raw : ssids) {
            displaySsids.add(_displaySsid(raw));
        }

        ArrayAdapter<String> ssidInputAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displaySsids);
        spinner.setAdapter(ssidInputAdapter);

        if (sel != null) {
            spinner.setSelection(ssids.indexOf(sel));
        }
    }

    private void storePassword(SharedPreferences sharedPref, String ssid, String password) {
        Log.d("apssword", "storing ssid = " + ssid + "; password = " + password);
        if (password.length() == 0) return;

        String password_b64 = null;
        try {
            password_b64 = Base64.encodeToString(password.getBytes("UTF-8"), Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        SharedPreferences.Editor editor = sharedPref.edit();
        String key = "wlan_password_b64_" + _displaySsid(ssid);
        editor.putString(key, password_b64);
        editor.commit();
    }

    private String loadPassword(SharedPreferences sharedPref, String ssid) {
        String key = "wlan_password_b64_" + _displaySsid(ssid);
        String b64 = sharedPref.getString(key, null);
        if (b64 == null) return null;

        String password = null;
        try {
            password = new String(Base64.decode(b64, Base64.DEFAULT), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return password;
    }

    private void setPasswordMode(EditText passwordInput, boolean visible) {
        passwordInput.setTransformationMethod(visible ? null : new PasswordTransformationMethod());
    }

    private void setupPasswordVisible(SharedPreferences sharedPref) {
        EditText passwordInput = (EditText) findViewById(R.id.passwordInput);
        CheckBox displayPasswordCheckbox = findViewById(R.id.displayPasswordCheckbox);
        if (sharedPref.contains("password_visible")) {
            boolean prefVisible = sharedPref.getBoolean("password_visible", true);
            displayPasswordCheckbox.setChecked(prefVisible);
            setPasswordMode(passwordInput, prefVisible);
        }
        displayPasswordCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setPasswordMode(passwordInput, isChecked);

                // Store preferences
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("password_visible", isChecked);
                editor.commit();
            }
        });
    }

    private void setupStorePassword(SharedPreferences sharedPref) {
        CheckBox storePasswordCheckbox = findViewById(R.id.storePasswordCheckbox);
        if (sharedPref.contains("store_password")) {
            storePasswordCheckbox.setChecked(sharedPref.getBoolean("store_password", true));
        }
        storePasswordCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Store preferences
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("store_password", isChecked);
                editor.commit();
            }
        });
    }

    private void configure(SharedPreferences sharedPref) {
        Spinner ssidInput = (Spinner) findViewById(R.id.ssidInput);
        String ssid = ssidInput.getSelectedItem().toString();

        EditText passwordInput = (EditText) findViewById(R.id.passwordInput);
        String password = passwordInput.getText().toString();

        Spinner tonieboxInput = (Spinner) findViewById(R.id.tonieboxes);
        String tonieboxId = tonieboxInput.getSelectedItem().toString();

        // TODO test with no Toniebox ID selected, or no password, ...

        // Save password
        CheckBox storePasswordCheckbox = (CheckBox) findViewById(R.id.storePasswordCheckbox);
        if (storePasswordCheckbox.isChecked()) {
            storePassword(sharedPref, ssid, password);
        }

        // TODO actually connect

    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Context context = getApplicationContext();
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        EditText passwordInput = (EditText) findViewById(R.id.passwordInput);

        // Set up UI
        setupPasswordVisible(sharedPref);
        setupStorePassword(sharedPref);

        Button configureButton = (Button) findViewById(R.id.configureBtn);
        configureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                configure(sharedPref);
            }
        });

        // Set up Wifi (enable if it isn't on)
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(context, "Kein WLAN ... wird aktiviert", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }

        // Enter currently connected WLAN
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        boolean connected = wifiInfo.getSupplicantState() == SupplicantState.COMPLETED;
        String connectedSsid = connected ? wifiInfo.getSSID() : null;
        if (connected) {
            List<String> ssidList = new ArrayList<>();
            ssidList.add(connectedSsid);
            Spinner ssidInput = (Spinner) findViewById(R.id.ssidInput);
            setSsids(ssidInput, ssidList, connectedSsid);
            String stored = loadPassword(sharedPref, connectedSsid);
            if ((stored != null) && (passwordInput.getText().toString().length() == 0)) {
                passwordInput.setText(stored);
            }
        }

        // Scan for more WLANs (including the Toniebox)
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                List<ScanResult> wifiScanList = wifiManager.getScanResults();
                if (wifiScanList.size() == 0) {
                    // Nothing found, do not change
                    return;
                }
                List<String> ssids = new ArrayList<>();
                for (ScanResult sr : wifiScanList) {
                    ssids.add(sr.SSID);
                }

                // Update list of available access points
                if (!updatedWLANList) {
                    Spinner ssidInput = (Spinner) findViewById(R.id.ssidInput);
                    setSsids(ssidInput, ssids, connectedSsid);
                    updatedWLANList = true;
                }

                // Update list of Tonieboxes
                Spinner tonieboxes = (Spinner) findViewById(R.id.tonieboxes);
                List<String> tonieboxIDs = new ArrayList<>();
                for (ScanResult sr : wifiScanList) {
                    final String SSID_PREFIX = "Toniebox-";
                    if (sr.SSID.startsWith(SSID_PREFIX)) {
                        tonieboxIDs.add(sr.SSID.substring(SSID_PREFIX.length()));
                    }
                }
                setSsids(tonieboxes, tonieboxIDs, null);
            }
        };
        wlanScanRequestThread = new WLANScanRequestThread(wifiManager);
        wlanScanRequestThread.resumeScanning();

        // Focus password field
        // TODO only if we don't know the password
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(passwordInput, InputMethodManager.SHOW_IMPLICIT);
            }
        };
        final Timer timer = new Timer();
        timer.schedule(tt, 300);
    }

    protected void onPause() {
        unregisterReceiver(wifiScanReceiver);
        super.onPause();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    protected void onResume() {
        registerReceiver(
                wifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 87);
            }
        }
        super.onResume();
    }

}

// TODO load passwords

// TODO make configure button clickable once everything is present, no earlier
// TODO actually configure the toniebox


// TODO show scanning icon
// TODO button to reload scan list
// TODO deal with connection changes (or starting without Wifi)
// TODO deduplicate SSIDs
// TODO integrate new Wifis found to the bottom?
// TODO block UI when connecting to Toniebox
// TODO test with multiple Tonieboxes, keep selection
// TODO keep cursor in pasword field when switching between visible / invisible
// TODO password fields: disable autocompletion
