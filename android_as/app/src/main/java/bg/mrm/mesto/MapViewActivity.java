package bg.mrm.mesto;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static bg.mrm.mesto.Globals.TAG;
import static bg.mrm.mesto.MestoLocationService.EventNotificationListener;

/**
 * @todo will have to make the server a separate service
 */
public class MapViewActivity extends Activity {
    private GoogleMap mMap;
    private MestoLocationService mService;

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
            mService.addEventNotificationListener(mListener);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_view);
        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        final Intent intent = new Intent().setClassName(this, MestoLocationService.class.getName());
        final ComponentName cn = startService(intent);
        if (null == cn) {
            throw new IllegalStateException("Service not found");
        }
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
        testSendLocation();
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMarkers.clear();
        unbindService(mServiceConnection);
    }

    //@todo broken
    private void testSendLocation() {
        final Runnable r = new Runnable() {
            @Override
            public final void run() {
                try {
                    final String[] places = {"Sofia, Bulgaria", "San Jose, CA", "Dallas, TX", "Varna, BG"};
                    final Geocoder geocoder = new Geocoder(MapViewActivity.this);
                    final int idx = new Random().nextInt(places.length);
                    Log.i(TAG, "testSendLocation: " + places[idx]);
                    final List<Address> addresses = geocoder.getFromLocationName(places[idx], 1);

                    if (addresses.size() > 0) {
                        /*final Set<String> uris = Utilities.loadEndPoints(MapViewActivity.this);
                        if (null != uris) {
                            final URI uri = new URI("tcp://" + uris);
                            final Socket s = new Socket(InetAddress.getByName(uri.getHost()), uri.getPort());

                            try {
                                final ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
                                final DataOutputStream dos = new DataOutputStream(baos);

                                dos.writeDouble(addresses.get(0).getLatitude());
                                dos.writeDouble(addresses.get(0).getLongitude());
                                final byte[] bytes = baos.toByteArray();
                                s.getOutputStream().write(bytes);
                            } finally {
                                s.close();
                            }
                        }*/
                    }

                } catch (final Exception e) {
                    Log.d(TAG, "error writing to server: ", e);
                }
            }
        };
        new Thread(r).start();
    }

    private final Map<String, Marker> mMarkers = new HashMap<String, Marker>();
    private final SimpleDateFormat mFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
    private final EventNotificationListener mListener = new EventNotificationListener() {
        @Override
        public void onEvent(final String udn, final String product, double latitude, double longitude) {
            Log.i(TAG, "received location update: " + latitude + ", " + longitude);
            final long now = System.currentTimeMillis();
            final String title = mFormat.format(new Date(now));

            final LatLng ll = new LatLng(latitude, longitude);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 10));

                    Marker m = mMarkers.get(udn);
                    if (null != m) {
                        m.setPosition(ll);
                        m.setTitle(product + '\n' + title);
                    } else {
                        m = mMap.addMarker(new MarkerOptions().position(ll).title(product + '\n' + title));
                        mMarkers.put(udn, m);
                    }
                }
            });
        }
    };

}