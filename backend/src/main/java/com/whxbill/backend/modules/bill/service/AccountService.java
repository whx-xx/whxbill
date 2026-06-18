package com.whxbill.backend.modules.bill.service;

import com.whxbill.backend.modules.bill.dto.AccountSaveRequest;
import com.whxbill.backend.modules.bill.entity.BizAccount;
import java.util.List;

public interface AccountService {

    List<BizAccount> listAccounts(Long bookId);

    BizAccount saveAccount(AccountSaveRequest request);

    void deleteAccount(Long accountId);

    void deleteAccounts(List<Long> accountIds);
}
