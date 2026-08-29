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
    /** 单件报价（工厂思考期填），总报价 = unitPrice × maxQty */
    private BigDecimal unitPrice;
    private BigDecimal price;
    private BigDecimal yieldRate;
    private Integer promisedDays;
    private Integer minQty;
    private Integer maxQty;
    private String stageCurveJson;
    /** 实施方案（工厂思考期填） */
    private String planText;
    /** 每期交付内容（工厂填，期数由买家 deliveryTimes 决定） */
    private String deliveryPlanJson;
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
