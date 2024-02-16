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
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;

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
    private RangingThread rangingThread = null;

    //private RegionBootstrap regionBootstrap;

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

    // Method to print a message
    public void printMessage(String message) {
        Log.d(TAG, message);
        // Display the message in a TextView or any other UI component
        // textViewMessage.setText(message);
    }

    // Method to start ranging beacons
    public void startRangingBeacons() {
        try {
            // Start ranging beacons using the BeaconManager instance
            beaconManager.startRangingBeaconsInRegion(region);
            Log.d(TAG, "Ranging beacons started.");
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting ranging beacons: " + e.getMessage());
        }
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
            // Set beacon scanning parameters based on Android version
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) { //kitkat, loolipop, marshmallow, nougat
                // Create a region to listen for beacons
                // Wake up the app when any beacon is seen (you can specify specific id filers in the parameters below)
                if (region == null)
                    region = new Region("BeSC", null, null, null);

                beaconManager.setBackgroundScanPeriod(200L); // NOT TRUE!: this period cannot be longer than 2 times 100ms (beacon broadcast interval)
                beaconManager.setBackgroundBetweenScanPeriod(650L); // non-round values to avoid the coincidence with beacon broadcast interval (100ms)
                beaconManager.setForegroundScanPeriod(200L);
                beaconManager.setForegroundBetweenScanPeriod(650L);

                BeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);
                RunningAverageRssiFilter.setSampleExpirationMilliseconds(1000l);
                ArmaRssiFilter.setDEFAULT_ARMA_SPEED(0.5);
                beaconManager.setBackgroundMode(true);
                beaconManager.setAndroidLScanningDisabled(true);
                Log.d(TAG, "Beacon scanner started and listening for Android < 8 ");
            } else {
                Log.e(TAG, "Beacon scanner started and listening for Android >= 8 ");
                // For Android Oreo and newer, use foreground service
                setBLEForegroundService(null);
            }

            // Start ranging thread if not already started
            if (rangingThread == null) {
                Log.e(TAG, getString(R.string.rangingThreadStarting));
                rangingThread = new RangingThread(region);
                rangingThread.waitForThreadToAwake(250);
                rangingThread.start();
                Log.e(TAG, getString(R.string.rangingThreadStarted) + ", bind=" + beaconManager.isBound(rangingThread));
            }

            // Bind BeaconManager to this activity
            beaconManager.bind(this);

            // Update UI
            scanStarted = true;
            buttonStartStop.setText(getString(R.string.stop));
        } catch (Throwable e) {
            Log.e(TAG, getString(R.string.errorWhileInvokingBeaconManager) + ":" + e.getLocalizedMessage());
        }
    }
