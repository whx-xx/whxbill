package com.whxbill.backend.modules.bill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_attachment")
public class BizAttachment extends BaseEntity {

    private Long userId;
    private Long billId;
    private String fileName;
    private String fileUrl;
    private String contentType;
    private Long fileSize;
}
