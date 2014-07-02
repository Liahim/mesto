package com.example.mycalls;

import android.os.Build;
import android.util.Log;

import org.teleal.cling.binding.annotations.UpnpAction;
import org.teleal.cling.binding.annotations.UpnpInputArgument;
import org.teleal.cling.binding.annotations.UpnpOutputArgument;
import org.teleal.cling.binding.annotations.UpnpService;
import org.teleal.cling.binding.annotations.UpnpServiceId;
import org.teleal.cling.binding.annotations.UpnpServiceType;
import org.teleal.cling.binding.annotations.UpnpStateVariable;
import org.teleal.cling.model.types.csv.CSV;
import org.teleal.cling.model.types.csv.CSVString;

import java.beans.PropertyChangeSupport;

@UpnpService(
        serviceId = @UpnpServiceId("MestoPeer"),
        serviceType = @UpnpServiceType(value = "MestoPeer", version = 1)
)
public class MestoPeer {
    public final static String ID = "MestoPeer";

    private final PropertyChangeSupport mChangeSupport = new PropertyChangeSupport(this);

    public PropertyChangeSupport getPropertyChangeSupport() {
        return mChangeSupport;
    }

    public MestoPeer() {
        super();
        Log.i(UpnpController.TAG, "MestoPeer created");
    }

    @UpnpStateVariable()
    private String pin;

    @UpnpStateVariable(defaultValue = "0")
    private boolean authed;

    @UpnpStateVariable()
    private CSV<String> mesto;

    @UpnpAction
    public void setPin(@UpnpInputArgument(name = "Pin") final String pin) {
        this.pin = pin;
        Log.i(UpnpController.TAG, "pin set to " + this.pin);
        mChangeSupport.firePropertyChange("Pin", this.pin, pin);
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "Authed"))
    public boolean getAuthed() {
        return authed;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "Mesto"))
    public CSV<String> getMesto() {
        if (null == mesto) {
            mesto = new CSVString();
        } else {
            mesto.clear();
        }

        mesto.add(Build.DEVICE);

        final String me = Utilities.getIPAddress(true) + ":50001";
        mesto.add(me);

        return mesto;
    }
}
