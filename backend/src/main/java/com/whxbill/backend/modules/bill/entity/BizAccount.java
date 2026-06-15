package com.whxbill.backend.modules.bill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_account")
public class BizAccount extends BaseEntity {

    private Long userId;
    private Long bookId;
    private String accountName;
    private String accountType;
    private BigDecimal balance;
    private String colorTag;
    private Integer sortOrder;
}
