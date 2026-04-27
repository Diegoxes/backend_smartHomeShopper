package com.smarthome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartHomeShopperApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartHomeShopperApplication.class, args);
    }
}
