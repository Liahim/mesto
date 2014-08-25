package bg.mrm.mesto;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import bg.mrm.mesto.upnp.UpnpController;

import static bg.mrm.mesto.Globals.TAG;

public class MestoLocationService extends Service {
    private static final boolean USE_NETWORK_AND_FUSED_PROVIDERS = true;
    private static final boolean USE_GPS_PROVIDER = false;
    private final static int MAX_LOG_EVENTS = 100;
    private final static long TWO_MINUTES_IN_NANOS = TimeUnit.MINUTES.toNanos(2);

    private final Binder mBinder = new Binder();
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private ScheduledExecutorService mScheduledExecutor;
    private ScheduledFuture<?> mScheduledFuture;
    private final LinkedList<Event> mLogEvents = new LinkedList<Event>();
    private final Collection<Runnable> mRunnableCallbacks = new HashSet<Runnable>();
    private boolean mIsReporting = true;
    private UpnpController mUpnpController;
    private String mDeviceId;

    private LocationListener mNetworkListener;
    private LocationListener mGpsListener;
    private LocationListener mPassiveListener;


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

        startReporting();
        mExecutor.submit(mServer);

        mUpnpController = new UpnpController(this, mExecutor);
        mDeviceId = mUpnpController.getOwnUdn();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();

