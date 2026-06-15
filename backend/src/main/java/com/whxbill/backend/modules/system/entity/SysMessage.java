package com.whxbill.backend.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message")
public class SysMessage extends BaseEntity {

    private Long userId;
    private String messageType;
    private String title;
    private String content;
    private Integer readStatus;
}
