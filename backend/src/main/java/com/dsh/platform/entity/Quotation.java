package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("quotation")
public class Quotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long demandId;
    private Integer processNo;
    private BigDecimal intentionPrice;
    private BigDecimal price;
    private BigDecimal yieldRate;
    private Integer promisedDays;
    private Integer minQty;
    private Integer maxQty;
    private String stageCurveJson;
    private Integer validDays;
    private String extraJson;
    private String deviceIdsJson;
    private String intentionStatus;
    private String depositStatus;
    private String status;
    private Integer version;
    @TableField(exist = false)
    private String demandTitle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
