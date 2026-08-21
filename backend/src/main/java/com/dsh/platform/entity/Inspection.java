package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection")
public class Inspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stageId;
    private Long inspectorTenantId;
    private String result;
    private String reportJson;
    private String hash;
    private String status;
    private LocalDateTime createdAt;
}
