
package com.example.mycalls;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MestoLocationService extends Service {

    public MestoLocationService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        monitorLocation();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    private final void monitorLocation() {
        // Acquire a reference to the system Location Manager
        final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        final LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(final Location location) {
                System.err.println("mycalls: location: " + location);

                final Runnable r = new Runnable() {
                    @Override
                    public final void run() {
                        try {
                            Socket s = new Socket(InetAddress.getByName("hubabuba.asuscomm.com"), 5001);

                            final ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
                            DataOutputStream dos = new DataOutputStream(baos);

                            dos.writeDouble(location.getLatitude());
                            dos.writeDouble(location.getLongitude());
                            final byte[] bytes = baos.toByteArray();
                            s.getOutputStream().write(bytes);

                            s.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                mExecutor.execute(r);
            }

            public void onStatusChanged(final String provider, final int status, final Bundle extras) {
            }

            public void onProviderEnabled(final String provider) {
            }

            public void onProviderDisabled(final String provider) {
            }
        };

        if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60*1000, 50, locationListener);
            System.err.println("mycalls: network_provider selected");
        }

        if (locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60*1000, 50, locationListener);
            System.err.println("mycalls: gps_provider selected");
        }
    }

}
