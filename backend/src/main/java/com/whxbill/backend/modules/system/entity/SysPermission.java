package com.whxbill.backend.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private Long parentId;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Integer status;
}
