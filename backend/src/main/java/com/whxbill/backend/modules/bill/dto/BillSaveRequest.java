package com.whxbill.backend.modules.bill.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BillSaveRequest {

    private Long id;

    @NotNull(message = "账本不能为空")
    private Long bookId;

    @NotNull(message = "账户不能为空")
    private Long accountId;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotBlank(message = "账单类型不能为空")
    private String billType;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "账单日期不能为空")
    private LocalDate billDate;

    private LocalDateTime billTime;
    private String merchantName;
    private String remark;
    private String sourceType;
}
