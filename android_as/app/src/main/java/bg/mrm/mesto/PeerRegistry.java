package bg.mrm.mesto;

import android.content.Context;

import org.teleal.cling.model.types.UDN;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * remember peer udn and endpoints here; implement revoke; sqlite
 * <p/>
 * needs cookie but will udn instead first
 */
public abstract class PeerRegistry {

    public static class Endpoint {
        public final String ssid;
        public final String uri;
        public final int[] portRange;
        public final boolean external;

        public Endpoint(final String ssid, final String uri,
                        final int[] portRange, final boolean external) {
            this.ssid = ssid;
            this.uri = uri;
            this.portRange = portRange;
            this.external = external;
        }

        public static class Builder {
            private String ssid;
            private String uri;
            private int[] portRange;
            private boolean external;

            public Builder setSsid(String ssid) {
                this.ssid = ssid;
                return this;
            }

            public Builder setUri(String uri) {
                this.uri = uri;
                return this;
            }

            public Builder setPortRange(int[] portRange) {
                this.portRange = portRange;
                return this;
            }

            public Builder setExternal(boolean external) {
                this.external = external;
                return this;
            }

            public Endpoint make() {
                return new Endpoint(ssid, uri, portRange, external);
            }
        }
    }

    public static class PeerDescriptor {
        public String udn;
        public Endpoint[] endPoints;
        public boolean paired;
    }

    interface Notifications {
        void onPeerDiscovered(UDN udn, String title, boolean known);

        void onPeerGone(UDN udn);
    }

    private Notifications mListener;

    public void setListener(Notifications l) {
        mListener = l;
    }

    List<PeerDescriptor> mPeers;


    //save peer; return true if it was actually an update
    public boolean savePeer(String udn, Endpoint[] endpoints) {

        PeerDescriptor pd = findPeer(udn);
        if (null == pd) {
            pd = new PeerDescriptor();
            pd.endPoints = endpoints;
            pd.udn = udn;

            mPeers.add(pd);
        } else {
            pd.endPoints = endpoints;
        }

        return false;
    }

    public boolean commitPeer(String udn) {

        PeerDescriptor pd = findPeer(udn);
        if (null != pd) {
            Database.insertInPeersAsync(mDbHelper, udn, null, null);
            pd.paired = true;
        } else {
            pd = Database.updatePeer(mDbHelper, udn, -1);   //@todo
        }
        return false;
    }


    /**
     * When a new peer is discovered determine whether it has been registered
     * which could make it possible to directly reach this peer and also
     * retrieve updated peer information
     *
     * @param udn
     * @return
     */
    public PeerDescriptor findPeer(String udn) {
        return null;
    }

    /**
     * User-initiated removal
     *
     * @param udn
     */
    public void removePeer(String udn) {
    }

    //internally track endpoint reachability

    /**
     * Allow updates for previously registered peers
     *
     * @param peer
     * @return
     */
    public boolean updatePeer(PeerDescriptor peer) {
        return true;
    }

    /**
     * Given the current context (ssid) return endpoints that are deemed to
     * have a chance of being reachable
     *
     * @param ssid
     * @return
     */
    public String[] getEndpoints(String ssid) {
        return null;
    }

    /**
     * Retrieve endpoints that might be reachable for updates at
     * this moment and in this context.
     * @return
     */
    public Endpoint[] getUpdateEndpoints() {
        return null;
    }

//    private List<Endpoint> mEndpoints = new ArrayList<Endpoint>();
//
//    public void addOwnEndpoint(Endpoint endpoint) {
//        boolean updated = false;
//        for (Endpoint e : mEndpoints) {
//            if (null != e.ssid && e.ssid.equalsIgnoreCase(endpoint.ssid)) {
//                e.uri = endpoint.uri;
//                e.portRange = endpoint.portRange;
//                updated = true;
//                break;
//            }
//        }
//
//        if (!updated) {
//            mEndpoints.add(endpoint);
//        }
//    }

    private Database.Helper mDbHelper;

    public void initialize(final Context ctx, final ExecutorService executor) {
        mDbHelper = new Database.Helper(ctx.getApplicationContext(), executor);
    }

}

