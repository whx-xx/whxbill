package com.whxbill.backend.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoticeSaveRequest {

    private Long id;

    @NotBlank(message = "公告标题不能为空")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    private String coverUrl;

    @NotNull(message = "发布状态不能为空")
    private Integer publishStatus;
}
