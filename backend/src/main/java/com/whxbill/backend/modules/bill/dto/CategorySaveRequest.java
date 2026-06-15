package com.whxbill.backend.modules.bill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategorySaveRequest {

    private Long id;
    @NotNull(message = "账本不能为空")
    private Long bookId;
    private Long parentId;
    @NotBlank(message = "分类名称不能为空")
    private String categoryName;
    @NotBlank(message = "分类类型不能为空")
    private String categoryType;
    private String icon;
    private Integer level = 1;
    private Integer sortOrder = 0;
}
