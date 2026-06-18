package com.whxbill.backend.modules.system.controller;

import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.modules.system.dto.UserProfileUpdateRequest;
import com.whxbill.backend.modules.system.service.DictService;
import com.whxbill.backend.modules.system.service.MessageService;
import com.whxbill.backend.modules.system.service.NoticeService;
import com.whxbill.backend.modules.system.service.UserService;
import com.whxbill.backend.modules.system.vo.CurrentUserProfile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final NoticeService noticeService;
    private final MessageService messageService;
    private final DictService dictService;

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CurrentUserProfile> profile() {
        return ApiResponse.success(userService.getCurrentUserProfile());
    }

    @PostMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CurrentUserProfile> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success(userService.updateCurrentUserProfile(request));
    }

    @GetMapping("/notices")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> notices() {
        return ApiResponse.success(noticeService.listPublishedNotices());
    }

    @GetMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> messages() {
        return ApiResponse.success(messageService.listCurrentUserMessages());
    }

    @GetMapping("/dicts/{dictType}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> dicts(@PathVariable String dictType) {
        return ApiResponse.success(dictService.listByType(dictType));
    }

    @PostMapping("/messages/{messageId}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> markRead(@PathVariable Long messageId) {
        messageService.markRead(messageId);
        return ApiResponse.success(null);
    }
}
