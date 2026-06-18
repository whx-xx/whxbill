package com.whxbill.backend.modules.bill.controller;

import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.common.web.BatchIdsRequest;
import com.whxbill.backend.modules.bill.dto.CategorySaveRequest;
import com.whxbill.backend.modules.bill.service.CategoryService;
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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('bill:list')")
    public ApiResponse<?> list(@RequestParam Long bookId, @RequestParam(required = false) String categoryType) {
        return ApiResponse.success(categoryService.listCategories(bookId, categoryType));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<?> save(@Valid @RequestBody CategorySaveRequest request) {
        return ApiResponse.success(categoryService.saveCategory(request));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<?> update(@PathVariable Long categoryId, @Valid @RequestBody CategorySaveRequest request) {
        request.setId(categoryId);
        return ApiResponse.success(categoryService.saveCategory(request));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<Void> delete(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody BatchIdsRequest request) {
        categoryService.deleteCategories(request.getIds());
        return ApiResponse.success(null);
    }
}
