package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("solution")
public class Solution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long demandId;
    private String type;
    private String suggestedComboJson;
    private String finalComboJson;
    private String editedFieldsJson;
    private String source;
    private Integer isFinal;
    private BigDecimal score;
    private Long editedBy;
    private LocalDateTime editedAt;
    private String status;
    private String rationaleJson;
    private LocalDateTime createdAt;
}
