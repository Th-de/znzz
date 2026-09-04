package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("demand")
public class Demand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String title;
    private String productName;
    private String category;
    private Integer quantity;
    private String material;
    private String tolerance;
    private String surfaceTreatment;
    private String aql;
    private String certification;
    private BigDecimal minYield;
    private Integer minCreditScore;
    private LocalDate deadlineHard;
    private LocalDate deadlineFlexible;
    private String deliveryAddress;
    private String packaging;
    private Integer multiProcess;
    private String weightJson;
    private Integer intentionDays;
    private String remark;
    private String inspectMode;
    /** 质检单价（元/件） */
    private BigDecimal inspectPrice;
    private String generalTolerance;
    private String partRevision;
    private String extraJson;
    private String returnReason;
    private String cancelReason;
    private Long sourceDemandId;
    private java.time.LocalDateTime intentionEndAt;
    private LocalDateTime factoryThinkingEndAt;
    private LocalDateTime buyerThinkingEndAt;
    private LocalDateTime thinkingEndAt;
    private LocalDateTime reviewEndAt;
    private LocalDateTime lockingEndAt;
    /** 审核通过、进入意向期的时间 */
    private LocalDateTime publishedAt;
    /** 进入工厂思考期的时间 */
    private LocalDateTime factoryThinkingAt;
    /** 进入买家思考期的时间 */
    private LocalDateTime buyerThinkingAt;
    /** 分期交付次数（买家发布时定，工厂不可改） */
    private Integer deliveryTimes;
    /** 每期交付要求，JSON 数组（买家填） */
    private String deliveryPlanJson;
    /** 预估总价：买家保证金计费基数 */
    private BigDecimal estimatedTotal;
    /** 买家保证金状态 NONE/FROZEN/DEDUCTED/RELEASED */
    private String buyerDepositStatus;
    private String status;
    @TableField(exist = false)
    private Boolean applied;
    @TableField(exist = false)
    private String myQuoteStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
