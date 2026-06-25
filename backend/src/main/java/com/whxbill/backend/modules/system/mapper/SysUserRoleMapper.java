package com.whxbill.backend.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whxbill.backend.modules.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Delete("delete from sys_user_role where user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}
