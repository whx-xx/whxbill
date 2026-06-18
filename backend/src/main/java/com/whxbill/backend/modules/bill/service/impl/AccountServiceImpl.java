package com.whxbill.backend.modules.bill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.modules.bill.dto.AccountSaveRequest;
import com.whxbill.backend.modules.bill.entity.BizAccount;
import com.whxbill.backend.modules.bill.entity.BizBill;
import com.whxbill.backend.modules.bill.entity.BizBook;
import com.whxbill.backend.modules.bill.mapper.BizAccountMapper;
import com.whxbill.backend.modules.bill.mapper.BizBillMapper;
import com.whxbill.backend.modules.bill.mapper.BizBookMapper;
import com.whxbill.backend.modules.bill.service.AccountService;
import com.whxbill.backend.security.SecurityUtils;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final BizAccountMapper bizAccountMapper;
    private final BizBillMapper bizBillMapper;
    private final BizBookMapper bizBookMapper;

    @Override
    public List<BizAccount> listAccounts(Long bookId) {
        return bizAccountMapper.selectList(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getUserId, SecurityUtils.getUserId())
            .eq(BizAccount::getBookId, bookId)
            .orderByAsc(BizAccount::getSortOrder)
            .orderByDesc(BizAccount::getId));
    }

    @Override
    public BizAccount saveAccount(AccountSaveRequest request) {
        Long userId = SecurityUtils.getUserId();
        BizBook book = bizBookMapper.selectOne(new LambdaQueryWrapper<BizBook>()
            .eq(BizBook::getId, request.getBookId())
            .eq(BizBook::getUserId, userId));
        if (book == null) {
            throw new BusinessException("账本不存在");
        }
        BizAccount account = request.getId() == null ? new BizAccount() : bizAccountMapper.selectOne(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getId, request.getId())
            .eq(BizAccount::getUserId, userId));
        if (account == null) {
            throw new BusinessException("账户不存在");
        }
        boolean hasBills = false;
        if (request.getId() != null) {
            Long billCount = bizBillMapper.selectCount(new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getUserId, userId)
                .eq(BizBill::getAccountId, request.getId()));
            hasBills = billCount != null && billCount > 0;
            if (hasBills && !account.getBookId().equals(request.getBookId())) {
                throw new BusinessException("账户已有账单引用，不能切换账本");
            }
        }
        account.setUserId(SecurityUtils.getUserId());
        account.setBookId(request.getBookId());
        account.setAccountName(request.getAccountName());
        account.setAccountType(request.getAccountType());
        if (request.getId() == null) {
            account.setBalance(request.getBalance() == null ? BigDecimal.ZERO : request.getBalance());
        } else if (request.getBalance() != null) {
            account.setBalance(request.getBalance());
        }
        account.setColorTag(request.getColorTag());
        account.setSortOrder(request.getSortOrder());
        if (request.getId() == null) {
            bizAccountMapper.insert(account);
        } else {
            bizAccountMapper.updateById(account);
        }
        return account;
    }

    @Override
    public void deleteAccount(Long accountId) {
        Long userId = SecurityUtils.getUserId();
        BizAccount account = bizAccountMapper.selectOne(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getId, accountId)
            .eq(BizAccount::getUserId, userId));
        if (account == null) {
            throw new BusinessException("账户不存在");
        }
        Long billCount = bizBillMapper.selectCount(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getUserId, userId)
            .eq(BizBill::getAccountId, accountId));
        if (billCount != null && billCount > 0) {
            throw new BusinessException("该账户已有账单引用，不能删除");
        }
        bizAccountMapper.deleteById(accountId);
    }

    @Override
    public void deleteAccounts(List<Long> accountIds) {
        // 批量删除仍逐条走引用校验，避免绕过“已有账单不可删”的限制。
        accountIds.forEach(this::deleteAccount);
    }
}
