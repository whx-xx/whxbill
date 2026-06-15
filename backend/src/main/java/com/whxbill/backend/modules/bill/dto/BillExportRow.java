package com.whxbill.backend.modules.bill.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillExportRow {

    @ExcelProperty("账单编号")
    private Long id;

    @ExcelProperty("账本")
    private String bookName;

    @ExcelProperty("账单类型")
    private String billType;

    @ExcelProperty("金额")
    private BigDecimal amount;

    @ExcelProperty("账单日期")
    private String billDate;

    @ExcelProperty("账户")
    private String accountName;

    @ExcelProperty("分类")
    private String categoryName;

    @ExcelProperty("商户来源")
    private String merchantName;

    @ExcelProperty("备注")
    private String remark;

    @ExcelProperty("记录方式")
    private String sourceType;
}
