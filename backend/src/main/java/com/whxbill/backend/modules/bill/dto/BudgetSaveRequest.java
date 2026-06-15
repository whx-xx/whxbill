package com.whxbill.backend.modules.bill.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class BudgetSaveRequest {

    private Long id;
    @NotNull(message = "账本不能为空")
    private Long bookId;
    private Long categoryId;
    @NotBlank(message = "预算月份不能为空")
    private String budgetMonth;
    @NotNull(message = "预算金额不能为空")
    @DecimalMin(value = "0.01", message = "预算金额必须大于0")
    private BigDecimal budgetAmount;
}
