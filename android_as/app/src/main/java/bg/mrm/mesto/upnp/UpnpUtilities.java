package bg.mrm.mesto.upnp;

import java.util.ArrayList;
import java.util.List;

import bg.mrm.mesto.PeerRegistry;

final class UpnpUtilities {
    private static final int COUNT_ENDPOINT_FIELDS = 5;

    static List<String> exportEndpoints(final String udn) {
        List<String> result = new ArrayList<String>();

        final PeerRegistry.PeerDescriptor pd = UpnpPeerRegistry.get().findPeer(udn);
        for (PeerRegistry.Endpoint e : pd.endPoints) {
            result.add(e.uri);
            result.add(Integer.toString(e.portRange[0]));
            result.add(Integer.toString(e.portRange[1]));
            result.add(e.ssid);
            result.add(Boolean.toString(e.external));
        }

        return result;
    }

    static PeerRegistry.Endpoint[] importEndpoints(List<String> values, int offset) {
        final int size = values.size();
        if (0 == size) {
            return null;
        }

        int endpointsCount = (values.size() - offset) / COUNT_ENDPOINT_FIELDS;
        PeerRegistry.Endpoint[] result = new PeerRegistry.Endpoint[endpointsCount];
        for (int i = offset; i < size; ++i) {
            PeerRegistry.Endpoint.Builder b = new PeerRegistry.Endpoint.Builder();

            b.setUri(values.get(i++));

            int[] portRange = new int[2];
            portRange[0] = Integer.parseInt(values.get(i++));
            portRange[1] = Integer.parseInt(values.get(i++));
            b.setPortRange(portRange);

            b.setSsid(values.get(i++));
            b.setExternal(Boolean.parseBoolean(values.get(i++)));

            result[--endpointsCount] = b.make();
        }

        return result;
    }

    private UpnpUtilities() {
    }
}
