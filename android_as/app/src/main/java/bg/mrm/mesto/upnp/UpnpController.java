package bg.mrm.mesto.upnp;

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
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.types.DeviceType;
import org.teleal.cling.model.types.ServiceType;
import org.teleal.cling.model.types.UDADeviceType;
import org.teleal.cling.model.types.UDAServiceId;
import org.teleal.cling.model.types.UDAServiceType;
import org.teleal.cling.model.types.UDN;
import org.teleal.cling.model.types.UnsignedIntegerTwoBytes;
import org.teleal.cling.model.types.csv.CSVString;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.registry.RegistryListener;
import org.teleal.cling.support.igd.callback.GetExternalIP;
import org.teleal.cling.support.igd.callback.PortMappingAdd;
import org.teleal.cling.support.model.PortMapping;
import org.teleal.common.logging.LoggingUtil;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import bg.mrm.mesto.FixedAndroidHandler;
import bg.mrm.mesto.PeerRegistry;
import bg.mrm.mesto.Utilities;

import static bg.mrm.mesto.Globals.TAG;

public class UpnpController {
    private final DeviceIdentity mIdentity;
    private UpnpService mUpnpService;
    private Context mApplicationContext;

    public UpnpController(final Context context, ExecutorService executor) {
        //not reliable, need to replace with my uuid generated during first launch
        mIdentity = new DeviceIdentity(UDN.uniqueSystemIdentifier("Mesto"));

        mApplicationContext = context.getApplicationContext();
        UpnpPeerRegistry.get().initialize(
                mApplicationContext, executor, mIdentity.getUdn().getIdentifierString());
    }

    public final void up() {
        //enableLogging();
        registerDefaultEndpoint();

        //actually throws a NetworkOnMain but will keep it intentionally
        //so cling uses the mac address instead of localhost for udn
        //generation
        startUpnp();
    }

    private void registerDefaultEndpoint() {
        final WifiManager wifi = (WifiManager) mApplicationContext.getSystemService(Context.WIFI_SERVICE);

        PeerRegistry.Endpoint[] e=new PeerRegistry.Endpoint[1];
        e[0] = new PeerRegistry.Endpoint(
                wifi.getConnectionInfo().getSSID(),
                Utilities.getIPAddress(true),
                new int[]{50001, 50001},
                false
        );

        UpnpPeerRegistry.get().savePeer(mIdentity.getUdn().getIdentifierString(), e);
    }

    public final void down() {
        if (null != mUpnpService) {
            mUpnpService.shutdown();
        }
    }

    public final String getOwnUdn() {
        return mIdentity.getUdn().getIdentifierString();
    }

    private final RegistryListener mClingRegistryListener = new RegistryListener() {
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
            RemoteService[] services = device.getServices();
            for (RemoteService rs : services) {
                Log.i(TAG, "upnp service: " + rs);
                registerPortMapping(rs);
            }
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
            UpnpPeerRegistry.get().onPeerGone(device.getIdentity().getUdn());//@todo
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

                    PeerRegistry.Endpoint[] endpoints = UpnpUtilities.importEndpoints(mesto, 1);
                    UpnpPeerRegistry.get().onPeerDiscovered(device.getIdentity().getUdn(), endpoints);
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

    private void registerPortMapping(final RemoteService rs) {
        if (rs.getServiceType().equals(new ServiceType("schemas-upnp-org", "WANIPConnection"))) {
            final PortMapping desiredMapping =
                    new PortMapping(
                            50001,
                            Utilities.getIPAddress(true),
                            PortMapping.Protocol.TCP,
                            "Mesto peer port mapping"
                    );
            desiredMapping.setExternalPort(new UnsignedIntegerTwoBytes(50051));

            mUpnpService.getControlPoint().execute(new PortMappingAdd(rs, desiredMapping) {
                @Override
                public void success(final ActionInvocation invocation) {
                    Log.i(TAG, "port mapping succeeded");
                    PeerRegistry.Endpoint.Builder b = new PeerRegistry.Endpoint.Builder();
                    b.setPortRange(new int[]{50051, 50051});
                    getExternalIpAddress(rs, b);
                }

                @Override
                public void failure(final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
                    Log.i(TAG, "port mapping failed: " + defaultMsg);
                }
            });
        }
    }

    private void getExternalIpAddress(final RemoteService service, final PeerRegistry.Endpoint.Builder b) {
        mUpnpService.getControlPoint().execute(
                new GetExternalIP(service) {

                    @Override
                    protected void success(String externalIPAddress) {
                        Log.i(TAG, "getexternalip succeeded");

                        b.setUri(externalIPAddress);
                        WifiManager wifi = (WifiManager) mApplicationContext.getSystemService(Context.WIFI_SERVICE);
                        b.setSsid(wifi.getConnectionInfo().getSSID());
                        b.setExternal(true);

                        final String udn = service.getDevice().getIdentity().getUdn().getIdentifierString();
                        PeerRegistry.Endpoint[] ee = new PeerRegistry.Endpoint[1];
                        ee[0]=b.make();//overwrites other endpoints, needs api changes
                        UpnpPeerRegistry.get().savePeer(udn, ee);
                    }

                    @Override
                    public void failure(ActionInvocation invocation,
                                        UpnpResponse operation,
                                        String defaultMsg) {
                        Log.i(TAG, "getexternalip failed: " + defaultMsg);
                    }
                }
        );
    }

    public final void startUpnp() {
        final WifiManager wifiManager =
                (WifiManager) mApplicationContext.getSystemService(Context.WIFI_SERVICE);
        mUpnpService = new UpnpServiceImpl(createConfiguration(wifiManager), mClingRegistryListener);

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

        final UDAServiceType udaType = new UDAServiceType(MestoPeer.ID);
        mUpnpService.getControlPoint().search(new UDAServiceTypeHeader(udaType));

        mUpnpService.getControlPoint().search(new UDAServiceTypeHeader(new UDAServiceType("WANIPConnection")));
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

    public PeerRegistry getRegistry() {
        return UpnpPeerRegistry.get();
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
