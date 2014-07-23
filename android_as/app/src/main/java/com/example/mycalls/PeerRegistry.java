package com.example.mycalls;

import android.util.Log;

/**
 * remember peer udn and endpoints here; implement revoke; sqlite
 * <p/>
 * needs cookie but will udn instead first
 */
public class PeerRegistry {

    private PeerRegistry() {
    }

    private final static class Holder {
        static PeerRegistry INSTANCE = new PeerRegistry();
    }

    public enum EndpointTypes {
        EndpointPublic,
        EndpointPrivate
    }

    public static class Endpoint {
        public EndpointTypes type;
        public String ssid;//if applicable
        public String uri;
        public short[] portRange;
    }

    public static class PeerDescriptor {
        public String udn;
        public Endpoint[] endPoints;
    }

    /**
     * When a new peer is discovered, allow adding it to the registry
     *
     * @param peer
     * @return true if really added
     */
    public boolean addPeer(PeerDescriptor peer) {
        return true;
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
     * @param ssid
     * @return
     */
    public String[] getEndpoints(String ssid) {
        return null;
    }

    public static PeerRegistry get() {
        return Holder.INSTANCE;
    }
}
