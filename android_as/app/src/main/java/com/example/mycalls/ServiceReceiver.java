
package com.example.mycalls;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ServiceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        /*final TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephony.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(final int state, final String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                System.out.println("incomingNumber : " + incomingNumber);
                if (TelephonyManager.CALL_STATE_RINGING == state) {
                    MainActivity.answerCall(context);
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);*/
    }

    public ServiceReceiver() {
    }

}
