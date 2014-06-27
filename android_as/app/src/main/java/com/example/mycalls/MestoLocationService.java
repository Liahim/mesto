package com.example.mycalls;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.android.AndroidUpnpServiceConfiguration;
import org.teleal.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.teleal.cling.model.DefaultServiceManager;
import org.teleal.cling.model.ValidationException;
import org.teleal.cling.model.message.header.UDADeviceTypeHeader;
import org.teleal.cling.model.meta.DeviceDetails;
import org.teleal.cling.model.meta.DeviceIdentity;
import org.teleal.cling.model.meta.LocalDevice;
import org.teleal.cling.model.meta.LocalService;
import org.teleal.cling.model.meta.ManufacturerDetails;
import org.teleal.cling.model.meta.ModelDetails;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.types.DeviceType;
import org.teleal.cling.model.types.ServiceType;
import org.teleal.cling.model.types.UDADeviceType;
import org.teleal.cling.model.types.UDAServiceType;
import org.teleal.cling.model.types.UDN;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.registry.RegistryListener;
import org.teleal.common.logging.LoggingUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class MestoLocationService extends Service {
    private final static String TAG = "MestoService";
    private final static int MAX_LOG_EVENTS = 100;

    private final Binder mBinder = new Binder();
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private final ArrayDeque<Event> mLogEvents = new ArrayDeque<Event>(MAX_LOG_EVENTS);
    private final Collection<Runnable> mRunnableCallbacks = new HashSet<Runnable>();
    private boolean mIsReporting = true;

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
        LoggingUtil.resetRootHandler(new FixedAndroidHandler());

        /* Enable this for debug logging:
        Logger.getLogger("org.teleal.cling.transport.Router").setLevel(Level.FINEST);

        // UDP communication
        Logger.getLogger("org.teleal.cling.transport.spi.DatagramIO").setLevel(Level.FINE);
        Logger.getLogger("org.teleal.cling.transport.spi.MulticastReceiver").setLevel(Level.FINE);

        // Discovery
        Logger.getLogger("org.teleal.cling.protocol.ProtocolFactory").setLevel(Level.FINER);
        Logger.getLogger("org.teleal.cling.protocol.async").setLevel(Level.FINER);

        // Description
        Logger.getLogger("org.teleal.cling.protocol.ProtocolFactory").setLevel(Level.FINER);
        Logger.getLogger("org.teleal.cling.protocol.RetrieveRemoteDescriptors").setLevel(Level.FINE);
        Logger.getLogger("org.teleal.cling.transport.spi.StreamClient").setLevel(Level.FINEST);

        Logger.getLogger("org.teleal.cling.protocol.sync.ReceivingRetrieval").setLevel(Level.FINE);
        Logger.getLogger("org.teleal.cling.binding.xml.DeviceDescriptorBinder").setLevel(Level.FINE);
        Logger.getLogger("org.teleal.cling.binding.xml.ServiceDescriptorBinder").setLevel(Level.FINE);
        Logger.getLogger("org.teleal.cling.transport.spi.SOAPActionProcessor").setLevel(Level.FINEST);

        // Registry
        Logger.getLogger("org.teleal.cling.registry.Registry").setLevel(Level.FINER);
        Logger.getLogger("org.teleal.cling.registry.LocalItems").setLevel(Level.FINER);
        Logger.getLogger("org.teleal.cling.registry.RemoteItems").setLevel(Level.FINER);
        */

        java.util.logging.Logger.getLogger("org.teleal.cling").setLevel(Level.FINE);

        super.onCreate();
        recordEvent(Event.Type.Start);
        startMonitoringLocation();
        mExecutor.submit(mServer);

        //startUpnp();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
        if (null != mUpnpService) {
            mUpnpService.shutdown();
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

                            final ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
                            final DataOutputStream dos = new DataOutputStream(baos);

                            dos.writeDouble(location.getLatitude());
                            dos.writeDouble(location.getLongitude());
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
        mExecutor.execute(r);
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


    final RegistryListener mRegistryListener = new RegistryListener() {
        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            Log.i(TAG, "remote discover started: " + device);
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
            Log.e(TAG, "remote discover failed", ex);
        }

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            Log.i(TAG, "remote device added: " + device);
        }

        @Override
        public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
            Log.i(TAG, "remote device updated");
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            Log.i(TAG, "remote device removed");
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            Log.i(TAG, "local device added: " + device);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            Log.i(TAG, "local device removed");
        }

        @Override
        public void beforeShutdown(Registry registry) {
            Log.i(TAG, "before shutdown");
        }

        @Override
        public void afterShutdown() {
            Log.i(TAG, "after shutdown");
        }
    };

    private UpnpService mUpnpService;
    private BinaryLightServer mBinaryLightServer;

    public final void startUpnp() {
        final WifiManager wifiManager =
                (WifiManager) getSystemService(Context.WIFI_SERVICE);

        final ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        mUpnpService = new UpnpServiceImpl(createConfiguration(wifiManager), mRegistryListener);
        mBinaryLightServer = new BinaryLightServer();

        //mUpnpService.getControlPoint().search(new STAllHeader());
        UDADeviceType udaType = new UDADeviceType("BinaryLight");
        mUpnpService.getControlPoint().search(
                new UDADeviceTypeHeader(udaType)
        );
    }

    LocalDevice createDevice() throws IOException, ValidationException {
        DeviceIdentity identity = new DeviceIdentity(UDN.uniqueSystemIdentifier("Demo Binary Light"));
        DeviceType type = new UDADeviceType("BinaryLight", 1);

        DeviceDetails details = new DeviceDetails("Friendly Binary Light",
                new ManufacturerDetails("ACME"), new ModelDetails("BinLight2000",
                "A demo light with on/off switch", "v1")
        );

        LocalService<SwitchPower> switchPowerService
                = new AnnotationLocalServiceBinder().read(SwitchPower.class);
        switchPowerService.setManager(new DefaultServiceManager<SwitchPower>(
                switchPowerService, SwitchPower.class));

        return new LocalDevice(identity, type, details, switchPowerService);
    }

    public class BinaryLightServer implements Runnable {
        public BinaryLightServer() {
            Thread t = new Thread(this);
            t.setDaemon(false);
            t.start();
        }

        @Override
        public void run() {
            try {
                mUpnpService.getRegistry().addDevice(createDevice());
            } catch (Exception e) {
                Log.e(TAG, "addDevice failed", e);
            }
        }
    }


    public static class FixedAndroidHandler extends Handler {
        /**
         * Holds the formatter for all Android log handlers.
         */
        private static final Formatter THE_FORMATTER = new Formatter() {
            @Override
            public String format(LogRecord r) {
                Throwable thrown = r.getThrown();
                if (thrown != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    sw.write(r.getMessage());
                    sw.write("\n");
                    thrown.printStackTrace(pw);
                    pw.flush();
                    return sw.toString();
                } else {
                    return r.getMessage();
                }
            }
        };

        /**
         * Constructs a new instance of the Android log handler.
         */
        public FixedAndroidHandler() {
            setFormatter(THE_FORMATTER);
        }

        @Override
        public void close() {
            // No need to close, but must implement abstract method.
        }

        @Override
        public void flush() {
            // No need to flush, but must implement abstract method.
        }

        @Override
        public void publish(LogRecord record) {
            try {
                int level = getAndroidLevel(record.getLevel());
                String tag = record.getLoggerName();

                if (tag == null) {
                    // Anonymous logger.
                    tag = "null";
                } else {
                    // Tags must be <= 23 characters.
                    int length = tag.length();
                    if (length > 23) {
                        // Most loggers use the full class name. Try dropping the
                        // package.
                        int lastPeriod = tag.lastIndexOf(".");
                        if (length - lastPeriod - 1 <= 23) {
                            tag = tag.substring(lastPeriod + 1);
                        } else {
                            // Use last 23 chars.
                            tag = tag.substring(tag.length() - 23);
                        }
                    }
                }

            /* ############################################################################################

            Instead of using the perfectly fine java.util.logging API for setting the
            loggable levels, this call relies on a totally obscure "local.prop" file which you have to place on
            your device. By default, if you do not have that file and if you do not execute some magic
            "setprop" commands on your device, only INFO/WARN/ERROR is loggable. So whatever you do with
            java.util.logging.Logger.setLevel(...) doesn't have any effect. The debug messages might arrive
            here but they are dropped because you _also_ have to set the Android internal logging level with
            the aforementioned magic switches.

            Also, consider that you have to understand how a JUL logger name is mapped to the "tag" of
            the Android log. Basically, the whole cutting and cropping procedure above is what you have to
            memorize if you want to log with JUL and configure Android for debug output.

            I actually admire the pure evil of this setup, even Mr. Ceki can learn something!

            Commenting out these lines makes it all work as expected:

            if (!Log.isLoggable(tag, level)) {
                return;
            }

            ############################################################################################### */

                String message = getFormatter().format(record);
                Log.println(level, tag, message);
            } catch (RuntimeException e) {
                Log.e("AndroidHandler", "Error logging message.", e);
            }
        }

        /**
         * Converts a {@link java.util.logging.Logger} logging level into an Android one.
         *
         * @param level The {@link java.util.logging.Logger} logging level.
         * @return The resulting Android logging level.
         */
        static int getAndroidLevel(Level level) {
            int value = level.intValue();
            if (value >= 1000) { // SEVERE
                return Log.ERROR;
            } else if (value >= 900) { // WARNING
                return Log.WARN;
            } else if (value >= 800) { // INFO
                return Log.INFO;
            } else {
                return Log.DEBUG;
            }
        }

    }

    private AndroidUpnpServiceConfiguration createConfiguration(WifiManager wifiManager) {
        return new AndroidUpnpServiceConfiguration(wifiManager) {
            @Override
            public ServiceType[] getExclusiveServiceTypes() {
                return new ServiceType[]{
                        new UDAServiceType("SwitchPower")
                };
            }

        };
    }
}
