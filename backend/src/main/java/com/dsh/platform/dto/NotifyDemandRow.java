package com.dsh.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NotifyDemandRow {
    private Long demandId;
    private String title;
    private String status;
    private LocalDateTime bidAt;
    private LocalDateTime latestNotifyAt;
    private List<NotifyView> notifies = new ArrayList<>();
}
