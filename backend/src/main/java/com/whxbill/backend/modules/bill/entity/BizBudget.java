package com.whxbill.backend.modules.bill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_budget")
public class BizBudget extends BaseEntity {

    private Long userId;
    private Long bookId;
    private Long categoryId;
    private String budgetMonth;
    private BigDecimal budgetAmount;
    private BigDecimal usedAmount;
}
