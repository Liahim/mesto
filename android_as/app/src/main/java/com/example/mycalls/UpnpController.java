package com.example.mycalls;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.android.AndroidUpnpServiceConfiguration;
import org.teleal.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.teleal.cling.controlpoint.ActionCallback;
import org.teleal.cling.controlpoint.SubscriptionCallback;
import org.teleal.cling.model.DefaultServiceManager;
import org.teleal.cling.model.ValidationException;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.gena.CancelReason;
import org.teleal.cling.model.gena.GENASubscription;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.message.header.UDAServiceTypeHeader;
import org.teleal.cling.model.meta.Action;
import org.teleal.cling.model.meta.Device;
import org.teleal.cling.model.meta.DeviceDetails;
import org.teleal.cling.model.meta.DeviceIdentity;
import org.teleal.cling.model.meta.LocalDevice;
import org.teleal.cling.model.meta.LocalService;
import org.teleal.cling.model.meta.ManufacturerDetails;
import org.teleal.cling.model.meta.ModelDetails;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.types.DeviceType;
import org.teleal.cling.model.types.ServiceType;
import org.teleal.cling.model.types.UDADeviceType;
import org.teleal.cling.model.types.UDAServiceId;
import org.teleal.cling.model.types.UDAServiceType;
import org.teleal.cling.model.types.UDN;
import org.teleal.cling.model.types.csv.CSVString;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.registry.RegistryListener;
import org.teleal.common.logging.LoggingUtil;

