
package com.example.mycalls;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MestoLocationService extends Service {
    final static String TAG = "Mesto";
    private final Binder mBinder = new Binder();

    public MestoLocationService() {
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);
        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startMonitoringLocation();
    }

    public final class Binder extends android.os.Binder {
        public MestoLocationService getService() {
            return MestoLocationService.this;
        }
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return mBinder;
    }

    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private Location mLastLocation;
    private final LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(final Location location) {
            mLastLocation = location;
            Log.d(TAG, "location: " + location);
            if (mIsReporting) {
                sendLocation(location);
            }
        }

        public void onStatusChanged(final String provider, final int status, final Bundle extras) {
        }

        public void onProviderEnabled(final String provider) {
        }

        public void onProviderDisabled(final String provider) {
        }
    };

    private final void startMonitoringLocation() {
        final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60 * 1000, 50, mLocationListener);
            Log.d(TAG, "network_provider selected");
        }

        if (locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60 * 1000, 50, mLocationListener);
            Log.d(TAG, "gps_provider selected");
        }
    }

    private final void stopMonitoringLocation() {
        final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(mLocationListener);
    }

    public void sendLocation() {
        if (null != mLastLocation) {
            sendLocation(mLastLocation);
        } else {
            Log.e(TAG, "no known last location");
        }
    }

    private boolean mIsReporting = true;

    boolean isReporting() {
        return mIsReporting;
    }

    void stopReporting() {
        Log.i(TAG, "stop reporting requested");
        mIsReporting = false;
        stopMonitoringLocation();
    }

    void startReporting() {
        Log.i(TAG, "start reporting requested");
        mIsReporting = true;
        startMonitoringLocation();
    }

    private long mLastUpdateTime;

    long getLastUpdateTime() {
        return mLastUpdateTime;
    }

    private void sendLocation(final Location location) {
        final Runnable r = new Runnable() {
            @Override
            public final void run() {
                try {
                    final String server = MestoActivity.loadServerLocation(MestoLocationService.this);
                    if (null != server) {
                        final URI uri = new URI("tcp://" + server);
                        final Socket s = new Socket(InetAddress.getByName(uri.getHost()), uri.getPort());

                        final ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
                        final DataOutputStream dos = new DataOutputStream(baos);

                        dos.writeDouble(location.getLatitude());
                        dos.writeDouble(location.getLongitude());
                        final byte[] bytes = baos.toByteArray();
                        s.getOutputStream().write(bytes);

                        s.close();

                        mLastUpdateTime = System.currentTimeMillis();
                        if (null != mRunnable) {
                            mRunnable.run();
                        }
                    }
                } catch (final Exception e) {
                    Log.d(TAG, "error writing to server: ", e);
                }
            }
        };
        mExecutor.execute(r);
    }

    private Runnable mRunnable;

    final void setRunnableCallback(final Runnable r) {
        mRunnable = r;
    }

}
