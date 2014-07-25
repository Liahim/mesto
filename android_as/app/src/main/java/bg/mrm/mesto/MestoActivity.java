package bg.mrm.mesto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.teleal.cling.model.types.UDN;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static bg.mrm.mesto.Globals.TAG;

public final class MestoActivity extends Activity implements PeerRegistry.Notifications {

    private MestoLocationService mService;
    private TextView mStatusText;
    private LinearLayout mPeersList;
    private String mPin;    //@todo replace
    private MenuItem mMenuItemToggleReporting;
    private MenuItem mMenuItemToggleUpnp;

    private String mStatusTextString;


    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public final void onServiceDisconnected(final ComponentName name) {
            Log.i(TAG, "onServiceDisconnected");
            mService = null;
        }

        @Override
        public final void onServiceConnected(final ComponentName name, final IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mService = ((MestoLocationService.Binder) service).getService();
            mService.addRunnableCallback(mRunnable);

            if (mService.isUpnpOn()) {
                mService.getPeerRegistry().setListener(MestoActivity.this);
            }

            showToggleReportingIfPossible();
            showToggleUpnpIfPossible();
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStatusText = (TextView) getWindow().findViewById(R.id.status_text);
        mStatusText.setMovementMethod(new ScrollingMovementMethod());
        mPeersList = (LinearLayout) getWindow().findViewById(R.id.ll_peers);

        final Intent intent = new Intent().setClassName(this, MestoLocationService.class.getName());
        startService(intent);
        bindService(intent, mServiceConnection, 0);

        mPin = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    @Override
    protected final void onResume() {
        super.onResume();
        if (null != mService) {
            mService.addRunnableCallback(mRunnable);
            prepareStatusText();
            displayStatusText();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != mService) {
            mService.removeRunnableCallback(mRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
    }

    private final SimpleDateFormat mFormat = new SimpleDateFormat("EEE, d MMM yyyy H:mm:ss z", Locale.US);
    private final Runnable mRunnable = new Runnable() {
        @Override
        public final void run() {
            prepareStatusText();
            runOnUiThread(new Runnable() {
                @Override
                public final void run() {
                    displayStatusText();
                }
            });
        }
    };

    private final synchronized void prepareStatusText() {
        try {
            final Collection<MestoLocationService.Event> events = mService.getLogEvents();
            final StringBuilder sb = new StringBuilder();
            for (final MestoLocationService.Event event : events) {
                final Date date = new Date(event.mTime);
                switch (event.mType) {
                    case Update:
                        sb.append("Updated at ");
                        break;
                    case Start:
                        sb.append("Started at ");
                        break;
                }
                sb.append(mFormat.format(date));
                sb.append('\n');
            }
            mStatusTextString = sb.toString();
        } catch (final Exception e) {
            Log.e(TAG, "error updating status text", e);
        }
    }

    private void displayStatusText() {
        mStatusText.setText(mStatusTextString);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.send_current).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (null != mService) {
                    mService.sendLocation();
                }
                return true;
            }
        });
        menu.findItem(R.id.action_settings).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                return onMenuSettings();
            }
        });

        mMenuItemToggleReporting = menu.findItem(R.id.menu_toggle_reporting);
        showToggleReportingIfPossible();

        mMenuItemToggleUpnp = menu.findItem(R.id.menu_toggle_upnp);
        showToggleUpnpIfPossible();

        menu.findItem(R.id.menu_dump_peers).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                onMenuDumpPeers();
                return true;
            }
        });
        return true;
    }

    private void showToggleUpnpIfPossible() {
        if (null != mMenuItemToggleUpnp) {
            final boolean serviceConnected = null != mService;
            mMenuItemToggleUpnp.setVisible(serviceConnected);
            if (serviceConnected) {
                establishToggleUpnpState(mMenuItemToggleUpnp, !mService.isUpnpOn(), false);
                mMenuItemToggleUpnp.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public final boolean onMenuItemClick(final MenuItem item) {
                        establishToggleUpnpState(item, mService.isUpnpOn(), true);
                        return true;
                    }
                });
            }
        }
    }

    private void showToggleReportingIfPossible() {
        if (null != mMenuItemToggleReporting) {
            final boolean serviceConnected = null != mService;
            mMenuItemToggleReporting.setVisible(serviceConnected);
            if (serviceConnected) {
                establishToggleReportingState(mMenuItemToggleReporting, !mService.isReporting(), false);
                mMenuItemToggleReporting.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public final boolean onMenuItemClick(final MenuItem item) {
                        establishToggleReportingState(item, mService.isReporting(), true);
                        return true;
                    }
                });
            }
        }
    }

    private final void establishToggleReportingState(final MenuItem item, final boolean showReporting,
                                                     final boolean toggle) {
        if (showReporting) {
            item.setTitle(R.string.start_reporting);
            if (toggle) {
                mService.stopReporting();
            }
            mStatusText.setText("Location reporting stopped");
        } else {
            item.setTitle(R.string.stop_reporting);
            if (toggle) {
                mService.startReporting();
            }
            prepareStatusText();
            displayStatusText();
        }
    }

    private final void establishToggleUpnpState(final MenuItem item, final boolean showUpnp,
                                                final boolean toggle) {
        if (showUpnp) {
            item.setTitle(R.string.start_upnp);
            if (toggle) {
                mService.stopUpnp();

                final Collection<View> views = new ArrayList<View>();
                for (int i = 0; i < mPeersList.getChildCount(); ++i) {
                    final View v = mPeersList.getChildAt(i);
                    if (null != v.getTag(R.id.tag_udn)) {
                        views.add(v);
                    }
                }
                for (final View v : views) {
                    mPeersList.removeView(v);
                }
            }
            mStatusText.setText("Upnp stopped");
        } else {
            item.setTitle(R.string.stop_upnp);
            if (toggle) {
                mService.startUpnp();
                mService.getPeerRegistry().setListener(MestoActivity.this);
            }
        }
    }

    private boolean onMenuSettings() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(MestoActivity.this);
        final LayoutInflater inflater = getLayoutInflater();

        final View view = inflater.inflate(R.layout.dialog_settings, null);
        final AutoCompleteTextView serverAddress
                = (AutoCompleteTextView) view.findViewById(R.id.serverAddress);

        final PeerRegistry.Endpoint[] ee=mService.getPeerRegistry().getUpdateEndpoints();
        for (final PeerRegistry.Endpoint e : ee) {
            serverAddress.setText(e.uri + '\n');
        }

        final TextView tv = (TextView) view.findViewById(R.id.localServer);
        final String ipAddress = Utilities.getIPAddress(true);
        final String wlanName = Utilities.getWlanName(this);
        tv.setText("Local server running at " + ipAddress + ":50001\nUsing wlan " + wlanName);

        builder.setView(view)
                .setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int id) {
                        final String server = serverAddress.getText().toString();
                        final Set<String> uris = new HashSet<String>();

                        if (!server.isEmpty()) {
                            final String[] tmp = server.split("\n");
                            Collections.addAll(uris, tmp);
                        }

                        //mService.getPeerRegistry().commitPeer();@todo save manually entered endpoints
                        mService.sendLocation();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null);

        builder.create().show();

        return true;
    }

    @Override
    public final void onPeerDiscovered(final UDN udn, final String title, final boolean known) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                final boolean known = -1 != findPeerView(udn);
                final String identifier = udn.getIdentifierString();

                Log.i(TAG, "showing peer " + title + "; " + identifier);

                final LayoutInflater inflater = getLayoutInflater();
                final TextView tv = (TextView) inflater.inflate(R.layout.list_element_peer, null);
                tv.setText(title);
                tv.setTag(R.id.tag_udn, udn);

                PeerRegistry.PeerDescriptor pd=mService.getPeerRegistry().findPeer(identifier);
                if (pd.paired) {
                    tv.setTextColor(Color.GREEN);
                }

                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(MestoActivity.this);
                        final LayoutInflater inflater = getLayoutInflater();

                        final View view = inflater.inflate(R.layout.dialog_pin, null);
                        builder.setView(view).setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int id) {
                                mService.getPeerRegistry().commitPeer(identifier);
                                tv.setTextColor(Color.GREEN);
                                Toast.makeText(MestoActivity.this, "Peer registered", Toast.LENGTH_LONG).show();
                            }
                        }).setNegativeButton(R.string.button_cancel, null);
                        builder.create().show();
                    }
                });
                mPeersList.addView(tv);
            }
        };

        runOnUiThread(r);
    }

    @Override
    public void onPeerGone(final UDN udn) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                final int idx = findPeerView(udn);
                if (-1 != idx) {
                    mPeersList.removeViewAt(idx);
                }
            }
        };
        runOnUiThread(r);
    }

    private final int findPeerView(final UDN udn) {
        int result = -1;
        for (int i = 0; i < mPeersList.getChildCount(); ++i) {
            final View v = mPeersList.getChildAt(i);
            if (udn.equals(v.getTag(R.id.tag_udn))) {
                result = i;
                break;
            }
        }
        return result;
    }


    /*byte[] aa() throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidParameterSpecException, BadPaddingException, IllegalBlockSizeException {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] salt = { 0 };
        KeySpec spec = new PBEKeySpec(mPin.toCharArray(), salt, 1, 256);
        SecretKey tmp = f.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, secret);

        AlgorithmParameters ap = c.getParameters();
        //byte[] iv = ap.getParameterSpec(IvParameterSpec.class).getIV();
        byte[] ct = c.doFinal("Hello World!".getBytes());

        return ct;
    }

    void bb(byte[] bytes) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidParameterSpecException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException {
        //
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] salt = { 0 };
        KeySpec spec = new PBEKeySpec(mPin.toCharArray(), salt, 1, 256);
        SecretKey tmp = f.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        //AlgorithmParameters ap = c.getParameters();
        //byte[] iv = ap.getParameterSpec(IvParameterSpec.class).getIV();
        c.init(Cipher.DECRYPT_MODE, secret);
    String txt = new String(c.doFinal(bytes), "UTF-8");
    System.err.println("txt: "+txt);
}*/

    private final void onMenuDumpPeers() {
        if (null != mService) {
            //@todo reimplement from scratch
/*            final Map<String, Utilities.PeerInfo> peers = Utilities.loadAllPeersInfo(this);
            String s = "";  // to sb, check bcode
            for (Map.Entry<String, Utilities.PeerInfo> info : peers.entrySet()) {
                s += "Peer udn: " + info.getKey() + "; title: " + info.getValue().title + '\n';
                for (final String ss : info.getValue().uris) {
                    s += "Endpoint: " + ss + '\n';
                }
            }

            mStatusTextString = s + mStatusTextString;*/
        } else {
            mStatusTextString = "Not connected to background service\n" + mStatusTextString;
        }
        displayStatusText();
    }

}
