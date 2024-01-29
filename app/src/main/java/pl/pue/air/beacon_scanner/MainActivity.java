package pl.pue.air.beacon_scanner;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.RegionBootstrap;

public class MainActivity extends AppCompatActivity implements BootstrapNotifier {
    private static final String TAG = "BSc";
    private static final int PERMISSION_REQUEST_CODE = 223344;

    private boolean scanStarted = false;
    private RegionBootstrap regionBootstrap;
    private Region region = null;
    private BeaconManager beaconManager = null;
    private RangingThread rangingThread = null;
    private Notification.Builder builderOreoForeground = null;
    private Intent intentOreoForeground = null;
    private NotificationChannel channelOreoForeground = null;
    private NotificationManager notificationManagerOreoForeground = null;
    private TextView textViewAppName;
    private TextView textViewMessage;
    private Button buttonStartStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mainActivity = this;
        setContentView(R.layout.activity_main);
        textViewAppName = findViewById(R.id.textViewAppName);
        textViewMessage = findViewById(R.id.textViewMessage);
        buttonStartStop = findViewById(R.id.buttonStartStop);
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN
                }, PERMISSION_REQUEST_CODE);
            }
        }
        buttonStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (scanStarted) {
                    stopScan();
                } else {
                    startScan();
                }
            }
        });
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int p = 0; p < permissions.length; p++) {
            if (checkSelfPermission(permissions[p]) ==
                    PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, getString(R.string.accessRightsGranted) + ": " + p);
            } else {
                Log.e(TAG, getString(R.string.accessRightsNOTgranted) + ": " + p);
            }
        }
    }

    public void startScan() {
        try {
            beaconManager = BeaconManager.getInstanceForApplication(mainActivity.getApplication());
            beaconManager.getBeaconParsers().add(new BeaconParser().
                    setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                if (region == null)
                    region = new Region("BeSC", null, null, null);
                if (regionBootstrap == null)
                    regionBootstrap = new RegionBootstrap(mainActivity, region);
                beaconManager.setBackgroundScanPeriod(200L);
                beaconManager.setBackgroundBetweenScanPeriod(650L);
                beaconManager.setForegroundScanPeriod(200L);
                beaconManager.setForegroundBetweenScanPeriod(650L);
                beaconManager.setBackgroundMode(true);
                beaconManager.setAndroidLScanningDisabled(true);
                Log.d(TAG, "Beacon scanner started and listening for Android < 8 ");
            } else {
                Log.e(TAG, "Beacon scanner started and listening for Android >= 8 ");
            }
            if (rangingThread == null) {
                Log.e(TAG, getString(R.string.rangingThreadStarting));
                rangingThread = new RangingThread(region);
                rangingThread.waitForThreadToAwake(250);
                rangingThread.start();
                Log.e(TAG, getString(R.string.rangingThreadStarted) +
                        ", bind=" + beaconManager.isBound(rangingThread));
            }
            scanStarted = true;
            buttonStartStop.setText(getString(R.string.stop));
        } catch (Throwable e) {
            Log.e(TAG, getString(R.string.errorWhileInvokingBeaconManager) +
                    ":" + e.getLocalizedMessage());
        }
    }

    public void stopScan() {
        scanStarted = false;
        Log.i(TAG, getString(R.string.scanStopped));
        textViewMessage.setText(getString(R.string.scanStopped));
        buttonStartStop.setText(getString(R.string.start));
        if (rangingThread != null) {
            rangingThread.exitThread();
            rangingThread = null;
        }
    }

    @Override
    public void didEnterRegion(Region region) {
        Log.d(TAG, getString(R.string.gotDidEnterRegionCall));
        if (rangingThread == null) {
            Log.d(TAG, getString(R.string.newRangingThread));
            rangingThread = new RangingThread(region);
            rangingThread.waitForThreadToAwake(2000);
            rangingThread.start();
        }
    }

    @Override
    public void didExitRegion(Region region) {
        Log.d(TAG, getString(R.string.gotDidExitRegionCall));
    }

    @Override
    public void didDetermineStateForRegion(int state, Region region) {
        Log.d(TAG, getString(R.string.gotDidStateRegionCall) + '=' + state);
    }
}