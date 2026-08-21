package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("credit_event")
public class CreditEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String type;
    private Integer scoreChange;
    private String refType;
    private Long refId;
    private String remark;
    private LocalDateTime createdAt;
}