/*
    public void stopScan() {
        scanStarted = false;
        Log.i(TAG, getString(R.string.scanStopped));
        buttonStartStop.setText(getString(R.string.start));
        if (beaconManager != null) {
            beaconManager.unbind(this);
        }
    }
    */
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

    //@Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        // Handle beacon ranging here
    }

    //    @Override
    public void didEnterRegion(Region region) {
        Log.d(TAG, getString(R.string.gotDidEnterRegionCall));
        if (rangingThread == null) {
            Log.d(TAG, getString(R.string.newRangingThread));
            rangingThread = new RangingThread(region);
            rangingThread.waitForThreadToAwake(2000);
            rangingThread.start();
        }
    }

    // @Override
    public void didExitRegion(Region region) {
        Log.d(TAG, getString(R.string.gotDidExitRegionCall));
    }

    //  @Override
    public void didDetermineStateForRegion(int state, Region region) {
        Log.d(TAG, getString(R.string.gotDidStateRegionCall) + '=' + state);
    }

    public boolean setBLEForegroundService(RangingThread notificationThread) {
        try {
            if (beaconManager.isBound(notificationThread)) {
                beaconManager.unbind(notificationThread);
                if (beaconManager.isBound(notificationThread))
                    Log.e(TAG, getString(R.string.scanningForBeaconsInFormerAndroidForegroundTroubleStillBonded) + "???");
            }
            beaconManager.disableForegroundServiceScanning();

            builderOreoForeground = new Notification.Builder(this);
            builderOreoForeground.setSmallIcon(R.drawable.beam_bw);
            builderOreoForeground.setContentTitle(getString(R.string.beaconScan));

            intentOreoForeground = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intentOreoForeground, PendingIntent.FLAG_UPDATE_CURRENT);
            builderOreoForeground.setContentIntent(pendingIntent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channelOreoForeground = new NotificationChannel(getString(R.string.notification_BLE_ID),
                        getString(R.string.notificationBLE), NotificationManager.IMPORTANCE_DEFAULT);
                channelOreoForeground.setDescription(getString(R.string.notificationChannelForBLEscan));
                notificationManagerOreoForeground = (NotificationManager)
                        getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManagerOreoForeground.createNotificationChannel(channelOreoForeground);
                builderOreoForeground.setChannelId(channelOreoForeground.getId());
            }

            beaconManager.setEnableScheduledScanJobs(false);

            if (beaconManager.isAnyConsumerBound()) {
                beaconManager.stopRangingBeaconsInRegion(region);
                beaconManager.stopMonitoringBeaconsInRegion(region);
                if (beaconManager.isAnyConsumerBound())
                    Log.e(TAG, getString(R.string.scanningForBeaconsInFormerAndroidForegroundTroubleStillBonded) + "?");
            }

            beaconManager.enableForegroundServiceScanning(builderOreoForeground.build(), 456);
            beaconManager.setEnableScheduledScanJobs(false);

            if (region == null)
                region = new Region("BeSC", null, null, null);


            beaconManager.setBackgroundScanPeriod(200L);
            beaconManager.setBackgroundBetweenScanPeriod(650L);
            beaconManager.setForegroundScanPeriod(200L);
            beaconManager.setForegroundBetweenScanPeriod(650L);
            beaconManager.setBackgroundMode(true);
            beaconManager.setAndroidLScanningDisabled(true);

            Log.e(TAG, getString(R.string.beaconScannerStartedAndListeningForAndroid8));
            return true;
        } catch (Throwable e) {
            Log.e(TAG, getString(R.string.scanningForBeaconsInFormerAndroidForegroundModeCompatibleWithOreoERROR) + ": " + e.getMessage());
            return false;
        }
    }



    @Override
    public void onBeaconServiceConnect() {
        if (currentAndroidVersion == ANDROID_VERSION_NOUGAT_AND_OLDER) {
            lastMomentDetectingBeacon = System.currentTimeMillis();
            RangeNotifier rangeNotifier = new RangeNotifier() {
                @Override
                public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                    lastMomentDetectingBeacon = System.currentTimeMillis();
                    String beaconReport = "";
                    if (beacons.size() > 0) {
                        for (Beacon beaconNext : beacons) {
                            beaconReport += "Beacon:\n" +
                                    "UUID=" + beaconNext.getId1().toString() + "\n" +
                                    "Major=" + beaconNext.getId2().toString() + "\n" +
                                    "Minor=" + beaconNext.getId3().toString() + "\n" +
                                    "Address=" + beaconNext.getBluetoothAddress().toString() + "\n" +
                                    "Distance=" + (int) (beaconNext.getDistance() * 100) + "cm\n\n";
                        }
                        printMessage(beaconReport);
                    }
                }
            };
            beaconManager.addRangeNotifier(rangeNotifier);
            startRangingBeacons();
        }
    }

    // Nested class representing your RangingThread
    public class RangingThread extends Thread implements BeaconConsumer {

        private RangeNotifier rangeNotifier;
        //private static RangingThread rangingThread;
        private long lastMomentDetectingBeacon = -1;
        private boolean anyBeaconDetected = false;
        private boolean isForegroundStarted = false;

        private boolean threadStarted = false;
        private BeaconManager beaconManager = null;
        private Region region = null;
        private char currentAndroidVersion = ANDROID_VERSION_NOUGAT_AND_OLDER;
        private static final char ANDROID_VERSION_OREO_AND_NEVER = 'o';
        private static final char ANDROID_VERSION_NOUGAT_AND_OLDER = 'n';

        // Initialize other necessary components for the thread
        protected RangingThread(Region region) {
            this.region = region;
            beaconManager = BeaconManager.getInstanceForApplication(MainActivity.this);

        }

        // Implementation of the thread's run method
        @Override
        public void run() {
            Log.d(TAG, MainActivity.this.getString(
                    R.string.beaconConsumerThreadStarted));
            long lastActivity = System.currentTimeMillis();
            long maxDelay = 30000L;//a half of a minute to restart, really pessimistic
            long now = -1L;
            while (threadStarted) {
                if (!anyBeaconDetected) {
// if no beacon detected and timeout reached, resetting the scanning
                    try {
                        if (currentAndroidVersion == ANDROID_VERSION_NOUGAT_AND_OLDER) {
                            if (!beaconManager.isBound(this)) {
                                beaconManager.bind(this);
                                MainActivity.this.startRangingBeacons();
                            }
                            if (lastMomentDetectingBeacon > 0) {
                                now = System.currentTimeMillis();
                                if (lastMomentDetectingBeacon + maxDelay < now) {
                                    lastMomentDetectingBeacon = now;
                                    lastActivity = now;
                                    beaconManager.unbind(this);
                                }
                            } else {
                                now = System.currentTimeMillis();
                                if (lastActivity + maxDelay < now) {
                                    beaconManager.unbind(this);
                                    lastActivity = now;
                                    beaconManager.bind(this);
                                    MainActivity.this.startRangingBeacons();
                                }
                            }
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, MainActivity.this.getString(
                                R.string.beaconConsumerThreadBindError) + " 3:" + e.getMessage());
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (Throwable e) {
                }
            }
            Log.d(TAG,
                    MainActivity.this.getString(R.string.beaconConsumerThreadAborted));
        }

        // Implementation of the exitThread method
        public void exitThread() {
            Log.d(TAG,
                    MainActivity.this.getString(R.string.beaconConsumerThreadDestroy));
            if (currentAndroidVersion == ANDROID_VERSION_NOUGAT_AND_OLDER) {
                beaconManager.unbind(this);
            }
            threadStarted = false;
        }

        // Implementation of the waitForThreadToAwake method
        public void waitForThreadToAwake(long maxDelay) {
            if (currentAndroidVersion == ANDROID_VERSION_NOUGAT_AND_OLDER) {
                long start = System.currentTimeMillis();
                long now = start;
                long maxTime = start + maxDelay;
                while ((!beaconManager.isBound(this)) && now < maxTime) {
                    try {
                        Thread.sleep(10);
                        now = System.currentTimeMillis();
                    } catch (Throwable e) {
                        //ilb}
                    }
                }
                Log.d(TAG, MainActivity.this.getString(
                        R.string.waitedForRunningThreadToAwake) +
                        ": " + (System.currentTimeMillis() - start) +
                        MainActivity.this.getString(R.string.ms));
            }
        }

        @Override
        public void onBeaconServiceConnect() {

        }

        @Override
        public Context getApplicationContext() {
            return MainActivity.this.getApplicationContext();
            //return null;
        }

        @Override
        public void unbindService(ServiceConnection connection) {
            // Implementation of unbindService method
        }

        @Override
        public boolean bindService(Intent intent, ServiceConnection connection, int mode) {
            // Implementation of bindService method
            return false;
        }
    }

        @Override
        public Context getApplicationContext() {
            return MainActivity.this.getApplicationContext();
        }

        @Override
        public void unbindService(ServiceConnection serviceConnection) {
            // Implementation of unbindService method
        }

        @Override
        public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
            // Implementation of bindService method
            return false;
        }


    // Other methods of BeaconConsumer
/*
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (beaconManager != null) {
            beaconManager.unbind(this);
        }
    }
    */
}