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
import java.util.*;

@Service
public class SwitchDeviceService implements HealthIndicator
{
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
    }

    public Map<String, String> GetSwitchDevices()
    {
        var result = new HashMap<String, String>();
        HomeAutomation homeAutomation = null;
        try {
            homeAutomation = HomeAutomation.connect(url, username, password);
            List<String> ids = homeAutomation.getSwitchList();

            for (String id : ids) {
                result.put(id, homeAutomation.getSwitchName(id));
            }

            return result;
        }
        catch (RuntimeException ex) {
            LOG.error("Failed getting switch devices", ex);
            throw ex;
        }
        finally {
            if (homeAutomation != null)
                homeAutomation.logout();
        }
    }

    public void SwitchPowerState(boolean on, boolean doChecks)
    {
        if (switchId.isEmpty()) {
            throw new IllegalStateException("No SwitchId configured");
        }

        HomeAutomation homeAutomation = null;
        try {
            homeAutomation = HomeAutomation.connect(url, username, password);

            if (doChecks) {
                if (!homeAutomation.getSwitchList().contains(switchId)) {
                    throw new FritzBoxException("Switch not found");
                }
                if (!homeAutomation.getSwitchPresent(switchId)) {
                    throw new IllegalStateException("Switch currently not present");
                }
            }

            homeAutomation.switchPowerState(switchId, on);

            if (doChecks) {
                if (homeAutomation.getSwitchState(switchId) != on) {
                    throw new IllegalStateException("Switching power state failed");
                }
            }
        }
        catch (RuntimeException ex) {
            LOG.error("Failed switching power state for switch " + switchId, ex);
            throw ex;
        }
        finally {
            if (homeAutomation != null)
                homeAutomation.logout();
        }
    }

    @Override
    public Health health() {
        HomeAutomation homeAutomation = null;
        try {
            homeAutomation = HomeAutomation.connect(url, username, password);
            List<String> ids = homeAutomation.getSwitchList();

            if (switchId.isEmpty()) {
                throw new FritzBoxException("No SwitchId configured");
            }

            if (!ids.contains(switchId)) {
                throw new FritzBoxException("Switch " + switchId + " not found");
            }
            boolean switchAvailable = homeAutomation.getSwitchPresent(switchId);
            boolean switchState = homeAutomation.getSwitchState(switchId);

            if (!switchAvailable) {
                throw new FritzBoxException("Switch " + switchId + " currently not present");
            }
            return Health.up().withDetail("switchState", switchState).build();
        }
        catch (FritzBoxException e) {
            LOG.error("Unable to determine health", e);
            return Health.outOfService().withDetail("reason", e.getMessage()).build();
        }
        catch (RuntimeException e) {
            LOG.error("Unexpected issue on determining health", e);
            return Health.down().withDetail("reason", e.getMessage()).build();
        }
        finally {
            if (homeAutomation != null)
                homeAutomation.logout();
        }
    }
}
