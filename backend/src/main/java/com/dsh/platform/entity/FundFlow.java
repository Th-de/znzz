package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fund_flow")
public class FundFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long demandId;
    private Long tenantId;
    private String type;
    private String direction;
    private BigDecimal amount;
    private String status;
    private String idempotentNo;
    @TableField(exist = false)
    private String enterpriseName;
    private LocalDateTime createdAt;
}
