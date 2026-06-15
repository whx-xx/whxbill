package com.whxbill.backend.modules.bill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.modules.bill.dto.BookSaveRequest;
import com.whxbill.backend.modules.bill.entity.BizAccount;
import com.whxbill.backend.modules.bill.entity.BizBook;
import com.whxbill.backend.modules.bill.entity.BizBudget;
import com.whxbill.backend.modules.bill.entity.BizBill;
import com.whxbill.backend.modules.bill.mapper.BizAccountMapper;
import com.whxbill.backend.modules.bill.mapper.BizBillMapper;
import com.whxbill.backend.modules.bill.mapper.BizBookMapper;
import com.whxbill.backend.modules.bill.mapper.BizBudgetMapper;
import com.whxbill.backend.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BizBookMapper bizBookMapper;
    private final BizBillMapper bizBillMapper;
    private final BizAccountMapper bizAccountMapper;
    private final BizBudgetMapper bizBudgetMapper;

    @GetMapping
    public ApiResponse<List<BizBook>> list() {
        return ApiResponse.success(bizBookMapper.selectList(new LambdaQueryWrapper<BizBook>()
            .eq(BizBook::getUserId, SecurityUtils.getUserId())
            .orderByDesc(BizBook::getIsDefault)
            .orderByDesc(BizBook::getId)));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<BizBook> save(@Valid @RequestBody BookSaveRequest request) {
        Long userId = SecurityUtils.getUserId();
        BizBook book = request.getId() == null ? new BizBook() : bizBookMapper.selectOne(
            new LambdaQueryWrapper<BizBook>()
                .eq(BizBook::getId, request.getId())
                .eq(BizBook::getUserId, userId));
        if (book == null) {
            throw new BusinessException("账本不存在");
        }
        if (Integer.valueOf(1).equals(request.getIsDefault())) {
            List<BizBook> books = bizBookMapper.selectList(new LambdaQueryWrapper<BizBook>()
                .eq(BizBook::getUserId, userId));
            books.forEach(item -> {
                item.setIsDefault(0);
                bizBookMapper.updateById(item);
            });
        }
        book.setUserId(userId);
        book.setBookName(request.getBookName());
        book.setCurrencyCode(request.getCurrencyCode() == null || request.getCurrencyCode().isBlank() ? "CNY" : request.getCurrencyCode());
        book.setIsDefault(request.getIsDefault() == null ? 0 : request.getIsDefault());
        if (request.getId() == null) {
            bizBookMapper.insert(book);
        } else {
            bizBookMapper.updateById(book);
        }
        return ApiResponse.success(book);
    }

    @DeleteMapping("/{bookId}")
    public ApiResponse<Void> delete(@PathVariable Long bookId) {
        Long userId = SecurityUtils.getUserId();
        BizBook book = bizBookMapper.selectOne(new LambdaQueryWrapper<BizBook>()
            .eq(BizBook::getId, bookId)
            .eq(BizBook::getUserId, userId));
        if (book == null) {
            throw new BusinessException("账本不存在");
        }
        Long billCount = bizBillMapper.selectCount(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getUserId, userId)
            .eq(BizBill::getBookId, bookId));
        if (billCount != null && billCount > 0) {
            throw new BusinessException("账本下已有账单，不能删除");
        }
        Long accountCount = bizAccountMapper.selectCount(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getUserId, userId)
            .eq(BizAccount::getBookId, bookId));
        Long budgetCount = bizBudgetMapper.selectCount(new LambdaQueryWrapper<BizBudget>()
            .eq(BizBudget::getUserId, userId)
            .eq(BizBudget::getBookId, bookId));
        if ((accountCount != null && accountCount > 0)
            || (budgetCount != null && budgetCount > 0)) {
            throw new BusinessException("账本下已有账户或预算，不能删除");
        }
        bizBookMapper.deleteById(bookId);
        return ApiResponse.success(null);
    }
}
