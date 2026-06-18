package com.whxbill.backend.modules.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.modules.system.annotation.OperationLog;
import com.whxbill.backend.modules.system.entity.SysOperationLog;
import com.whxbill.backend.modules.system.mapper.SysOperationLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final SysOperationLogMapper sysOperationLogMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:log:list')")
    public ApiResponse<List<SysOperationLog>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String moduleName,
        @RequestParam(required = false) String operationType,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        String search = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
            .eq(moduleName != null && !moduleName.isBlank(), SysOperationLog::getModuleName, moduleName)
            .eq(operationType != null && !operationType.isBlank(), SysOperationLog::getOperationType, operationType)
            .ge(startTime != null, SysOperationLog::getCreatedTime, startTime)
            .le(endTime != null, SysOperationLog::getCreatedTime, endTime)
            .and(!search.isBlank(), query -> query
                .like(SysOperationLog::getOperatorName, search)
                .or().like(SysOperationLog::getModuleName, search)
                .or().like(SysOperationLog::getOperationType, search)
                .or().like(SysOperationLog::getRequestUri, search)
                .or().like(SysOperationLog::getRequestMethod, search)
                .or().like(SysOperationLog::getOperationContent, search)
                .or().like(SysOperationLog::getIpAddress, search))
            .orderByDesc(SysOperationLog::getCreatedTime)
            .orderByDesc(SysOperationLog::getId);
        return ApiResponse.success(sysOperationLogMapper.selectList(wrapper));
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
