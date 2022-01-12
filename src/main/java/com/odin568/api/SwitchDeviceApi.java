package com.odin568.api;

import com.odin568.service.SwitchDeviceService;
import com.odin568.service.SwitchDeviceService.SwitchState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwitchDeviceApi {

    private final SwitchDeviceService switchDeviceService;

    @Autowired
    public SwitchDeviceApi(SwitchDeviceService switchDeviceService)
    {
        this.switchDeviceService = switchDeviceService;
    }

    @GetMapping("/switchDeviceApi/toggle")
    public String ToggleDevice() {
        try {
            return switchDeviceService.SwitchPowerState(SwitchState.TOGGLE).toString();
        }
        catch (RuntimeException ex) {
            return "ERROR: " + ex.getMessage();
        }
    }

    @GetMapping("/switchDeviceApi/switchOn")
    public String SwitchDeviceOn() {
        try {
            return switchDeviceService.SwitchPowerState(SwitchState.ON).toString();
        }
        catch (RuntimeException ex) {
            return "ERROR: " + ex.getMessage();
        }
    }

    @GetMapping("/switchDeviceApi/switchOff")
    public String SwitchDeviceOff() {
        try {
            return switchDeviceService.SwitchPowerState(SwitchState.OFF).toString();
        }
        catch (RuntimeException ex) {
            return "ERROR: " + ex.getMessage();
        }
    }
}
