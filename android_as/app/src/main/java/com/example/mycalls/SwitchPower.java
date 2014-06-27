package com.example.mycalls;

import org.teleal.cling.binding.annotations.UpnpAction;
import org.teleal.cling.binding.annotations.UpnpInputArgument;
import org.teleal.cling.binding.annotations.UpnpOutputArgument;
import org.teleal.cling.binding.annotations.UpnpService;
import org.teleal.cling.binding.annotations.UpnpServiceId;
import org.teleal.cling.binding.annotations.UpnpServiceType;
import org.teleal.cling.binding.annotations.UpnpStateVariable;

@UpnpService(
        serviceId = @UpnpServiceId("SwitchPower"),
        serviceType = @UpnpServiceType(value = "SwitchPower", version = 1)
)
public class SwitchPower {
    @UpnpStateVariable(defaultValue = "0", sendEvents = false)
    private boolean target;

    @UpnpStateVariable(defaultValue = "0")
    private boolean status;

    @UpnpAction
    public void setTarget(@UpnpInputArgument(name = "NewTargetValue") boolean newTargetValue) {
        target = newTargetValue;
        status = newTargetValue;
        System.err.println("Switch is: " + newTargetValue);
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "RefTargetValue"))
    public boolean getTarget() {
        return target;
    }

    @UpnpAction(out = @UpnpOutputArgument(name = "ResultStatus"))
    public boolean getStatus() {
        return status;
    }
}
