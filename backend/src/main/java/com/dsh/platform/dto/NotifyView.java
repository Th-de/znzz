package com.dsh.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotifyView {
    private Long id;
    private String title;
    private String content;
    private Integer isRead;
    private LocalDateTime createdAt;
    private String link;
    private String action;
}
