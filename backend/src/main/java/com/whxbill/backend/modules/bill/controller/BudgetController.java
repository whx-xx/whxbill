package com.whxbill.backend.modules.bill.controller;

import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.common.web.BatchIdsRequest;
import com.whxbill.backend.modules.bill.dto.BudgetSaveRequest;
import com.whxbill.backend.modules.bill.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    @PreAuthorize("hasAuthority('bill:list')")
    public ApiResponse<?> list(@RequestParam Long bookId, @RequestParam String budgetMonth) {
        return ApiResponse.success(budgetService.listBudgets(bookId, budgetMonth));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<?> save(@Valid @RequestBody BudgetSaveRequest request) {
        return ApiResponse.success(budgetService.saveBudget(request));
    }

    @PutMapping("/{budgetId}")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<?> update(@PathVariable Long budgetId, @Valid @RequestBody BudgetSaveRequest request) {
        request.setId(budgetId);
        return ApiResponse.success(budgetService.saveBudget(request));
    }

    @DeleteMapping("/{budgetId}")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<Void> delete(@PathVariable Long budgetId) {
        budgetService.deleteBudget(budgetId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody BatchIdsRequest request) {
        budgetService.deleteBudgets(request.getIds());
        return ApiResponse.success(null);
    }
}
