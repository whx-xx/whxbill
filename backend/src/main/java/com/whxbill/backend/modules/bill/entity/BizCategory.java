package com.whxbill.backend.modules.bill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.whxbill.backend.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_category")
public class BizCategory extends BaseEntity {

    private Long userId;
    private Long bookId;
    private Long parentId;
    private String categoryName;
    private String categoryType;
    private String icon;
    private Integer level;
    private Integer sortOrder;
}