import java.io.IOException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UpnpController {
    final static String TAG = "Mesto";

    private final DeviceIdentity mIdentity;
    private UpnpService mUpnpService;
    private Context mApplicationContext;

    public UpnpController() {
        //not reliable, need to replace with my uuid generated during first launch
        mIdentity = new DeviceIdentity(UDN.uniqueSystemIdentifier("Mesto"));
    }

    final void initialize(final Context context) {
        mApplicationContext = context.getApplicationContext();

        //enableLogging();

        //actually throws a NetworkOnMain but will keep it intentionally
        //so cling uses the mac address instead of localhost for udn
        //generation
        startUpnp();
    }

    final void shutdown() {
        if (null != mUpnpService) {
            mUpnpService.shutdown();
        }
    }

    final DeviceIdentity getDeviceIdentity() {
        return mIdentity;
    }

    interface PeerNotifications {
        void onAdded(UDN udn, AbstractList<String> mesto);

        void onRemoved(UDN udn);
    }

    private final Set<PeerNotifications> mPeerNotifications
            = Collections.newSetFromMap(new ConcurrentHashMap<PeerNotifications, Boolean>());

    boolean addPeerNotificationsListener(PeerNotifications l) {
        return mPeerNotifications.add(l);
    }

    private final RegistryListener mRegistryListener = new RegistryListener() {
        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            Log.i(TAG, "remote discover started: " + device);
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
            Log.e(TAG, "remote discover failed", ex);
        }

        @Override
        public void remoteDeviceAdded(Registry registry, final RemoteDevice device) {
            Log.i(TAG, "remote device added: " + device);
            processDevice(device);
        }

        @Override
        public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
            Log.i(TAG, "remote device updated");
            processDevice(device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            Log.i(TAG, "remote device removed");
            for (final PeerNotifications l : mPeerNotifications) {
                l.onRemoved(device.getIdentity().getUdn());//@todo
            }
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

        private final void processDevice(final RemoteDevice device) {
            final Service service = device.findService(new UDAServiceId(MestoPeer.ID));
            final Action action = service.getAction("GetMesto");
            final ActionInvocation invocation = new ActionInvocation(action);

            final ActionCallback setTargetCallback = new ActionCallback(invocation) {
                @Override
                public void success(ActionInvocation invocation) {
                    final CSVString mesto
                            = new CSVString((String) invocation.getOutput("Mesto").getValue());
                    Log.i(TAG, "remote device name retrieved: " + mesto.get(0));

                    for (final PeerNotifications l : mPeerNotifications) {
                        l.onAdded(device.getIdentity().getUdn(), mesto);
                    }
                }

                @Override
                public void failure(ActionInvocation invocation,
                                    UpnpResponse operation,
                                    String defaultMsg) {
                    System.err.println(defaultMsg);
                }
            };

            mUpnpService.getControlPoint().execute(setTargetCallback);
        }
    };


    public final void startUpnp() {
        final WifiManager wifiManager =
                (WifiManager) mApplicationContext.getSystemService(Context.WIFI_SERVICE);
        mUpnpService = new UpnpServiceImpl(createConfiguration(wifiManager), mRegistryListener);

        try {
            final LocalDevice device = createDevice();
            mUpnpService.getRegistry().addDevice(device);

            final LocalService<MestoPeer> service = device.getServices()[0];
            final SubscriptionCallback cb = new SubscriptionCallback(service, 180) {
                @Override
                protected void failed(final GENASubscription subscription, final UpnpResponse responseStatus, final Exception exception, final String defaultMsg) {
                    Log.i(TAG, "scb failed");
                }

                @Override
                protected void established(final GENASubscription subscription) {
                    Log.i(TAG, "scb established");
                }

                @Override
                protected void ended(final GENASubscription subscription, final CancelReason reason, final UpnpResponse responseStatus) {
                    Log.i(TAG, "scb ended");
                }

                @Override
                protected void eventReceived(final GENASubscription subscription) {
                    Log.i(TAG, "scb eventReceived");
                }

                @Override
                protected void eventsMissed(final GENASubscription subscription, final int numberOfMissedEvents) {
                    Log.i(TAG, "scb eventMissed");
                }
            };
            mUpnpService.getControlPoint().execute(cb);

        } catch (Exception e) {
            Log.e(TAG, "addDevice failed", e);
        }

        /*final PortMapping desiredMapping =
                new PortMapping(
                        55333,
                        "192.168.0.123",
                        PortMapping.Protocol.TCP,
                        "My Port Mapping");
        mUpnpService.getRegistry().addListener(new PortMappingListener(desiredMapping));*/

        final UDAServiceType udaType = new UDAServiceType(MestoPeer.ID);
        mUpnpService.getControlPoint().search(new UDAServiceTypeHeader(udaType));
    }

    void setPin(final UDN udn, final String pin) {
        final Device device = mUpnpService.getRegistry().getDevice(udn, false);
        if (null != device) {
            final Service service = device.findService(new UDAServiceId(MestoPeer.ID));
            final Action action = service.getAction("SetPin");
            final ActionInvocation invocation = new ActionInvocation(action);
            invocation.setInput("Pin", pin);

            final ActionCallback callback = new ActionCallback(invocation) {
                @Override
                public void success(ActionInvocation invocation) {
                    Log.i(TAG, "remote device pin set: " + udn);
                }

                @Override
                public void failure(ActionInvocation invocation,
                                    UpnpResponse operation,
                                    String defaultMsg) {
                    System.err.println(defaultMsg);
                }
            };

            mUpnpService.getControlPoint().execute(callback);
        }
    }

    final LocalDevice createDevice() throws IOException, ValidationException {

        final DeviceType type = new UDADeviceType(MestoPeer.ID, 1);
        final DeviceDetails details = new DeviceDetails("Mesto Mobile Client",
                new ManufacturerDetails("Mesto"), new ModelDetails("Mesto 1",
                "Mesto location service", "v1")
        );

        final LocalService<MestoPeer> service = new AnnotationLocalServiceBinder().read(MestoPeer.class);
        service.setManager(new DefaultServiceManager<MestoPeer>(service, MestoPeer.class));

        return new LocalDevice(mIdentity, type, details, service);
    }

    private AndroidUpnpServiceConfiguration createConfiguration(WifiManager wifiManager) {
        return new AndroidUpnpServiceConfiguration(wifiManager) {
            @Override
            public ServiceType[] getExclusiveServiceTypes() {
                return new ServiceType[]{new UDAServiceType(MestoPeer.ID)};
            }
        };
    }

    private final void enableLogging() {
        LoggingUtil.resetRootHandler(new FixedAndroidHandler());

        // Enable this for debug logging:
        Logger.getLogger("org.teleal.cling.transport.Router").setLevel(Level.FINEST);

        // UDP communication
        Logger.getLogger("org.teleal.cling.transport.spi.DatagramIO").setLevel(Level.FINEST);
        Logger.getLogger("org.teleal.cling.transport.spi.MulticastReceiver").setLevel(Level.FINEST);

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

        Logger.getLogger("org.teleal.cling").setLevel(Level.FINEST);
    }

}
