package com.whxbill.backend.modules.bill.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class BillImportRow {

    private Integer rowNo;
    private Boolean selected = true;
    private Boolean valid = true;
    private String errorMessage;
    private String warningMessage;
    private List<String> rawColumns;
    private String rawText;
    private Long bookId;
    private String bookName;
    private Long accountId;
    private String accountName;
    private Boolean accountMissing = false;
    private Long categoryId;
    private String categoryName;
    private String billType;
    private BigDecimal amount;
    private LocalDate billDate;
    private String billTime;
    private String merchantName;
    private String remark;
    private String sourceType = "IMPORT";
    private String paymentMethod;
    private String tradeStatus;
    private String transactionNo;
    private String merchantNo;
}
