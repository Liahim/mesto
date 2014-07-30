package bg.mrm.mesto;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


public abstract class PeerRegistry {

    public static final String OWN_MANUAL_ENDPOINT_ID = "FF808181478958FB014789590CD90001";
    public static final String OWN_EXTERNAL_ENDPOINT_ID = "FF808181478958FB01478958FB2B0000";

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
    private Notifications mListener;
    private Future<?> mInitFuture;
    private final List<PeerDescriptor> mPeers = new ArrayList<PeerDescriptor>();


    public void setListener(Notifications l) {
        waitForInit();

        mListener = l;
        if (null != mListener) {
            for (PeerDescriptor pd : mPeers) {
                if (!mOwnId.equalsIgnoreCase(pd.udn)) {
                    mListener.onPeerDiscovered(pd.udn, pd.title, pd.paired);
                }
            }
        }
    }

    public boolean trackPeer(String udn, String title, Collection<Endpoint> endpoints) {
        Endpoint[] ee = new Endpoint[endpoints.size()];
        ee = endpoints.toArray(ee);

        return trackPeer(udn, title, ee);
    }

    public boolean trackPeer(String udn, String title, Endpoint[] endpoints) {
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
            mDatabase.savePeer(pd);
        }

        if (null != mListener && !mOwnId.equalsIgnoreCase(udn)) {
            mListener.onPeerDiscovered(udn, title, pd.paired);
        }

        return update;
    }

    public void untrackPeer(String id) {
        Pair<Integer, PeerDescriptor> pair = findPeerImpl(id);
        mPeers.remove(pair.first);
        if (null != mListener) {
            mListener.onPeerGone(pair.second.udn);
        }
    }

    public void pairPeer(String udn) {
        PeerDescriptor pd = findPeer(udn);
        if (null != pd) {
            if (!pd.paired) {
                pd.paired = true;
            }

            mDatabase.savePeer(pd);
        }
    }

    public PeerDescriptor findPeer(String udn) {
        Pair<Integer, PeerDescriptor> pair = findPeerImpl(udn);
        return null != pair ? pair.second : null;
    }

    private Pair<Integer, PeerDescriptor> findPeerImpl(String udn) {
        for (int i = 0; i < mPeers.size(); ++i) {
            PeerDescriptor pd = mPeers.get(i);
            if (pd.udn.equalsIgnoreCase(udn)) {
                return new Pair<Integer, PeerDescriptor>(i, pd);
            }
        }
        return null;
    }

    public boolean unpairPeer(String udn) {
        boolean removed = false;

        PeerDescriptor pd = findPeer(udn);
        if (null != pd) {
            pd.paired = false;
            mDatabase.removePeer(udn);
            removed = true;
        }

        return removed;
    }

    /**
     * Retrieve endpoints that might be reachable for updates at
     * this moment and in this context.
     *
     * @return
     */
    public Collection<Endpoint> getUpdateEndpoints() {
        waitForInit();

        List<Endpoint> ee = new ArrayList<Endpoint>();
        for (PeerDescriptor pd : mPeers) {
            if (includeFilter(pd)) {
                ee.addAll(Arrays.asList(pd.endPoints));
            }
        }

        return Collections.unmodifiableCollection(ee);
    }

    private void waitForInit() {
        if (!mInitFuture.isDone()) {
            Log.i(Globals.TAG, "have to wait for the db to be initialized");
            try {
                mInitFuture.get();
            } catch (Exception e) {
                Log.e(Globals.TAG, "error while initializing the db", e);
            }
        }
    }

    protected boolean includeFilter(PeerDescriptor pd) {
        return pd.paired
                && !mOwnId.equalsIgnoreCase(pd.udn)
                && !PeerRegistry.OWN_EXTERNAL_ENDPOINT_ID.equalsIgnoreCase(pd.udn);
    }

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
        mInitFuture = executor.submit(r);
    }

    public Endpoint[] getEndpoints(String peerId) {
        Endpoint[] result = null;
        final PeerRegistry.PeerDescriptor pd = findPeer(peerId);
        if (null != pd) {
            result = pd.endPoints;  //switch to unmodif coll instead of arr?
        }
        return result;
    }

    protected PeerRegistry(String ownId) {
        mOwnId = ownId;
    }

    public abstract List<String> exportOwnEndpoints();
}

