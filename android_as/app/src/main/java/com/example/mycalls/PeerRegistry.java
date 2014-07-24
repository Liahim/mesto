package com.example.mycalls;

import java.util.ArrayList;
import java.util.List;

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

    public static class Endpoint {
        public String ssid;
        public String uri;
        public int[] portRange;
        public boolean external;
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
     *
     * @param ssid
     * @return
     */
    public String[] getEndpoints(String ssid) {
        return null;
    }

    public static PeerRegistry get() {
        return Holder.INSTANCE;
    }

    private List<Endpoint> mEndpoints = new ArrayList<Endpoint>();

    public void addOwnEndpoint(Endpoint endpoint) {
        boolean updated = false;
        for (Endpoint e : mEndpoints) {
            if (null != e.ssid && e.ssid.equalsIgnoreCase(endpoint.ssid)) {
                e.uri = endpoint.uri;
                e.portRange = endpoint.portRange;
                updated = true;
                break;
            }
        }

        if (!updated) {
            mEndpoints.add(endpoint);
        }
    }

    public List<String> exportOwnEndpoints() {
        List<String> result = new ArrayList<String>();

        for (Endpoint e : mEndpoints) {
            result.add(e.uri);
            result.add(Integer.toString(e.portRange[0]));
            result.add(Integer.toString(e.portRange[1]));
            result.add(e.ssid);
            result.add(Boolean.toString(e.external));
        }

        return result;
    }

}

