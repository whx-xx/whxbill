package com.whxbill.backend.modules.bill.dto;

import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class StatisticsQuery {

    private Long bookId;
    private String mode = "month";
    private String month;
    private String year;
    private Boolean includeChildren = false;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) // yyyy-MM-dd
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
