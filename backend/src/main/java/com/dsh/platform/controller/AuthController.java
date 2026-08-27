package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.dto.AuthDtos.*;
import com.dsh.platform.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public R<Void> register(@RequestBody RegisterRequest req) {
        authService.register(req);
        return R.ok();
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @PostMapping("/create-user")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<Void> createUser(@RequestBody CreateUserRequest req) {
        authService.createUser(req);
        return R.ok();
    }
}
