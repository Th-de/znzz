package com.dsh.platform.config;

import com.dsh.platform.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(35)
@RequiredArgsConstructor
public class DeviceSeeder implements CommandLineRunner {

    private final DeviceService deviceService;

    @Override
    public void run(String... args) {
        deviceService.migrateFromCapability();
        deviceService.fillMissingCapacity();
        deviceService.fillMissingProcessNames();
        deviceService.ensureFactoryFleet(50);
        log.info("工厂设备已迁移并补齐至每厂 50 台");
    }
}
