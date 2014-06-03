
package com.example.mycalls;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

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
        // Inflate the menu; this adds items to the action bar if it is present.
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
        return true;
    }

}
