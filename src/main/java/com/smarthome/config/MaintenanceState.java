package com.smarthome.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MaintenanceState {

    private final AtomicBoolean enabled;

    public MaintenanceState(@Value("${app.maintenance.enabled:false}") boolean initialFromConfig) {
        this.enabled = new AtomicBoolean(initialFromConfig);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
