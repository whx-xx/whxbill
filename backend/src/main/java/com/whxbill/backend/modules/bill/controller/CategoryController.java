package com.whxbill.backend.modules.bill.controller;

import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.modules.bill.dto.CategorySaveRequest;
import com.whxbill.backend.modules.bill.service.CategoryService;
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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam Long bookId, @RequestParam(required = false) String categoryType) {
        return ApiResponse.success(categoryService.listCategories(bookId, categoryType));
    }

    @PostMapping
    public ApiResponse<?> save(@Valid @RequestBody CategorySaveRequest request) {
        return ApiResponse.success(categoryService.saveCategory(request));
    }

    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> delete(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ApiResponse.success(null);
    }
}
