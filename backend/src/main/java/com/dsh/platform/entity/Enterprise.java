package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("enterprise")
public class Enterprise {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String name;
    private String creditCode;
    private Integer creditScore;
    private String authStatus;
    private String contactName;
    private String contactPhone;
    private String legalPerson;
    private String bankAccount;
    private String capabilityJson;
    /** 企业介绍（工厂自填，买家可见） */
    private String introduction;
    private String address;
    @TableField(exist = false)
    private String accountPhone;
    @TableField(exist = false)
    private Long userId;
    @TableField(exist = false)
    private String userStatus;
    @TableField(exist = false)
    private String realName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
