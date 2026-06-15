package com.whxbill.backend.modules.bill.dto;

import com.whxbill.backend.common.web.PageQuery;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

@Data
@EqualsAndHashCode(callSuper = true)
public class BillPageQuery extends PageQuery {

    private Long bookId;
    private String month;
    private String billType;
    private String sourceType;
    private Long accountId;
    private Long categoryId;
    private Long parentCategoryId;
    private String keyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
