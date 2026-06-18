package com.whxbill.backend.common.web;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class BatchIdsRequest {

    @NotEmpty(message = "请选择要操作的数据")
    private List<Long> ids;
}
