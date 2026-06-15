package com.whxbill.backend.modules.bill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BookSaveRequest {

    private Long id;
    @NotBlank(message = "账本名称不能为空")
    private String bookName;
    private String currencyCode = "CNY";
    private Integer isDefault = 0;
}
