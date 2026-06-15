package com.whxbill.backend.modules.bill.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BillImportPreviewResponse {

    private String fileName;
    private Integer total;
    private Integer matched;
    private BigDecimal incomeTotal;
    private BigDecimal expenseTotal;
    private List<BillImportRow> rows;
}
