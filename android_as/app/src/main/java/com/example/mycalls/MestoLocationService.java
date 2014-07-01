package com.example.mycalls;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MestoLocationService extends Service {
    private final static String TAG = "MestoService";
    private final static int MAX_LOG_EVENTS = 100;

    private final Binder mBinder = new Binder();
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private final ArrayDeque<Event> mLogEvents = new ArrayDeque<Event>(MAX_LOG_EVENTS);
    private final Collection<Runnable> mRunnableCallbacks = new HashSet<Runnable>();
    private boolean mIsReporting = true;
    private final UpnpController mUpnpController = new UpnpController();

    static class Event implements Parcelable {

        private final static Type[] sTypes = Type.values();
        final Type mType;
        final long mTime;

        private Event(final Type type, final long time) {
            mType = type;
            mTime = time;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<Event> CREATOR = new Parcelable.Creator<Event>() {
            public Event createFromParcel(Parcel in) {
                return new Event(in);
            }

            public Event[] newArray(int size) {
                return new Event[size];
            }
        };

        private Event(Parcel in) {
            mType = sTypes[in.readInt()];
            mTime = in.readLong();
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeInt(mType.ordinal());
            dest.writeLong(mTime);
        }


        enum Type {
            Update,
            Start;
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);
        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        recordEvent(Event.Type.Start);
        startMonitoringLocation();
        mExecutor.submit(mServer);

        //mUpnpController.initialize(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                mUpnpController.shutdown();
            }
        };
        mExecutor.submit(r);
        mExecutor.shutdown();
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

    public Location getLocation() {
        return mLastLocation;
    }

    public void sendLocation() {
        if (null != mLastLocation) {
            sendLocation(mLastLocation);
        } else {
            Log.e(TAG, "no known last location");
        }
    }

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

    Collection<Event> getLogEvents() {
        synchronized (mLogEvents) {
            return mLogEvents.clone();
        }
    }

    private void sendLocation(final Location location) {
        final Runnable r = new Runnable() {
            @Override
            public final void run() {
                final String server = MestoActivity.loadServerLocation(MestoLocationService.this);
                if (null != server) {

                    boolean successful = false;
                    for (int i = 0; i < 3; ++i) {
                        try {
                            final URI uri = new URI("tcp://" + server);
                            final Socket s = new Socket(InetAddress.getByName(uri.getHost()), uri.getPort());

                            final ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
                            final DataOutputStream dos = new DataOutputStream(baos);

                            dos.writeDouble(location.getLatitude());
                            dos.writeDouble(location.getLongitude());
                            dos.writeUTF(mUpnpController.getDeviceIdentity().getUdn().getIdentifierString());

                            final byte[] bytes = baos.toByteArray();
                            s.getOutputStream().write(bytes);
                            s.close();

                            successful = true;
                            break;
                        } catch (final Exception e) {
                            Log.e(TAG, "error while sending update to server", e);
                        }
                    }

                    if (successful) {
                        recordEvent(Event.Type.Update);
                        for (final Runnable cb : mRunnableCallbacks) {
                            cb.run();
                        }
                    }
                }
            }
        };

        try {
            mExecutor.execute(r);
        } catch (final RejectedExecutionException e) {
            //ignored for now
        }
    }


    private void recordEvent(Event.Type type) {
        final long lastUpdateTime = System.currentTimeMillis();
        synchronized (mLogEvents) {
            if (MAX_LOG_EVENTS == mLogEvents.size()) {
                mLogEvents.pollLast();
            }
            mLogEvents.addFirst(new Event(type, lastUpdateTime));
        }
    }

    final void addRunnableCallback(final Runnable r) {
        if (mRunnableCallbacks.add(r)) {
            r.run();
        }
    }

    final boolean removeRunnableCallback(final Runnable r) {
        return mRunnableCallbacks.remove(r);
    }


    // Server below
    private final Runnable mServer = new Runnable() {
        @Override
        public void run() {
            try {
                final ServerSocket serverSocket = new ServerSocket(50001);
                while (true) {
                    Log.i(TAG, "local server running at port 50001");
                    //@todo make it cancelable
                    final Socket socket = serverSocket.accept();
                    mExecutor.submit(new SocketRunnable(socket));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    interface EventNotificationListener {
        void onEvent(double latitude, double longitude);
    }

    final Set<EventNotificationListener> mEventNotificationListeners = new HashSet<EventNotificationListener>();

    boolean addEventNotificationListener(final EventNotificationListener l) {
        return mEventNotificationListeners.add(l);
    }

    boolean removeEventNotificationListener(final EventNotificationListener l) {
        return mEventNotificationListeners.remove(l);
    }


    private final SimpleDateFormat mFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);

    private final class SocketRunnable implements Runnable {
        private final Socket mSocket;

        private SocketRunnable(final Socket s) {
            mSocket = s;
        }

        @Override
        public void run() {
            try {
                Log.i(TAG, "local server processing request from " + mSocket.getInetAddress());
                final DataInputStream dis = new DataInputStream(mSocket.getInputStream());

                final double latitude = dis.readDouble();
                final double longitude = dis.readDouble();

                for (EventNotificationListener l : mEventNotificationListeners) {
                    l.onEvent(latitude, longitude);
                }
            } catch (final IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    mSocket.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    UpnpController getUpnpController() {
        return mUpnpController;
    }
}
