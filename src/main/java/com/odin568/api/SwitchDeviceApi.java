package com.odin568.api;

import com.odin568.service.SwitchDeviceService;
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

    @GetMapping("/switchDeviceApi/switchOn")
    public String SwitchDeviceOn() {
        try {
            switchDeviceService.SwitchPowerState(true);
        }
        catch (RuntimeException ex) {
            return "FAILED - " + ex.getMessage();
        }
        return "OK";
    }

    @GetMapping("/switchDeviceApi/switchOff")
    public String SwitchDeviceOff() {
        try {
            switchDeviceService.SwitchPowerState(false);
        }
        catch (RuntimeException ex) {
            return "FAILED - " + ex.getMessage();
        }
        return "OK";
    }
}
