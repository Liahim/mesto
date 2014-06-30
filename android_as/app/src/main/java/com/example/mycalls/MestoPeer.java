package com.example.mycalls;

import android.util.Log;

import org.teleal.cling.binding.annotations.UpnpAction;
import org.teleal.cling.binding.annotations.UpnpInputArgument;
import org.teleal.cling.binding.annotations.UpnpOutputArgument;
import org.teleal.cling.binding.annotations.UpnpService;
import org.teleal.cling.binding.annotations.UpnpServiceId;
import org.teleal.cling.binding.annotations.UpnpServiceType;
import org.teleal.cling.binding.annotations.UpnpStateVariable;

@UpnpService(
        serviceId = @UpnpServiceId("MestoPeer"),
        serviceType = @UpnpServiceType(value = "MestoPeer", version = 1)
)
public class MestoPeer {
    public final static String ID = "MestoPeer";

    public MestoPeer() {
        super();
        Log.i(UpnpController.TAG, "MestoPeer created");
    }

    @UpnpStateVariable(defaultValue = "0", sendEvents = false)
    private int pin;

    @UpnpStateVariable(defaultValue = "0")
    private boolean authed;

    @UpnpAction
    public void setPin(@UpnpInputArgument(name = "Pin") int pin) {
        this.pin = pin;
        Log.i(UpnpController.TAG, "pin set to " + this.pin);
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "Authed"))
    public boolean getAuthed() {
        return authed;
    }
}
