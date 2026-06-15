package com.whxbill.backend.modules.bill.controller;

import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.modules.bill.dto.AccountSaveRequest;
import com.whxbill.backend.modules.bill.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam Long bookId) {
        return ApiResponse.success(accountService.listAccounts(bookId));
    }

    @PostMapping
    public ApiResponse<?> save(@Valid @RequestBody AccountSaveRequest request) {
        return ApiResponse.success(accountService.saveAccount(request));
    }

    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> delete(@PathVariable Long accountId) {
        accountService.deleteAccount(accountId);
        return ApiResponse.success(null);
    }
}
