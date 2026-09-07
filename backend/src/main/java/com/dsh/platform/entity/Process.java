package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process")
public class Process {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long demandId;
    private Integer processNo;
    private String processName;
    private String requirement;
    private LocalDateTime createdAt;
}
