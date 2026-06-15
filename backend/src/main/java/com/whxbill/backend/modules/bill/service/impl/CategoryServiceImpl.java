package com.whxbill.backend.modules.bill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.modules.bill.dto.CategorySaveRequest;
import com.whxbill.backend.modules.bill.entity.BizBill;
import com.whxbill.backend.modules.bill.entity.BizBook;
import com.whxbill.backend.modules.bill.entity.BizBudget;
import com.whxbill.backend.modules.bill.entity.BizCategory;
import com.whxbill.backend.modules.bill.mapper.BizBillMapper;
import com.whxbill.backend.modules.bill.mapper.BizBookMapper;
import com.whxbill.backend.modules.bill.mapper.BizBudgetMapper;
import com.whxbill.backend.modules.bill.mapper.BizCategoryMapper;
import com.whxbill.backend.modules.bill.service.CategoryService;
import com.whxbill.backend.security.SecurityUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final BizCategoryMapper bizCategoryMapper;
    private final BizBillMapper bizBillMapper;
    private final BizBookMapper bizBookMapper;
    private final BizBudgetMapper bizBudgetMapper;
    private static final Long SHARED_CATEGORY_BOOK_ID = 0L;

    @Override
    public List<BizCategory> listCategories(Long bookId, String categoryType) {
        return bizCategoryMapper.selectList(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getUserId, SecurityUtils.getUserId())
            .eq(categoryType != null && !categoryType.isBlank(), BizCategory::getCategoryType, categoryType)
            .orderByAsc(BizCategory::getSortOrder)
            .orderByAsc(BizCategory::getId));
    }

    @Override
    public BizCategory saveCategory(CategorySaveRequest request) {
        Long userId = SecurityUtils.getUserId();
        BizBook book = bizBookMapper.selectOne(new LambdaQueryWrapper<BizBook>()
            .eq(BizBook::getId, request.getBookId())
            .eq(BizBook::getUserId, userId));
        if (book == null) {
            throw new BusinessException("账本不存在");
        }
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        if (parentId > 0) {
            BizCategory parent = bizCategoryMapper.selectOne(new LambdaQueryWrapper<BizCategory>()
                .eq(BizCategory::getId, parentId)
                .eq(BizCategory::getUserId, userId));
            if (parent == null) {
                throw new BusinessException("父级分类不存在");
            }
            if (!parent.getCategoryType().equals(request.getCategoryType())) {
                throw new BusinessException("父级分类类型不一致");
            }
        }
        BizCategory category = request.getId() == null ? new BizCategory() : bizCategoryMapper.selectOne(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getId, request.getId())
            .eq(BizCategory::getUserId, userId));
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        if (request.getLevel() != null && request.getLevel() > 2) {
            throw new BusinessException("当前仅支持一级和二级分类");
        }
        if (parentId > 0 && request.getLevel() != null && request.getLevel() != 2) {
            throw new BusinessException("二级分类必须选择父级分类");
        }
        if (parentId == 0 && request.getLevel() != null && request.getLevel() != 1) {
            throw new BusinessException("一级分类不能选择父级分类");
        }
        if (request.getId() != null) {
            Long childCount = bizCategoryMapper.selectCount(new LambdaQueryWrapper<BizCategory>()
                .eq(BizCategory::getUserId, userId)
                .eq(BizCategory::getParentId, request.getId()));
            Long billCount = bizBillMapper.selectCount(new LambdaQueryWrapper<BizBill>()
                .eq(BizBill::getUserId, userId)
                .eq(BizBill::getCategoryId, request.getId()));
            Long budgetCount = bizBudgetMapper.selectCount(new LambdaQueryWrapper<BizBudget>()
                .eq(BizBudget::getUserId, userId)
                .eq(BizBudget::getCategoryId, request.getId()));
            boolean hasReferences = (childCount != null && childCount > 0)
                || (billCount != null && billCount > 0)
                || (budgetCount != null && budgetCount > 0);
            boolean structuralChanged = !category.getCategoryType().equals(request.getCategoryType())
                || !Long.valueOf(category.getParentId() == null ? 0L : category.getParentId()).equals(parentId);
            if (hasReferences && structuralChanged) {
                throw new BusinessException("分类已有业务数据引用，不能修改类型或父级");
            }
        }
        category.setUserId(SecurityUtils.getUserId());
        category.setBookId(SHARED_CATEGORY_BOOK_ID);
        category.setParentId(parentId);
        category.setCategoryName(request.getCategoryName());
        category.setCategoryType(request.getCategoryType());
        category.setIcon(request.getIcon());
        category.setLevel(request.getLevel());
        category.setSortOrder(request.getSortOrder());
        if (request.getId() == null) {
            bizCategoryMapper.insert(category);
        } else {
            bizCategoryMapper.updateById(category);
        }
        return category;
    }

    @Override
    public void deleteCategory(Long categoryId) {
        Long userId = SecurityUtils.getUserId();
        BizCategory category = bizCategoryMapper.selectOne(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getId, categoryId)
            .eq(BizCategory::getUserId, userId));
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        Long childCount = bizCategoryMapper.selectCount(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getUserId, userId)
            .eq(BizCategory::getParentId, categoryId));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("请先删除该分类下的二级分类");
        }
        Long billCount = bizBillMapper.selectCount(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getUserId, userId)
            .eq(BizBill::getCategoryId, categoryId));
        if (billCount != null && billCount > 0) {
            throw new BusinessException("该分类已有账单引用，不能删除");
        }
        Long budgetCount = bizBudgetMapper.selectCount(new LambdaQueryWrapper<BizBudget>()
            .eq(BizBudget::getUserId, userId)
            .eq(BizBudget::getCategoryId, categoryId));
        if (budgetCount != null && budgetCount > 0) {
            throw new BusinessException("该分类已有预算引用，不能删除");
        }
        bizCategoryMapper.deleteById(categoryId);
    }
}
