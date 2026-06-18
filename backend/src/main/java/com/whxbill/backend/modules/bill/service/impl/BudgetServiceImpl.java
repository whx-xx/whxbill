package com.whxbill.backend.modules.bill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.modules.bill.dto.BudgetSaveRequest;
import com.whxbill.backend.modules.bill.entity.BizBill;
import com.whxbill.backend.modules.bill.entity.BizBook;
import com.whxbill.backend.modules.bill.entity.BizBudget;
import com.whxbill.backend.modules.bill.entity.BizCategory;
import com.whxbill.backend.modules.bill.mapper.BizBillMapper;
import com.whxbill.backend.modules.bill.mapper.BizBookMapper;
import com.whxbill.backend.modules.bill.mapper.BizBudgetMapper;
import com.whxbill.backend.modules.bill.mapper.BizCategoryMapper;
import com.whxbill.backend.modules.bill.service.BudgetService;
import com.whxbill.backend.security.SecurityUtils;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

    private final BizBudgetMapper bizBudgetMapper;
    private final BizBillMapper bizBillMapper;
    private final BizBookMapper bizBookMapper;
    private final BizCategoryMapper bizCategoryMapper;

    @Override
    public List<BizBudget> listBudgets(Long bookId, String budgetMonth) {
        return bizBudgetMapper.selectList(new LambdaQueryWrapper<BizBudget>()
            .eq(BizBudget::getUserId, SecurityUtils.getUserId())
            .eq(BizBudget::getBookId, bookId)
            .eq(BizBudget::getBudgetMonth, budgetMonth)
            .orderByDesc(BizBudget::getId));
    }

    @Override
    public BizBudget saveBudget(BudgetSaveRequest request) {
        Long userId = SecurityUtils.getUserId();
        BizBook book = bizBookMapper.selectOne(new LambdaQueryWrapper<BizBook>()
            .eq(BizBook::getId, request.getBookId())
            .eq(BizBook::getUserId, userId));
        if (book == null) {
            throw new BusinessException("账本不存在");
        }
        if (request.getCategoryId() != null) {
            BizCategory category = bizCategoryMapper.selectOne(new LambdaQueryWrapper<BizCategory>()
                .eq(BizCategory::getId, request.getCategoryId())
                .eq(BizCategory::getUserId, userId)
                .eq(BizCategory::getCategoryType, "EXPENSE"));
            if (category == null) {
                throw new BusinessException("预算分类不存在或不是支出分类");
            }
        }
        BizBudget budget = request.getId() == null ? new BizBudget() : bizBudgetMapper.selectOne(new LambdaQueryWrapper<BizBudget>()
            .eq(BizBudget::getId, request.getId())
            .eq(BizBudget::getUserId, userId));
        if (budget == null) {
            throw new BusinessException("预算不存在");
        }
        budget.setUserId(SecurityUtils.getUserId());
        budget.setBookId(request.getBookId());
        budget.setCategoryId(request.getCategoryId());
        budget.setBudgetMonth(request.getBudgetMonth());
        budget.setBudgetAmount(request.getBudgetAmount());
        budget.setUsedAmount(calculateUsedAmount(request.getBookId(), request.getCategoryId(), request.getBudgetMonth()));
        if (request.getId() == null) {
            bizBudgetMapper.insert(budget);
        } else {
            bizBudgetMapper.updateById(budget);
        }
        return budget;
    }

    @Override
    public void deleteBudget(Long budgetId) {
        Long userId = SecurityUtils.getUserId();
        BizBudget budget = bizBudgetMapper.selectOne(new LambdaQueryWrapper<BizBudget>()
            .eq(BizBudget::getId, budgetId)
            .eq(BizBudget::getUserId, userId));
        if (budget == null) {
            throw new BusinessException("预算不存在");
        }
        bizBudgetMapper.deleteById(budgetId);
    }

    @Override
    public void deleteBudgets(List<Long> budgetIds) {
        // 逐条删除可继续复用账本归属校验，避免跨用户删除预算。
        budgetIds.forEach(this::deleteBudget);
    }

    private BigDecimal calculateUsedAmount(Long bookId, Long categoryId, String budgetMonth) {
        List<BizBill> bills = bizBillMapper.selectList(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getUserId, SecurityUtils.getUserId())
            .eq(BizBill::getBookId, bookId)
            .eq(BizBill::getBillType, "EXPENSE")
            .like(BizBill::getBillDate, budgetMonth));
        if (categoryId == null) {
            return bills.stream().map(BizBill::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        List<Long> categoryIds = bills.stream().map(BizBill::getCategoryId).distinct().toList();
        List<BizCategory> categories = categoryIds.isEmpty() ? List.of() : bizCategoryMapper.selectList(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getUserId, SecurityUtils.getUserId())
            .in(BizCategory::getId, categoryIds));
        return bills.stream()
            .filter(bill -> categoryId.equals(bill.getCategoryId()) || categories.stream()
                .filter(category -> category.getId().equals(bill.getCategoryId()))
                .anyMatch(category -> categoryId.equals(category.getParentId())))
            .map(BizBill::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
