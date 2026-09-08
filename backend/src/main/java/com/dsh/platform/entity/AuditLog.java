package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorId;
    private String action;
    private String targetType;
    private Long targetId;
    private String beforeJson;
    private String afterJson;
    private String hash;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String actorName;
    /** 对象类型中文名，如「需求」「订单」 */
    @TableField(exist = false)
    private String targetLabel;
}
