package bg.mrm.mesto;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;

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
import java.util.concurrent.TimeUnit;

import bg.mrm.mesto.upnp.UpnpController;

public class MestoLocationService extends Service {
    private static final boolean OK_PRECISION_OK_POWER = true;
    private static final boolean HIGH_PRECISION_HIGH_POWER = false;
    private final static int MAX_LOG_EVENTS = 100;
    private final static long TWO_MINUTES_IN_NANOS = TimeUnit.MINUTES.toNanos(2);

    private static final int DISTANCE_THRESHOLD = 100;    //meters
    private static final int MAX_PREVIOUS_LOCATIONS = 5;

    private final Binder mBinder = new Binder();
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    private final LinkedList<Event> mLogEvents = new LinkedList<Event>();
    private final Collection<Runnable> mRunnableCallbacks = new HashSet<Runnable>();
    private boolean mIsReporting = true;
    private UpnpController mUpnpController;
    private String mDeviceId;

    private LocationListener mNetworkListener;
    private LocationListener mGpsListener;
    private LocationListener mPassiveListener;

    private Handler mHandler;

    private Location mLastLocation;
    private ArrayList<Location> mPreviousLocations = new ArrayList<Location>();

    private final Runnable mSwitchToNetworkProviderRunnable = new Runnable() {
        @Override
        public void run() {
            Utilities.log("gps watchdog running");
            try {
                stopMonitoring();
            } catch (final Exception e) {
                Utilities.log("exception from stopMonitoring: " + e);
            }

            try {

                startMonitoring(OK_PRECISION_OK_POWER);
            } catch (final Exception e) {
                Utilities.log("exception from startMonitoring: " + e);
            }
        }
    };


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
        Utilities.openLogger(this);

        mHandler = new Handler();
        loadEvents();

        persistEvent(registerEvent(Event.Type.Start));

        mExecutor.submit(mServer);

        mUpnpController = new UpnpController(this, mExecutor);
        mDeviceId = mUpnpController.getOwnUdn();

        startReporting();
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
            Utilities.log("eventLog file size " + fileSize);
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
            Utilities.log("could not open events log: " + e);
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

