package com.example.mycalls;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapViewActivity extends Activity {
    private GoogleMap mMap;
    private MestoLocationService mService;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public final void onServiceDisconnected(final ComponentName name) {
            Log.i(MestoLocationService.TAG, "onServiceDisconnected");
            mService = null;
        }

        @Override
        public final void onServiceConnected(final ComponentName name, final IBinder service) {
            Log.i(MestoLocationService.TAG, "onServiceConnected");
            mService = ((MestoLocationService.Binder) service).getService();
            mService.addRunnableCallback(mRunnable);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_view);
        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        final Intent intent = new Intent().setClassName(this, MestoLocationService.class.getName());
        /*final ComponentName cn = startService(intent);
        if (null == cn) {
            throw new IllegalStateException("Service not found");
        }*/
        boolean b = bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        if (!b) {
            throw new IllegalStateException("Not connected to the service");
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.map_view, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public final void run() {
            final Location l = mService.getLocation();
            if (null != l) {
                final LatLng ll = new LatLng(l.getLatitude(), l.getLongitude());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMap.clear();
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 10));
                        mMap.addMarker(new MarkerOptions().position(ll).title("Me"));
                    }
                });
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
    }

}