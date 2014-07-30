package bg.mrm.mesto.upnp;

import android.content.Context;

import org.teleal.cling.model.types.UDN;

import java.util.List;
import java.util.concurrent.ExecutorService;

import bg.mrm.mesto.PeerRegistry;

class UpnpPeerRegistry extends PeerRegistry {
    void onPeerDiscovered(final UDN udn, String title, final Endpoint[] endpoints) {
        trackPeer(udn.getIdentifierString(), title, endpoints);
    }

    void onPeerGone(final String id) {
        untrackPeer(id);
    }

    void initialize(final Context ctx, final ExecutorService executor, String udn) {
        mOwnId = udn;
        super.initialize(ctx, executor);
    }

    private UpnpPeerRegistry() {
        super(null);
    }

    static UpnpPeerRegistry get() {
        return Holder.INSTANCE;
    }

    private final static class Holder {
        static UpnpPeerRegistry INSTANCE = new UpnpPeerRegistry();
    }

    @Override
    public List<String> exportOwnEndpoints() {
        return UpnpUtilities.exportEndpoints(mOwnId);
    }
}