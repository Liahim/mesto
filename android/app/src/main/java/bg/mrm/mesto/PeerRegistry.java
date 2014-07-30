package bg.mrm.mesto;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * remember peer udn and endpoints here; implement revoke; sqlite
 * <p/>
 * needs cookie but will udn instead first
 */
public abstract class PeerRegistry {

    public static final String MANUAL_ENDPOINT_ID = "manually_entered";

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
            private boolean fixed;

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

            public Builder setFixed(boolean fixed) {
                this.fixed = fixed;
                return this;
            }

            public Endpoint make() {
                return new Endpoint(ssid, uri, portRange, external);
            }
        }
    }

    public static class PeerDescriptor {
        public String udn;
        public String title;
        public Endpoint[] endPoints;
        public boolean paired;
    }

    interface Notifications {
        void onPeerDiscovered(String udn, String title, boolean known);

        void onPeerGone(String udn);
    }

    protected /*final*/ String mOwnId;
    private Database mDatabase;


    protected PeerRegistry(String ownId) {
        mOwnId = ownId;
    }

    public String getOwnId() {
        return mOwnId;
    }

    private Notifications mListener;

    public void setListener(Notifications l) {
        mListener = l;
    }

    private final List<PeerDescriptor> mPeers = new ArrayList<PeerDescriptor>();


    public boolean savePeer(String udn, String title, Collection<Endpoint> endpoints) {
        Endpoint[] ee = new Endpoint[endpoints.size()];
        ee = endpoints.toArray(ee);

        return savePeer(udn, title, ee);
    }

    //save peer; return true if it was actually an update
    public boolean savePeer(String udn, String title, Endpoint[] endpoints) {
        PeerDescriptor pd = findPeer(udn);
        boolean update = null != pd;
        if (!update) {
            pd = new PeerDescriptor();
            pd.endPoints = endpoints;
            pd.udn = udn;
            pd.title = title;

            mPeers.add(pd);
        } else {
            pd.endPoints = endpoints;
        }

        if (null != mListener) {
            mListener.onPeerDiscovered(udn, title, pd.paired);
        }

        return update;
    }

    public void commitPeer(String udn) {
        PeerDescriptor pd = findPeer(udn);
        if (null != pd) {
            if (!pd.paired) {
                pd.paired = true;
            }

            mDatabase.insertInPeers(pd);
        }
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
        for (PeerDescriptor pd : mPeers) {
            if (pd.udn.equalsIgnoreCase(udn)) {
                return pd;
            }
        }
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
     * Retrieve endpoints that might be reachable for updates at
     * this moment and in this context.
     *
     * @return
     */
    public Collection<Endpoint> getUpdateEndpoints() {
        if (!mFuture.isDone()) {
            Log.i(Globals.TAG, "have to wait for the db to be initialized");
            try {
                mFuture.get();
            } catch (Exception e) {
                Log.e(Globals.TAG, "error while initializing the db", e);
            }
        }
        List<Endpoint> ee = new ArrayList<Endpoint>();
        for (PeerDescriptor pd : mPeers) {
            if (filter(pd)) {
                ee.addAll(Arrays.asList(pd.endPoints));
            }
        }

        return Collections.unmodifiableCollection(ee);
    }

    protected boolean filter(PeerDescriptor pd) {
        return true;
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

    private Future<?> mFuture;

    public void initialize(final Context ctx, final ExecutorService executor) {
        mDatabase = new Database(ctx.getApplicationContext());

        Runnable r = new Runnable() {
            @Override
            public void run() {
                mDatabase.getAllPeers(new Database.Callback() {
                    @Override
                    public void onReadPeer(final PeerDescriptor pd) {
                        mPeers.add(pd);
                    }
                });
            }
        };
        mFuture = executor.submit(r);
    }

    public Endpoint[] getEndpoints(String peerId) {
        Endpoint[] result = null;
        final PeerRegistry.PeerDescriptor pd = findPeer(peerId);
        if (null != pd) {
            result = pd.endPoints;  //switch to unmodif coll instead of arr?
        }
        return result;
    }

    public abstract List<String> exportOwnEndpoints();
}

