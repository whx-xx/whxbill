package com.whxbill.backend.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whxbill.backend.modules.system.entity.SysUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Delete("delete from sys_user where id = #{userId}")
    int physicalDeleteById(@Param("userId") Long userId);
}
