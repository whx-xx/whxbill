package com.whxbill.backend.modules.admin.controller;

import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.modules.system.annotation.OperationLog;
import com.whxbill.backend.modules.system.dto.NoticeSaveRequest;
import com.whxbill.backend.modules.system.entity.SysNotice;
import com.whxbill.backend.modules.system.service.NoticeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:notice:list')")
    public ApiResponse<List<SysNotice>> list() {
        return ApiResponse.success(noticeService.listAllNotices());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('admin:notice:create') or hasAuthority('admin:notice:update')")
    @OperationLog(module = "公告", type = "SAVE", value = "保存公告")
    public ApiResponse<SysNotice> save(@Valid @RequestBody NoticeSaveRequest request) {
        return ApiResponse.success(noticeService.saveNotice(request));
    }

    @PutMapping("/{noticeId}")
    @PreAuthorize("hasAuthority('admin:notice:update')")
    @OperationLog(module = "公告", type = "UPDATE", value = "修改公告")
    public ApiResponse<SysNotice> update(@PathVariable Long noticeId, @Valid @RequestBody NoticeSaveRequest request) {
        request.setId(noticeId);
        return ApiResponse.success(noticeService.saveNotice(request));
    }

    @DeleteMapping("/{noticeId}")
    @PreAuthorize("hasAuthority('admin:notice:delete')")
    @OperationLog(module = "公告", type = "DELETE", value = "删除公告")
    public ApiResponse<Boolean> delete(@PathVariable Long noticeId) {
        noticeService.deleteNotice(noticeId);
        return ApiResponse.success(Boolean.TRUE);
    }
}
