package com.whxbill.backend.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_operation_log")
public class SysOperationLog extends BaseEntity {

    private Long operatorId;
    private String operatorName;
    private String ipAddress;
    private String moduleName;
    private String operationType;
    private String requestUri;
    private String requestMethod;
    private String operationContent;
}
