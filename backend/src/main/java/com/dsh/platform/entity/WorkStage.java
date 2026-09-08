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
    private Integer reworkCount;
    private LocalDateTime reworkDeadlineAt;
    /** 买家要求返工时根据质检结果生成 */
    private String reworkReason;
    private BigDecimal payAmount;
    private BigDecimal firstYield;
    private Integer inspectRound;
    private Integer firstQuantityOk;
    /** 工厂实交件数（上报进度或交付时写入） */
    private Integer deliveredQty;
    /** 交付期次（同一工厂同一需求下第几期） */
    private Integer periodNo;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String inspectFeeStatus;
    private BigDecimal inspectFeeAmount;
    private String inspectFeePayer;
    private Long parentStageId;
    private String reworkKind;
    @TableField(exist = false)
    private Long demandId;
    @TableField(exist = false)
    private String demandTitle;
    @TableField(exist = false)
    private Boolean windowOpen;
    @TableField(exist = false)
    private String periodLabel;
    @TableField(exist = false)
    private Integer requiredSampleCount;
    @TableField(exist = false)
    private Boolean canConcede;
    @TableField(exist = false)
    private Boolean canRework;
    @TableField(exist = false)
    private Boolean canClose;
    @TableField(exist = false)
    private String branchCode;
    @TableField(exist = false)
    private Boolean contractSigned;
    @TableField(exist = false)
    private Boolean surveyed;
    @TableField(exist = false)
    private String factoryName;
    @TableField(exist = false)
    private Integer minQty;
    @TableField(exist = false)
    private Integer maxQty;
    /** 质检单：需求产品名 */
    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String category;
    @TableField(exist = false)
    private String techSpecs;
    @TableField(exist = false)
    private String qualityThreshold;
    @TableField(exist = false)
    private String buyerRemark;
    /** 质检单：需求最低良率 */
    @TableField(exist = false)
    private BigDecimal minYield;
    @TableField(exist = false)
    private String inspectMode;
    @TableField(exist = false)
    private String aql;
    @TableField(exist = false)
    private Integer aqlAc;
    @TableField(exist = false)
    private Integer aqlRe;
    /** 质检员是否已提交质检单 */
    @TableField(exist = false)
    private Boolean hasInspection;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