    //must be run on the ui thread
    private final void startMonitoring(boolean provider) {
        final LocationManager lm = (LocationManager) this.getSystemService(
                Context.LOCATION_SERVICE);
        final List<String> ps = lm.getAllProviders();

        if (OK_PRECISION_OK_POWER == provider) {
            if (ps.contains(LocationManager.NETWORK_PROVIDER)) {
                mNetworkListener = new MyLocationListener();
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10 /** 60 */ * 1000,
                        500, mNetworkListener);
                Utilities.log("network_provider selected");
            } else if (ps.contains(LocationManager.PASSIVE_PROVIDER)) {
                mPassiveListener = new MyLocationListener();
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5 * 60 * 1000,
                        500, mPassiveListener);
                Utilities.log("passive_provider selected");
            }
        } else if (HIGH_PRECISION_HIGH_POWER == provider) {
            if (ps.contains(LocationManager.GPS_PROVIDER)) {
                mGpsListener = new MyLocationListener();
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1 * 60 * 1000,
                        0, mGpsListener);
                Utilities.log("gps_provider selected");
                scheduleGpsTimer();
            }
        }
    }

    private final void stopMonitoring() {
        final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (null != mGpsListener) {
            Utilities.log("about to stop listening to gps_provider");
            locationManager.removeUpdates(mGpsListener);
            mGpsListener = null;

            try {
                stopGpsTimer();
            } catch (Exception e) {
                Utilities.log("exception from stopGpsTimer: " + e);
            }
        }
        if (null != mPassiveListener) {
            Utilities.log("about to stop listening to passive_provider");
            locationManager.removeUpdates(mPassiveListener);
            mPassiveListener = null;
        }
        if (null != mNetworkListener) {
            Utilities.log("about to stop listening to network_provider");
            locationManager.removeUpdates(mNetworkListener);
            mNetworkListener = null;
        }

        mLastLocation = null;
        mPreviousLocations.clear();
    }

    private void scheduleGpsTimer() {
        if (null != mGpsListener) {
            Utilities.log("scheduleGpsTimer");

            stopGpsTimer();
            mHandler.postDelayed(mSwitchToNetworkProviderRunnable, TimeUnit.MINUTES.toMillis(5));
        }
    }

    private void stopGpsTimer() {
        mHandler.removeCallbacks(mSwitchToNetworkProviderRunnable);
    }

    public void sendLocation() {
        if (null != mLastLocation) {
            /*final Location l = new Location(mLastLocation);
            l.setLatitude(37.390017);
            l.setLongitude(-121.955094);
            sendLocation(l, "TestDeviceUdn", "TestDevice");*/

            sendLocation(mLastLocation, mDeviceId, Build.DEVICE);
        } else {
            Utilities.log("no location to send");
        }
    }

    boolean isUpnpOn() {
        return mUpnpController.isUp();
    }

    boolean isReporting() {
        return mIsReporting;
    }

    void stopUpnp() {
        Utilities.log("stop upnp");
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
        Utilities.log("start upnp");
        mUpnpController.up();
    }

    void stopReporting() {
        Utilities.log("stop reporting requested");
        mIsReporting = false;
        stopMonitoring();
    }

    void startReporting() {
        Utilities.log("start reporting requested");
        mIsReporting = true;
        startMonitoring(OK_PRECISION_OK_POWER);
    }

    Collection<Event> getLogEvents() {
        synchronized (mLogEvents) {
            return (Collection<Event>) mLogEvents.clone();
        }
    }

    enum MovementState {
        Stationary,
        Moving,
        Indeterminable
    }

    private MovementState detectMovement(final Location location) {
        MovementState result = MovementState.Indeterminable;

        if (0 < mPreviousLocations.size()) {
            result = MovementState.Stationary;

            for (final Location l : mPreviousLocations) {
                final float distance = l.distanceTo(location);
                Utilities.log("distance " + distance + "; " + l);
                if (l.distanceTo(location) > DISTANCE_THRESHOLD) {
                    Utilities.log("movement detected");
                    result = MovementState.Moving;
                    break;
                }
            }
        }

        return result;
    }

    private void sendLocation(final Location location, final String udn, final String title) {
        final Runnable updateRunnable = new Runnable() {
            @Override
            public final void run() {
                Collection<PeerRegistry.Endpoint> ee = mUpnpController.getRegistry().getUpdateEndpoints();

                for (final PeerRegistry.Endpoint e : ee) {
                    final Runnable peerRunnable = new Runnable() {
                        @Override
                        public final void run() {
                            for (int i = 0; i < 3; ++i) {
                                if (socketWrite(e, i)) {
                                    persistEvent(registerEvent(Event.Type.Update));
                                    for (final Runnable cb : mRunnableCallbacks) {
                                        cb.run();
                                    }
                                    break;
                                }
                            }
                        }
                    };

                    mExecutor.execute(peerRunnable);
                }
            }

            private final boolean socketWrite(final PeerRegistry.Endpoint e, final int sleepFactor) {
                boolean result = false;

                final ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
                final DataOutputStream dos = new DataOutputStream(baos);
                Socket s = null;

                try {
                    s = new Socket(InetAddress.getByName(e.uri), e.portRange[0]);

                    dos.writeUTF(udn);
                    dos.writeUTF(title);
                    dos.writeDouble(location.getLatitude());
                    dos.writeDouble(location.getLongitude());

                    final byte[] bytes = baos.toByteArray();
                    s.getOutputStream().write(bytes);
                    s.close();

                    result = true;
                    Utilities.log("updated: " + e.uri);

                } catch (final Exception exc) {
                    Utilities.log("update error: " + e.uri);
                    SystemClock.sleep(3000 * (1 + sleepFactor));

                } finally {
                    try {
                        dos.close();
                    } catch (IOException e1) {
                    }
                    try {
                        baos.close();
                    } catch (IOException e1) {
                    }
                    if (null != s) {
                        try {
                            s.close();
                        } catch (IOException e1) {
                        }
                    }
                }

                return result;
            }
        };

        try {
            mExecutor.execute(updateRunnable);
        } catch (final RejectedExecutionException e) {
            //ignored for now
        }
    }

    private void switchMonitorIfNecessary(final MovementState currentState) {
        if (null == mGpsListener && MovementState.Moving == currentState) {
            Utilities.log("enable gps provider; in motion");
            stopMonitoring();
            startMonitoring(HIGH_PRECISION_HIGH_POWER);
        } else if (null != mGpsListener && MovementState.Stationary == currentState) {
            Utilities.log("switch to network provider; stationary");
            stopMonitoring();
            startMonitoring(OK_PRECISION_OK_POWER);
        }
    }

    private void updateLocationHistory(final Location location) {
        if (null != mLastLocation) {
            if (mPreviousLocations.size() >= MAX_PREVIOUS_LOCATIONS) {
                mPreviousLocations.remove(mPreviousLocations.size() - 1);
            }
            mPreviousLocations.add(0, mLastLocation);
        }
        mLastLocation = location;
    }

    private final Event registerEvent(final Event.Type type) {
        final long lastUpdateTime = System.currentTimeMillis();
        Event result;

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
                    Utilities.log("local server at port 50001");
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
                Utilities.log("local server processing request from " + mSocket.getInetAddress());
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
            Utilities.log("location: " + l);

            if (mIsReporting) {
                scheduleGpsTimer();

                if (null != mLastLocation) {
                    final long timePassed = l.getElapsedRealtimeNanos() - mLastLocation.getElapsedRealtimeNanos();
                    if (l.getAccuracy() >= mLastLocation.getAccuracy()
                            && mLastLocation.distanceTo(l) < 50 && timePassed < TWO_MINUTES_IN_NANOS) {
                        Utilities.log("skip reporting less accurate location");
                        return;
                    }
                }

                updateLocationHistory(l);
                switchMonitorIfNecessary(detectMovement(l));

                sendLocation(l, mDeviceId, Build.DEVICE);
            }
        }

        public void onStatusChanged(final String provider, final int status, final Bundle extras) {
            Utilities.log("status changed: " + provider + ", " + status);
        }

        public void onProviderEnabled(final String provider) {
            Utilities.log("provider enabled: " + provider);
        }

        public void onProviderDisabled(final String provider) {
            //network might be disabled; need fallback options if gps or netw are unavailable
            Utilities.log("provider disabled: " + provider);
        }
    }

    public PeerRegistry getPeerRegistry() {
        return mUpnpController.getRegistry();
    }
}
