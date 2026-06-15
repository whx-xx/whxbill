package com.whxbill.backend.modules.bill.service;

import com.whxbill.backend.modules.bill.dto.BudgetSaveRequest;
import com.whxbill.backend.modules.bill.entity.BizBudget;
import java.util.List;

public interface BudgetService {

    List<BizBudget> listBudgets(Long bookId, String budgetMonth);

    BizBudget saveBudget(BudgetSaveRequest request);

    void deleteBudget(Long budgetId);
}
