package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stage_progress_log")
public class StageProgressLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stageId;
    private Integer doneQty;
    private Integer progress;
    private String remark;
    private Long attachmentId;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String fileName;
}
