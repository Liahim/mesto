package bg.mrm.mesto.upnp;

import android.content.Context;

import org.teleal.cling.model.types.UDN;

import java.util.List;
import java.util.concurrent.ExecutorService;

import bg.mrm.mesto.PeerRegistry;

class UpnpPeerRegistry extends PeerRegistry {
    private String mOwnUdn;

    void onPeerDiscovered(final UDN udn, final Endpoint[] endpoints) {
        savePeer(udn.getIdentifierString(), endpoints);
    }

    void onPeerGone(final UDN udn) {
    }

    public List<String> exportOwnEndpoints() {
        return UpnpUtilities.exportEndpoints(mOwnUdn);
    }

    void initialize(final Context ctx, final ExecutorService executor, String udn) {
        mOwnUdn = udn;
        super.initialize(ctx, executor);
    }

    private UpnpPeerRegistry() {
    }

    static UpnpPeerRegistry get() {
        return Holder.INSTANCE;
    }

    private final static class Holder {
        static UpnpPeerRegistry INSTANCE = new UpnpPeerRegistry();
    }
}
