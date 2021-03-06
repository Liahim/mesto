package bg.mrm.mesto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Utilities.openLogger(context);
        Utilities.log("boot receiver invoked");
        Utilities.closeLogger();

        final Intent startIntent = new Intent().setClassName(context, MestoLocationService.class.getName());
        context.startService(startIntent);
    }
}
