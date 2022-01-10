package com.odin568.api;

import com.odin568.service.SwitchDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
public class SwitchDeviceApi {

    private final SwitchDeviceService switchDeviceService;

    @Autowired
    public SwitchDeviceApi(SwitchDeviceService switchDeviceService)
    {
        this.switchDeviceService = switchDeviceService;
    }

    @GetMapping("/switchDeviceApi/getDevices")
    public String GetDevices() {
        Map<String, String> devices;
        try {
            devices = switchDeviceService.GetSwitchDevices();
        }
        catch (Exception ex) {
            return "FAILED - " + ex.getMessage();
        }

        if (devices.isEmpty()) {
            return "OK - No switch devices found";
        }

        StringBuilder sb = new StringBuilder();
        for (var device : devices.entrySet()) {
            sb.append(device.getKey());
            sb.append(" - ");
            sb.append(device.getValue());
            sb.append("<br/>");
        }
        return sb.toString();
    }

    @GetMapping("/switchDeviceApi/switchOn")
    public String SwitchDeviceOn(@RequestParam("doChecks") Optional<Boolean> doChecks) {
        try {
            switchDeviceService.SwitchPowerState(true, doChecks.orElse(false));
        }
        catch (RuntimeException ex) {
            return "FAILED - " + ex.getMessage();
        }
        return "OK";
    }

    @GetMapping("/switchDeviceApi/switchOff")
    public String SwitchDeviceOff(@RequestParam("doChecks") Optional<Boolean> doChecks) {
        try {
            switchDeviceService.SwitchPowerState(false, doChecks.orElse(false));
        }
        catch (RuntimeException ex) {
            return "FAILED - " + ex.getMessage();
        }
        return "OK";
    }
}
