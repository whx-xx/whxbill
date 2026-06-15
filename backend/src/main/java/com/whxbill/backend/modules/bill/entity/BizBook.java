package com.whxbill.backend.modules.bill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_book")
public class BizBook extends BaseEntity {

    private Long userId;
    private String bookName;
    private String currencyCode;
    private Integer isDefault;
}
