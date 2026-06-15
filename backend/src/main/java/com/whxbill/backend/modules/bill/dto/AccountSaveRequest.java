package com.whxbill.backend.modules.bill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AccountSaveRequest {

    private Long id;
    @NotNull(message = "账本不能为空")
    private Long bookId;
    @NotBlank(message = "账户名称不能为空")
    private String accountName;
    @NotBlank(message = "账户类型不能为空")
    private String accountType;
    private BigDecimal balance;
    private String colorTag;
    private Integer sortOrder = 0;
}
