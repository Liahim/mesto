package com.example.mycalls;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.android.AndroidUpnpServiceConfiguration;
import org.teleal.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.teleal.cling.model.DefaultServiceManager;
import org.teleal.cling.model.ValidationException;
import org.teleal.cling.model.message.header.UDAServiceTypeHeader;
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

import java.io.IOException;
import java.util.logging.Level;

public class UpnpController {
    private final static String TAG = "MestoUpnp";

    private UpnpService mUpnpService;
    private Context mApplicationContext;

    final void initialize(final Context context) {
        mApplicationContext = context.getApplicationContext();
        enableLogging();
        startUpnp();
    }

    private final void enableLogging() {
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
    }

    final void shutdown() {
        if (null != mUpnpService) {
            mUpnpService.shutdown();
        }
    }

    public final class Binder extends android.os.Binder {
        public UpnpController getService() {
            return UpnpController.this;
        }
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


    public final void startUpnp() {
        final WifiManager wifiManager =
                (WifiManager) mApplicationContext.getSystemService(Context.WIFI_SERVICE);
        mUpnpService = new UpnpServiceImpl(createConfiguration(wifiManager), mRegistryListener);

        try {
            mUpnpService.getRegistry().addDevice(createDevice());
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

        UDAServiceType udaType = new UDAServiceType("SwitchPower");
        mUpnpService.getControlPoint().search(
                new UDAServiceTypeHeader(udaType)
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

    private AndroidUpnpServiceConfiguration createConfiguration(WifiManager wifiManager) {
        return new AndroidUpnpServiceConfiguration(wifiManager) {
            @Override
            public ServiceType[] getExclusiveServiceTypes() {
                Log.i(TAG, "requesting only SwitchPower service support");
                return new ServiceType[]{
                        new UDAServiceType("SwitchPower")
                };
            }

        };
    }
}
