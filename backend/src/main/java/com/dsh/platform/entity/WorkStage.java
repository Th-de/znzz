package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("work_stage")
public class WorkStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long tenantId;
    private Integer processNo;
    private String processName;
    private Integer quantity;
    private Integer promisedDays;
    private LocalDate promisedDate;
    private BigDecimal amount;
    private String escrowStatus;
    private Integer actualProgress;
    private String status;
    @TableField(exist = false)
    private Boolean contractSigned;
    @TableField(exist = false)
    private Boolean surveyed;
    @TableField(exist = false)
    private String factoryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
