
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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.TextView;

public final class MestoActivity extends Activity {
    private MestoLocationService mService;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Intent intent = new Intent().setClassName(this, MestoLocationService.class.getName());
        startService(intent);
        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceDisconnected(final ComponentName name) {
            }

            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                mService = ((MestoLocationService.Binder) service).getService();
            }
        }, 0);
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
