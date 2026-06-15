package com.whxbill.backend.modules.bill.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class BillImportRequest {

    @Valid
    @NotEmpty(message = "导入账单不能为空")
    private List<BillImportRow> rows;
}
