package bg.mrm.mesto;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Log;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class Utilities {

    private Utilities() {
    }

    public static String getIPAddress(boolean useIPv4) {
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

    public static String getWlanName(final Context c) {
        WifiManager wifiMgr = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        String name = wifiInfo.getSSID();
        return name;
    }

    static BufferedOutputStream mLogger;
    static Context mAppCtx;

    static boolean openLogger(Context ctx) {
        mAppCtx = ctx.getApplicationContext();

        boolean result;
        try {
            final FileOutputStream fos = mAppCtx.openFileOutput("log.txt", Context.MODE_APPEND);
            mLogger = new BufferedOutputStream(fos);
            result = true;
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
            result = false;
        }
        return result;
    }

    static void closeLogger() {
        try {
            mLogger.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        mLogger = null;
    }

    static void exportLog() {
        closeLogger();

        File source = mAppCtx.getFileStreamPath("log.txt");
        final File target = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "log.txt");
        target.delete();

        try {
            FileInputStream fis = new FileInputStream(source);
            FileOutputStream fos = new FileOutputStream(target);
            final byte[] bytes = new byte[4096];
            int read;
            while ((read = fis.read(bytes)) > 0) {
                fos.write(bytes, 0, read);
            }
            fos.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        source.delete();

        openLogger(mAppCtx);
    }

    static void log(final Exception exc) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        exc.printStackTrace(pw);
        pw.close();

        try {
            final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
            final String dateTime = df.format(new Date());
            mLogger.write(dateTime.getBytes("UTF-8"));
            mLogger.write(' ');
            mLogger.write(sw.toString().getBytes("UTF-8"));
            mLogger.write('\n');
        } catch (final IOException e) {
            e.printStackTrace();
        }
        Log.d("Mesto", "exception: ", exc);
    }

    static void log(final String s) {
        try {
            final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
            final String dateTime = df.format(new Date());
            mLogger.write(dateTime.getBytes("UTF-8"));
            mLogger.write(' ');
            mLogger.write(s.getBytes("UTF-8"));
            mLogger.write('\n');
        } catch (final IOException e) {
            e.printStackTrace();
        }
        Log.d("Mesto", s);
    }
}
