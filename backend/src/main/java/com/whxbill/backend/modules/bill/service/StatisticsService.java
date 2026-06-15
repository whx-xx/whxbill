package com.whxbill.backend.modules.bill.service;

import com.whxbill.backend.modules.bill.dto.StatisticsQuery;
import java.util.Map;

public interface StatisticsService {

    Map<String, Object> getDashboard(String month);

    Map<String, Object> getDashboard(StatisticsQuery query);
}
