package com.dsh.platform.config;

import com.dsh.platform.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AuthService authService;

    @Override
    public void run(String... args) {
        try {
            authService.initSuperAdmin();
            log.info("超级管理员已就绪（账号 admin / 密码 admin123）");
        } catch (Exception e) {
            log.error("初始化超级管理员失败", e);
        }
    }
}