        final Runnable r = new Runnable() {
            @Override
            public void run() {
                mUpnpController.down();
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
            Log.i(TAG, "eventLog file size " + fileSize);
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


    private final void startMonitoring(boolean provider) {
        final LocationManager lm = (LocationManager) this.getSystemService(
                Context.LOCATION_SERVICE);
        final List<String> ps = lm.getAllProviders();

        if (USE_NETWORK_AND_FUSED_PROVIDERS == provider) {
            if (ps.contains(LocationManager.NETWORK_PROVIDER)) {
                mNetworkListener = new MyLocationListener();
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10 * 60 * 1000,
                        500, mNetworkListener);
                Log.d(TAG, "network_provider selected");
            }

            if (ps.contains(LocationManager.PASSIVE_PROVIDER)) {
                mPassiveListener = new MyLocationListener();
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5 * 60 * 1000,
                        500, mPassiveListener);
                Log.d(TAG, "passive_provider selected");
            }
        } else if (USE_GPS_PROVIDER == provider) {
            if (ps.contains(LocationManager.GPS_PROVIDER)) {
                mGpsListener = new MyLocationListener();
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1 * 60 * 1000,
                        0, mGpsListener);
                Log.d(TAG, "gps_provider selected");
                scheduleGpsTimer();
            }
        }
        //@todo fallback strategies in case gps or network are unavailable
    }

    private void scheduleGpsTimer() {
        if (null != mGpsListener) {
            Log.d(TAG, "scheduleGpsTimer");

            if (null == mScheduledExecutor) {
                mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            } else if (null != mScheduledFuture) {
                mScheduledFuture.cancel(true);
            }

            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    stopMonitoring();
                    startMonitoring(USE_NETWORK_AND_FUSED_PROVIDERS);
                }
            };

            mScheduledFuture = mScheduledExecutor.schedule(r, 5, TimeUnit.MINUTES);
        }
    }

    private void stopGpsTimer() {
        if (null != mScheduledExecutor) {
            Log.d(TAG, "stopGpsTimer");

            if (null != mScheduledFuture) {
                mScheduledFuture.cancel(true);
                mScheduledFuture = null;
            }

            mScheduledExecutor.shutdownNow();
            mScheduledExecutor = null;
        }
    }

    private final void stopMonitoring() {
        final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (null != mGpsListener) {
            Log.d(TAG, "about to stop listening to gps_provider");
            locationManager.removeUpdates(mGpsListener);
            mGpsListener = null;

            try {
                stopGpsTimer();
            } catch (Exception e) {
                Log.e(TAG, "exception from stopGpsTimer", e);
            }
        }
        if (null != mPassiveListener) {
            Log.d(TAG, "about to stop listening to passive_provider");
            locationManager.removeUpdates(mPassiveListener);
            mPassiveListener = null;
        }
        if (null != mNetworkListener) {
            Log.d(TAG, "about to stop listening to network_provider");
            locationManager.removeUpdates(mNetworkListener);
            mNetworkListener = null;
        }
        mPreviousLocations.clear();
    }

    public void sendLocation() {
        final Location l = getLastLocation();
        if (null != l) {
            /*final Location l = new Location(mLastLocation);
            l.setLatitude(37.390017);
            l.setLongitude(-121.955094);
            sendLocation(l, "TestDeviceUdn", "TestDevice");*/

            sendLocation(l, mDeviceId, Build.DEVICE);
        } else {
            Log.e(TAG, "no paired last location");
        }
    }

    boolean isUpnpOn() {
        return mUpnpController.isUp();
    }

    boolean isReporting() {
        return mIsReporting;
    }

    void stopUpnp() {
        Log.i(TAG, "stop upnp");
        final UpnpController uc = mUpnpController;
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                uc.down();
            }
        };
        mExecutor.submit(r);
    }

    void startUpnp() {
        Log.i(TAG, "start upnp");
        mUpnpController.up();
    }

    void stopReporting() {
        Log.i(TAG, "stop reporting requested");
        mIsReporting = false;
        stopMonitoring();
    }

    void startReporting() {
        Log.i(TAG, "start reporting requested");
        mIsReporting = true;
        startMonitoring(USE_NETWORK_AND_FUSED_PROVIDERS);
    }

    Collection<Event> getLogEvents() {
        synchronized (mLogEvents) {
            return (Collection<Event>) mLogEvents.clone();
        }
    }

    private static final int DISTANCE_THRESHOLD = 100;    //meters
    private static final int MAX_PREVIOUS_LOCATIONS = 5;
    private ArrayList<Location> mPreviousLocations = new ArrayList<Location>();

    private boolean detectMovement(final Location location) {
        for (final Location l : mPreviousLocations) {
            final float distance = l.distanceTo(location);
            Log.i(TAG, "distance " + distance + "; " + l);
            if (l.distanceTo(location) > DISTANCE_THRESHOLD) {
                Log.i(TAG, "movement detected");
                return true;
            }
        }
        return false;
    }

    private Location getLastLocation() {
        final Location result;
        if (mPreviousLocations.size() > 0) {
            result = mPreviousLocations.get(0);
        } else {
            result = null;
        }
        return result;
    }

    private void sendLocation(final Location location, final String udn, final String title) {
        final boolean moving = detectMovement(location);
        if (null == mGpsListener && moving) {
            Log.i(TAG, "enabling gps provider; seems to be moving");
            stopMonitoring();
            startMonitoring(USE_GPS_PROVIDER);
        } else if (null != mGpsListener && !moving) {
            Log.i(TAG, "switching to network provider only; seems to be stationary");
            stopMonitoring();
            startMonitoring(USE_NETWORK_AND_FUSED_PROVIDERS);
        }
        rememberLastLocation(location);

        final Runnable updateRunnable = new Runnable() {
            @Override
            public final void run() {
                Collection<PeerRegistry.Endpoint> ee = mUpnpController.getRegistry().getUpdateEndpoints();

                for (final PeerRegistry.Endpoint e : ee) {
                    final Runnable peerRunnable = new Runnable() {
                        @Override
                        public final void run() {
                            boolean successful = false;
                            for (int i = 0; i < 3; ++i) {
                                try {
                                    final Socket s = new Socket(InetAddress.getByName(e.uri), e.portRange[0]);

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
                                    Log.i(TAG, "updated: " + e.uri);
                                    break;
                                } catch (final Exception exc) {
                                    Log.e(TAG, "update error: " + e.uri);
                                    SystemClock.sleep(3000 * (1 + i));
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

                    mExecutor.execute(peerRunnable);
                }
            }
        };

        try {
            mExecutor.execute(updateRunnable);
        } catch (final RejectedExecutionException e) {
            //ignored for now
        }
    }

    private void rememberLastLocation(final Location location) {
        if (mPreviousLocations.size() >= MAX_PREVIOUS_LOCATIONS) {
            mPreviousLocations.remove(mPreviousLocations.size() - 1);
        }
        mPreviousLocations.add(0, location);
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
                    Log.i(TAG, "local server at port 50001");
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

    private final class MyLocationListener implements LocationListener {
        public void onLocationChanged(final Location l) {
            Log.d(TAG, "location: " + l);

            if (mIsReporting) {
                scheduleGpsTimer();

                final Location ll = getLastLocation();
                if (null != ll) {
                    final long timePassed = l.getElapsedRealtimeNanos() - ll.getElapsedRealtimeNanos();
                    if (l.getAccuracy() >= ll.getAccuracy() && ll.distanceTo(l) < 50 && timePassed < TWO_MINUTES_IN_NANOS) {
                        Log.i(TAG, "skip reporting less accurate location");
                        return;
                    }
                }

                sendLocation(l, mDeviceId, Build.DEVICE);
            }
        }

        public void onStatusChanged(final String provider, final int status, final Bundle extras) {
            Log.d(TAG, "status changed: " + provider + ", " + status);
        }

        public void onProviderEnabled(final String provider) {
            Log.d(TAG, "provider enabled: " + provider);
        }

        public void onProviderDisabled(final String provider) {
            //network might be disabled; need fallback options if gps or netw are unavailable
            Log.d(TAG, "provider disabled: " + provider);
        }
    }

    public PeerRegistry getPeerRegistry() {
        return mUpnpController.getRegistry();
    }
}
