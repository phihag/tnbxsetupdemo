package de.phihag.tnbxsetupdemo;

import android.net.wifi.WifiManager;
import android.util.Log;

class WLANScanRequestThread extends Thread {
    private final long EVERY_MS = 3000;
    private WifiManager wifiManager;
    private boolean paused;

    public WLANScanRequestThread(WifiManager wifiManager) {
        super();
        this.setDaemon(true);
        this.wifiManager = wifiManager;
        this.paused = true;
    }

    public void run() {
        while (!paused) {
            this.wifiManager.startScan();
            try {
                Thread.sleep(EVERY_MS);
            } catch (InterruptedException e) {
                Log.w("WLANScanRequestThread", "sleep interrupted");
            }
        }
    }

    public void pauseScanning() {
        this.paused = true;
    }

    public void resumeScanning() {
        if (!paused) {
            Log.w("WLANScanRequestThread", "Resuming despite not being paused");
        }
        paused = false;
        this.start();
    }
}
