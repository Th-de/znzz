package com.dsh.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dsh.platform.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
