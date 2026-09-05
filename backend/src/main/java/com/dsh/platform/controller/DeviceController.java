package com.dsh.platform.controller;

import com.dsh.platform.common.R;
import com.dsh.platform.entity.Device;
import com.dsh.platform.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/mine")
    @PreAuthorize("hasRole('FACTORY')")
    public R<List<Device>> mine() {
        return R.ok(deviceService.listMine());
    }

    @PostMapping
    @PreAuthorize("hasRole('FACTORY')")
    public R<Long> save(@RequestBody Device body) {
        return R.ok(deviceService.save(body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('FACTORY')")
    public R<Void> status(@PathVariable Long id, @RequestBody Map<String, String> body) {
        deviceService.setStatus(id, body == null ? null : body.get("status"));
        return R.ok();
    }
}
