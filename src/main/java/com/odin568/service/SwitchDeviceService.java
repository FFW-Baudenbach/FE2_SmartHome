package com.odin568.service;

import com.github.kaklakariada.fritzbox.FritzBoxException;
import com.github.kaklakariada.fritzbox.HomeAutomation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class SwitchDeviceService implements HealthIndicator
{
    public enum SwitchState { ON, OFF, TOGGLE }

    private static final Logger LOG = LoggerFactory.getLogger(SwitchDeviceService.class);
    private final String url;
    private final String username;
    private final String password;
    private final String switchId;


    public SwitchDeviceService(@Value("${fritzbox.url}") String url,
                               @Value("${fritzbox.username}") String username,
                               @Value("${fritzbox.password}") String password,
                               @Value("${fritzbox.switchid}") Long switchId)
    {
        this.url = url;
        this.username = username;
        this.password = password;
        this.switchId = String.valueOf(switchId);
    }

    @PostConstruct
    private void checkConfiguration() {
        if (!url.matches("^http(s)?://.*"))
            throw new IllegalArgumentException("Invalid FritzBox url. Needs to start with protocol (http, https)");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Invalid FritzBox username");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Invalid FritzBox password");
        }
        if (switchId == null || switchId.isBlank()) {
            throw new IllegalStateException("Invalid SwitchId configured");
        }
    }

    public SwitchState SwitchPowerState(final SwitchState targetState)
    {
        LOG.info("Started switching to mode " + targetState);

        HomeAutomation homeAutomation = null;
        try {
            homeAutomation = HomeAutomation.connect(url, username, password);

            validateSwitchDevice(homeAutomation);

            switch(targetState) {
                case ON -> homeAutomation.switchPowerState(switchId, true);
                case OFF -> homeAutomation.switchPowerState(switchId, false);
                case TOGGLE -> homeAutomation.togglePowerState(switchId);
            }

            // It can take some time until state is reflected properly
            Thread.sleep(1000);

            SwitchState newState = homeAutomation.getSwitchState(switchId) ? SwitchState.ON : SwitchState.OFF;

            if (targetState != SwitchState.TOGGLE && newState != targetState)
                throw new FritzBoxException("Switching power state " + targetState + " failed");

            LOG.info("Finished switching to mode " + targetState);
            return newState;
        }
        catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        catch (RuntimeException ex) {
            LOG.error("Failed switching power state", ex);
            throw ex;
        }
        finally {
            if (homeAutomation != null)
                homeAutomation.logout();
        }
    }

    private void validateSwitchDevice(final HomeAutomation homeAutomation) {
        if (!homeAutomation.getSwitchList().contains(switchId)) {
            throw new FritzBoxException("Switch not found");
        }
        if (!homeAutomation.getSwitchPresent(switchId)) {
            throw new FritzBoxException("Switch currently not present");
        }
    }

    @Override
    public Health health() {
        HomeAutomation homeAutomation = null;
        try {
            homeAutomation = HomeAutomation.connect(url, username, password);
            List<String> switchDevices = homeAutomation.getSwitchList();

            if (switchId.isBlank()) {
                throw new FritzBoxException("No SwitchId configured");
            }

            if (!switchDevices.contains(switchId)) {
                throw new FritzBoxException("Switch not found");
            }

            if (!homeAutomation.getSwitchPresent(switchId)) {
                throw new FritzBoxException("Switch currently not present");
            }

            boolean switchState = homeAutomation.getSwitchState(switchId);

            return Health.up().withDetail("switchState", switchState ? "ON" : "OFF").build();
        }
        catch (RuntimeException e) {
            LOG.error("Unable to determine health", e);
            return Health.down().withDetail("exception", e.getMessage()).build();
        }
        finally {
            if (homeAutomation != null)
                homeAutomation.logout();
        }
    }
}
