package com.dsh.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String phone;
    private String password;
    /** 运营端展示用明文；登录仍校验 password 哈希 */
    private String passwordPlain;
    private String role;
    private String realName;
    private Long avatarId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
