
package com.example.mycalls;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        Log.i(MestoLocationService.TAG, "boot receiver invoked");
        final Intent startIntent = new Intent().setClassName(context, MestoLocationService.class.getName());
        context.startService(startIntent);
    }
}