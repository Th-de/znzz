package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.dto.AuthDtos.MeResponse;
import com.dsh.platform.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthService authService;

    @GetMapping
    public R<MeResponse> me() {
        return R.ok(authService.me());
    }

    @PostMapping("/avatar")
    public R<MeResponse> avatar(@RequestParam("file") MultipartFile file) {
        return R.ok(authService.updateAvatar(file));
    }
}
