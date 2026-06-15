package com.whxbill.backend.modules.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.modules.system.annotation.OperationLog;
import com.whxbill.backend.modules.system.entity.SysOperationLog;
import com.whxbill.backend.modules.system.mapper.SysOperationLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final SysOperationLogMapper sysOperationLogMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:log:list')")
    public ApiResponse<List<SysOperationLog>> list() {
        return ApiResponse.success(sysOperationLogMapper.selectList(
            new LambdaQueryWrapper<SysOperationLog>().orderByDesc(SysOperationLog::getId)));
    }

    @DeleteMapping("/{logId}")
    @PreAuthorize("hasAuthority('admin:log:list')")
    @OperationLog(module = "日志", type = "DELETE", value = "删除操作日志")
    public ApiResponse<Boolean> delete(@PathVariable Long logId) {
        sysOperationLogMapper.deleteById(logId);
        return ApiResponse.success(Boolean.TRUE);
    }

    @DeleteMapping("/clear")
    @PreAuthorize("hasAuthority('admin:log:list')")
    @OperationLog(module = "日志", type = "CLEAR", value = "清空操作日志")
    public ApiResponse<Boolean> clear() {
        sysOperationLogMapper.delete(new LambdaQueryWrapper<>());
        return ApiResponse.success(Boolean.TRUE);
    }
}
