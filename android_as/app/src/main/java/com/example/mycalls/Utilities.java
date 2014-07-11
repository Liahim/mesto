package com.example.mycalls;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Pair;

import org.apache.http.conn.util.InetAddressUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Utilities {

    public static final String PREFIX_PEER = "peer_";

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


    private final static String KEY_SERVER_URIS = "server_uris";
    private final static String KEY_SERVER_HISTORY = "server_history";

    static final void saveServerInfo(
            final Context ctx, final Set<String> uris, final Set<String> history) {
        final SharedPreferences sp
                = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sp.edit();
        editor.putStringSet(KEY_SERVER_URIS, uris);
        if (null != history) {
            editor.putStringSet(KEY_SERVER_HISTORY, history);
        }
        editor.apply();
    }

    static final Set<String> loadServerUris(final Context ctx) {
        final SharedPreferences sp
                = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        return sp.getStringSet(KEY_SERVER_URIS, null);
    }

    static final Pair<Set<String>, Set<String>> loadServerInfo(final Context ctx) {
        final SharedPreferences sp
                = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        final Set<String> uris = sp.getStringSet(KEY_SERVER_URIS, null);

        final Set<String> serverHistory = new HashSet<String>();
        serverHistory.addAll(sp.getStringSet(KEY_SERVER_HISTORY, serverHistory));

        final Pair<Set<String>, Set<String>> result = new Pair<Set<String>, Set<String>>(uris, serverHistory);
        return result;
    }

    static final void savePeerInfo(
            final Context ctx, final String udn, final Set<String> uris) {
        final SharedPreferences sp
                = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sp.edit();

        editor.putStringSet(PREFIX_PEER + udn, uris);
        editor.apply();
    }

    static final Set<String> loadPeerInfo(final Context ctx) {
        final SharedPreferences sp
                = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);

        final Set<String> results = new HashSet<String>();
        final Map<String, ?> all = sp.getAll();
        for (final String s : all.keySet()) {
            int idx = s.indexOf(PREFIX_PEER);
            if (-1 != idx) {
                final Set<String> uris = (Set<String>) all.get(s);
                results.addAll(uris);
            }
        }

        return results;
    }

}
