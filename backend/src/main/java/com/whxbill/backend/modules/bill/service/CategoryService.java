package com.whxbill.backend.modules.bill.service;

import com.whxbill.backend.modules.bill.dto.CategorySaveRequest;
import com.whxbill.backend.modules.bill.entity.BizCategory;
import java.util.List;

public interface CategoryService {

    List<BizCategory> listCategories(Long bookId, String categoryType);

    BizCategory saveCategory(CategorySaveRequest request);

    void deleteCategory(Long categoryId);

    void deleteCategories(List<Long> categoryIds);
}
