package com.whxbill.backend.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_permission")
public class SysRolePermission extends BaseEntity {

    private Long roleId;
    private Long permissionId;
}
