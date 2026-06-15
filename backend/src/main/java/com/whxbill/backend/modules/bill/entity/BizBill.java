package com.whxbill.backend.modules.bill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bill")
public class BizBill extends BaseEntity {

    private Long userId;
    private Long bookId;
    private Long accountId;
    private Long categoryId;
    private String billType;
    private BigDecimal amount;
    private LocalDate billDate;
    private LocalDateTime billTime;
    private String merchantName;
    private String remark;
    private String sourceType;
}
