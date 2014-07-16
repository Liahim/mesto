package com.example.mycalls;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.apache.http.conn.util.InetAddressUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Utilities {

    public static final String KEY_PREFIX_PEER = "peer_";
    public static final String KEY_PREFIX_TITLE = "title_";
    public static final String UDN_USER_SPECIFIED = "user_specified";

    private Utilities() {
    }

    static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                                return delim < 0 ? sAddr : sAddr.substring(0, delim);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        } // for now eat exceptions
        return "";
    }

    static String getWlanName(final Context c) {
        WifiManager wifiMgr = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        String name = wifiInfo.getSSID();
        return name;
    }

    static final void savePeerInfo(
            final Context ctx, final String udn, final Set<String> uris, final String title) {
        final SharedPreferences sp = getSharedPreferences(ctx);
        final SharedPreferences.Editor editor = sp.edit();

        editor.putStringSet(KEY_PREFIX_PEER + udn, uris);
        editor.putString(KEY_PREFIX_TITLE + udn, title);

        editor.apply();
    }

    static final Set<String> loadEndPoints(final Context ctx) {
        final SharedPreferences sp = getSharedPreferences(ctx);

        final Set<String> results = new HashSet<String>();
        final Map<String, ?> all = sp.getAll();
        for (final String s : all.keySet()) {
            int idx = s.indexOf(KEY_PREFIX_PEER);
            if (-1 != idx) {
                final Set<String> uris = (Set<String>) all.get(s);
                results.addAll(uris);
            }
        }

        return results;
    }

    private static SharedPreferences getSharedPreferences(final Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static class PeerInfo {
        public String title;
        public Set<String> uris;
    }

    public static final PeerInfo loadPeerInfoFull(final Context ctx, final String udn) {
        final SharedPreferences sp = getSharedPreferences(ctx);
        final PeerInfo pi = new PeerInfo();
        pi.uris = sp.getStringSet(KEY_PREFIX_PEER + udn, Collections.<String>emptySet());
        return pi;
    }

    static final Map<String, PeerInfo> loadAllPeersInfo(final Context ctx) {
        final SharedPreferences sp = getSharedPreferences(ctx);

        final Map<String, PeerInfo> result = new HashMap<String, PeerInfo>();

        final Map<String, ?> all = sp.getAll();
        for (final String s : all.keySet()) {
            int idx = s.indexOf(KEY_PREFIX_PEER);
            if (-1 != idx) {
                final Set<String> uris = (Set<String>) all.get(s);

                final String udn = s.substring(KEY_PREFIX_PEER.length());
                final PeerInfo pi = getPeerInfo(result, udn);
                pi.uris = uris;
                continue;
            }

            idx = s.indexOf(KEY_PREFIX_TITLE);
            if (-1 != idx) {
                final String title = (String) all.get(s);

                final String udn = s.substring(KEY_PREFIX_TITLE.length());
                final PeerInfo pi = getPeerInfo(result, udn);
                pi.title = title;
                continue;
            }
        }

        return result;
    }

    private static PeerInfo getPeerInfo(final Map<String, PeerInfo> result, final String udn) {
        PeerInfo pi = result.get(udn);
        if (null == pi) {
            pi = new PeerInfo();
            result.put(udn, pi);
        }
        return pi;
    }
}
