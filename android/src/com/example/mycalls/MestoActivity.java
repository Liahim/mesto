
package com.example.mycalls;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MestoActivity extends Activity {
    private MestoLocationService mService;
    private TextView mStatusText;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(final ComponentName name) {
        }

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = ((MestoLocationService.Binder) service).getService();
            mService.setRunnableCallback(mRunnable);
            showStatusText();
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStatusText = (TextView) getWindow().findViewById(R.id.status_text);

        final Intent intent = new Intent().setClassName(this, MestoLocationService.class.getName());
        startService(intent);
        bindService(intent, mServiceConnection, 0);
    }

    @Override
    protected final void onResume() {
        super.onResume();
        if (null != mService) {
            mService.setRunnableCallback(mRunnable);
            showStatusText();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mService.setRunnableCallback(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
    }

    private final SimpleDateFormat mFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss zzzz", Locale.US);
    private final Runnable mRunnable = new Runnable() {
        @Override
        public final void run() {
            runOnUiThread(new Runnable() {
                @Override
                public final void run() {
                    showStatusText();
                }
            });
        }
    };

    private final void showStatusText() {
        try {
            final long time = mService.getLastUpdateTime();
            if (0 < time) {
                final Date date = new Date(mService.getLastUpdateTime());
                final String s = mFormat.format(date);
                mStatusText.setText("Last updated at " + s);
            } else {
                mStatusText.setText("No updates recently");
            }
        } catch (final Exception e) {
            Log.e(MestoLocationService.TAG, "error updating status text", e);
        }
    }

    public static void answerCall(final Context context) {
        final Intent buttonDown = new Intent(Intent.ACTION_MEDIA_BUTTON);
        buttonDown.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
        context.sendOrderedBroadcast(buttonDown, "android.permission.CALL_PRIVILEGED");

        // froyo and beyond trigger on buttonUp instead of buttonDown
        final Intent buttonUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
        buttonUp.putExtra(Intent.EXTRA_KEY_EVENT,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
        context.sendOrderedBroadcast(buttonUp, "android.permission.CALL_PRIVILEGED");
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
                final AlertDialog.Builder builder = new AlertDialog.Builder(MestoActivity.this);
                final LayoutInflater inflater = getLayoutInflater();

                final View view = inflater.inflate(R.layout.dialog_settings, null);
                final TextView serverAddress = (TextView) view.findViewById(R.id.serverAddress);
                if (null != serverAddress) {
                    serverAddress.setText(loadServerLocation(MestoActivity.this));
                }

                builder.setView(view)
                        .setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                final String server = serverAddress.getText().toString();
                                saveServerLocation(server);
                            }
                        })
                        .setNegativeButton(R.string.button_cancel, null);

                builder.create().show();

                return true;
            }
        });

        menu.findItem(R.id.menu_toggle_reporting).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (null != mService) {
                    if (mService.isReporting()) {
                        item.setTitle(R.string.start_reporting);
                        mService.stopReporting();
                        mStatusText.setText("Location reporting stopped");
                    } else {
                        item.setTitle(R.string.stop_reporting);
                        mService.startReporting();
                        mStatusText.setText("No updates recently");
                    }
                }
                return true;
            }
        });
        return true;
    }

    private final void saveServerLocation(final String uri) {
        final SharedPreferences sp = getSharedPreferences("settings", Context.MODE_PRIVATE);
        sp.edit().putString("server", uri).apply();
    }

    static final String loadServerLocation(final Context ctx) {
        final SharedPreferences sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        return sp.getString("server", null);
    }
}
