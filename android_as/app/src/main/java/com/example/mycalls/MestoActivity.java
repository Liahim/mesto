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
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MestoActivity extends Activity {
    private MestoLocationService mService;
    private TextView mStatusText;
    private MenuItem mMenuItemToggleReporting;

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

            showToggleReportingIfPossible();
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.i(MestoLocationService.TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStatusText = (TextView) getWindow().findViewById(R.id.status_text);
        mStatusText.setMovementMethod(new ScrollingMovementMethod());

        final Intent intent = new Intent().setClassName(this, MestoLocationService.class.getName());
        startService(intent);
        bindService(intent, mServiceConnection, 0);
    }

    @Override
    protected final void onResume() {
        Log.i(MestoLocationService.TAG, "onResume");
        super.onResume();
        if (null != mService) {
            mService.addRunnableCallback(mRunnable);
            prepareStatusText();
            displayStatusText();
        }
    }

    @Override
    protected void onPause() {
        Log.i(MestoLocationService.TAG, "onPause");
        super.onPause();
        if (null != mService) {
            mService.removeRunnableCallback(mRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(MestoLocationService.TAG, "onDestroy");
        super.onDestroy();
        unbindService(mServiceConnection);
    }

    private final SimpleDateFormat mFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
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

    private String mStatusTextString;

    private final void prepareStatusText() {
        try {
            final Collection<MestoLocationService.Event> events = mService.getLogEvents();
            final StringBuilder sb = new StringBuilder();
            for (final MestoLocationService.Event event : events) {
                final Date date = new Date(event.mTime);
                sb.append(event.mType == MestoLocationService.Event.Type.Update ? "Updated at " : "Started at ");
                sb.append(mFormat.format(date));
                sb.append('\n');
            }
            mStatusTextString = sb.toString();
        } catch (final Exception e) {
            Log.e(MestoLocationService.TAG, "error updating status text", e);
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
        return true;
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

    private final void saveServerLocation(final String uri, final Set<String> history) {
        final SharedPreferences sp = getSharedPreferences("settings", Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sp.edit();
        editor.putString("server", uri);
        editor.putStringSet("server_history", history);
        editor.apply();
    }

    static final String loadServerLocation(final Context ctx) {
        final SharedPreferences sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        return sp.getString("server", null);
    }

    static final Pair<String, Set<String>> loadServerLocationAndHistory(final Context ctx) {
        final SharedPreferences sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        final String server = sp.getString("server", null);

        final Set<String> serverHistory = new HashSet<String>();
        serverHistory.addAll(sp.getStringSet("server_history", serverHistory));

        final Pair<String, Set<String>> result = new Pair<String, Set<String>>(server, serverHistory);
        return result;
    }

    private boolean onMenuSettings() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(MestoActivity.this);
        final LayoutInflater inflater = getLayoutInflater();

        final View view = inflater.inflate(R.layout.dialog_settings, null);
        final AutoCompleteTextView serverAddress
                = (AutoCompleteTextView) view.findViewById(R.id.serverAddress);

        final Pair<String, Set<String>> pair = loadServerLocationAndHistory(MestoActivity.this);
        serverAddress.setText(pair.first);

        if (null != pair.second && !pair.second.isEmpty()) {
            final String[] history = new String[pair.second.size()];
            final ArrayAdapter<String> autoCompleteAdapter
                    = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, pair.second.toArray(history));
            serverAddress.setAdapter(autoCompleteAdapter);
        }

        builder.setView(view)
                .setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int id) {
                        final String server = serverAddress.getText().toString();

                        final Set<String> historySet;
                        if (null == pair.second) {
                            historySet = new HashSet<String>();
                        } else {
                            historySet = pair.second;
                        }
                        historySet.add(server);

                        if (!pair.first.equalsIgnoreCase(server)) {
                            saveServerLocation(server, historySet);
                            mService.sendLocation();
                        }
                    }
                })
                .setNegativeButton(R.string.button_cancel, null);

        builder.create().show();

        return true;
    }
}
