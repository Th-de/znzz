package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("contract")
public class Contract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long tenantId;
    private Integer version;
    private String samplingJson;
    private String responsibilityJson;
    private String status;
    private String hash;
    private Long attachmentId;
    private Integer buyerRead;
    private String buyerSign;
    private Integer factoryRead;
    private String factorySign;
    private String factorySignsJson;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String factoryName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer minQty;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer maxQty;
    /** 确认方案中该厂承接数量 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer allocQty;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String processNames;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String fileName;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String signHint;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String demandTitle;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String buyerName;
    private LocalDateTime signedAt;
    private LocalDateTime createdAt;
}
