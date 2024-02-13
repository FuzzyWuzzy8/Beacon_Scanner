package pl.pue.air.beacon_scanner;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Collection;


public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    private static final String TAG = "BSc";
    private static final int PERMISSION_REQUEST_CODE = 223344;
    private boolean scanStarted = false;
    private BeaconManager beaconManager = null;
    private Region region = null;
    private TextView textViewAppName;
    private TextView textViewMessage;
    private Button buttonStartStop;
    private Notification.Builder builderOreoForeground = null;
    private Intent intentOreoForeground = null;
    private NotificationChannel channelOreoForeground = null;
    private NotificationManager notificationManagerOreoForeground = null;
    private long lastMomentDetectingBeacon = -1;

    //private RegionBootstrap regionBootstrap;
    //private RangingThread rangingThread = null;



    private static final char ANDROID_VERSION_OREO_AND_NEVER = 'o';
    private static final char ANDROID_VERSION_NOUGAT_AND_OLDER = 'n';
    private char currentAndroidVersion = ANDROID_VERSION_NOUGAT_AND_OLDER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        // Initialize BeaconManager
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")); // for ESTIMOTE only
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
            // Create a region to listen for beacons
            region = new Region("BeSC", null, null, null);

            // Bind BeaconManager to this activity
            beaconManager.bind(this);

            // Update UI
            scanStarted = true;
            buttonStartStop.setText(getString(R.string.stop));
        } catch (Throwable e) {
            Log.e(TAG, getString(R.string.errorWhileInvokingBeaconManager) + ":" + e.getLocalizedMessage());
        }
    }

    public void stopScan() {
        scanStarted = false;
        Log.i(TAG, getString(R.string.scanStopped));
        buttonStartStop.setText(getString(R.string.start));
        if (beaconManager != null) {
            beaconManager.unbind(this);
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        try {
            // Start monitoring the region
            beaconManager.startMonitoringBeaconsInRegion(region);
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting monitoring beacons: " + e.getMessage());
        }
    }

   // @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        // Handle beacon ranging here
    }

    // Other methods of BeaconConsumer

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (beaconManager != null) {
            beaconManager.unbind(this);
        }
    }
}