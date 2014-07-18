package com.example.mycalls;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class MestoLocationService extends Service {
    private final static String TAG = "MestoService";
    private final static int MAX_LOG_EVENTS = 100;
    private final static long sTwoMinutesNanos = TimeUnit.MINUTES.toNanos(2);

    private final Binder mBinder = new Binder();
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private final LinkedList<Event> mLogEvents = new LinkedList<Event>();
    private final Collection<Runnable> mRunnableCallbacks = new HashSet<Runnable>();
    private boolean mIsReporting = true;
    private UpnpController mUpnpController;
    private String mDeviceIdentifier;
    private Location mLastLocation;

    static class Event {

        private final static Type[] sTypes = Type.values();
        final Type mType;
        final long mTime;

        private Event(final Type type, final long time) {
            mType = type;
            mTime = time;
        }

        enum Type {
            Update,
            Start
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

        loadEvents();

        persistEvent(registerEvent(Event.Type.Start));

        startMonitoringLocation();
        mExecutor.submit(mServer);

        mUpnpController = new UpnpController();
        mDeviceIdentifier = mUpnpController.getDeviceIdentity().getUdn().getIdentifierString();
        mUpnpController = null;
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

    private final void loadEvents() {
        FileInputStream fis = null;
        DataInputStream dis = null;

        try {
            fis = openFileInput("eventLog");

            final long fileSize = fis.getChannel().size();
            final long maxSize = MAX_LOG_EVENTS * ((Byte.SIZE + Long.SIZE) / 8);

            int entries;
            if (fileSize > maxSize) {
                long offset = fileSize - maxSize;
                fis.skip(offset);
                entries = MAX_LOG_EVENTS;
            } else {
                entries = (int) (fileSize / ((Byte.SIZE + Long.SIZE) / 8));
            }

            dis = new DataInputStream(fis);
            while (entries-- > 0) {
                final byte typeIdx = dis.readByte();
                final long time = dis.readLong();
                final Event ev = new Event(Event.sTypes[typeIdx], time);
                mLogEvents.addFirst(ev);
            }

        } catch (final FileNotFoundException e) {

        } catch (final Exception e) {
            Log.e(TAG, "could not open events log", e);
            e.printStackTrace();
        } finally {
            if (null != dis) {
                try {
                    dis.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != fis) {
                try {
                    fis.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }
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

    private final LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(final Location location) {
            Log.d(TAG, "location: " + location);
            if (mIsReporting) {
                if (null != mLastLocation) {
                    final long timePassed = location.getElapsedRealtimeNanos() - mLastLocation.getElapsedRealtimeNanos();
                    if (timePassed < sTwoMinutesNanos) {
                        if (location.getAccuracy() > mLastLocation.getAccuracy()) {
                            Log.i(TAG, "skip reporting less accurate location");
                            return;
                        }
                    }
                }

                mLastLocation = location;
                sendLocation(location, mDeviceIdentifier, Build.DEVICE);
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
            /*final Location l = new Location(mLastLocation);
            l.setLatitude(37.390017);
            l.setLongitude(-121.955094);
            sendLocation(l, "TestDeviceUdn", "TestDevice");*/

            sendLocation(mLastLocation, mDeviceIdentifier, Build.DEVICE);
        } else {
            Log.e(TAG, "no known last location");
        }
    }

    boolean isUpnpOn() {
        return null != mUpnpController;
    }

    boolean isReporting() {
        return mIsReporting;
    }

    void stopUpnp() {
        Log.i(TAG, "stop upnp");
        if (null != mUpnpController) {
            final UpnpController uc = mUpnpController;
            mUpnpController = null;
            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    uc.shutdown();
                }
            };
            mExecutor.submit(r);
        }
    }

    void startUpnp() {
        Log.i(TAG, "start upnp");
        if (null == mUpnpController) {
            mUpnpController = new UpnpController();
            mUpnpController.initialize(this);
        }
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
            return (Collection<Event>) mLogEvents.clone();
        }
    }

    private void sendLocation(final Location location, final String udn, final String title) {
        final Runnable r = new Runnable() {
            @Override
            public final void run() {
                final Set<String> servers = Utilities.loadEndPoints(MestoLocationService.this);
                for (final String s : servers) {
                    final Runnable rr = new Runnable() {
                        @Override
                        public final void run() {
                            boolean successful = false;
                            for (int i = 0; i < 3; ++i) {
                                try {
                                    final URI uri = new URI("tcp://" + s);
                                    final Socket s = new Socket(InetAddress.getByName(uri.getHost()), uri.getPort());

                                    final ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
                                    final DataOutputStream dos = new DataOutputStream(baos);

                                    dos.writeUTF(udn);
                                    dos.writeUTF(title);
                                    dos.writeDouble(location.getLatitude());
                                    dos.writeDouble(location.getLongitude());

                                    final byte[] bytes = baos.toByteArray();
                                    s.getOutputStream().write(bytes);
                                    s.close();

                                    successful = true;
                                    Log.i(TAG, "peer " + s + " updated");
                                    break;
                                } catch (final Exception e) {
                                    Log.e(TAG, "error while sending update to server; exc: " + e);
                                    SystemClock.sleep(3000);
                                }
                            }

                            if (successful) {
                                persistEvent(registerEvent(Event.Type.Update));
                                for (final Runnable cb : mRunnableCallbacks) {
                                    cb.run();
                                }
                            }
                        }
                    };

                    mExecutor.execute(rr);
                }
            }
        };

        try {
            mExecutor.execute(r);
        } catch (final RejectedExecutionException e) {
            //ignored for now
        }
    }

    private final Event registerEvent(final Event.Type type) {
        Event result = null;
        final long lastUpdateTime = System.currentTimeMillis();

        synchronized (mLogEvents) {
            if (MAX_LOG_EVENTS == mLogEvents.size()) {
                mLogEvents.pollLast();
            }
            result = new Event(type, lastUpdateTime);
            mLogEvents.addFirst(result);
        }

        return result;
    }

    private final void persistEvent(final Event ev) {
        OutputStream os = null;
        DataOutputStream dos = null;

        try {
            os = openFileOutput("eventLog", Context.MODE_APPEND);
            dos = new DataOutputStream(os);

            dos.writeByte((byte) ev.mType.ordinal());
            dos.writeLong(ev.mTime);
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            if (null != dos) {
                try {
                    dos.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != os) {
                try {
                    os.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    final void addRunnableCallback(final Runnable r) {
        if (mRunnableCallbacks.add(r)) {
            mExecutor.submit(r);
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
        void onEvent(String udn, String product, double latitude, double longitude);
    }

    final Set<EventNotificationListener> mEventNotificationListeners = new HashSet<EventNotificationListener>();

    boolean addEventNotificationListener(final EventNotificationListener l) {
        return mEventNotificationListeners.add(l);
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

                final String udn = dis.readUTF();
                final String product = dis.readUTF();
                final double latitude = dis.readDouble();
                final double longitude = dis.readDouble();

                for (EventNotificationListener l : mEventNotificationListeners) {
                    l.onEvent(udn, product, latitude, longitude);
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
