package com.whxbill.backend.modules.admin.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.modules.admin.dto.AdminUserSaveRequest;
import com.whxbill.backend.modules.admin.vo.AdminUserVo;
import com.whxbill.backend.modules.system.annotation.OperationLog;
import com.whxbill.backend.modules.system.entity.SysRole;
import com.whxbill.backend.modules.system.entity.SysUser;
import com.whxbill.backend.modules.system.entity.SysUserRole;
import com.whxbill.backend.modules.system.mapper.SysRoleMapper;
import com.whxbill.backend.modules.system.mapper.SysUserMapper;
import com.whxbill.backend.modules.system.mapper.SysUserRoleMapper;
import com.whxbill.backend.modules.system.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:user:list')")
    public ApiResponse<List<AdminUserVo>> list() {
        List<SysUser> users = userService.listUsers();
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>());
        Map<Long, SysRole> roleMap = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>())
            .stream()
            .collect(Collectors.toMap(SysRole::getId, item -> item));
        return ApiResponse.success(users.stream().map(user -> {
            List<Long> roleIds = userRoles.stream()
                .filter(item -> Objects.equals(item.getUserId(), user.getId()))
                .map(SysUserRole::getRoleId)
                .toList();
            List<String> roleNames = roleIds.stream()
                .map(roleMap::get)
                .filter(Objects::nonNull)
                .map(SysRole::getRoleName)
                .toList();
            return AdminUserVo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .userType(user.getUserType())
                .roleIds(roleIds)
                .roleNames(roleNames)
                .createdTime(user.getCreatedTime())
                .build();
        }).toList());
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('admin:user:create') or hasAuthority('admin:user:update')")
    @OperationLog(module = "用户", type = "SAVE", value = "保存用户")
    public ApiResponse<AdminUserVo> save(@Valid @RequestBody AdminUserSaveRequest request) {
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, request.getUsername())
            .ne(request.getId() != null, SysUser::getId, request.getId())
            .last("limit 1"));
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }
        validatePassword(request);

        SysUser user = request.getId() == null ? new SysUser() : sysUserMapper.selectById(request.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setStatus(request.getStatus());
        user.setUserType(request.getUserType());
        if (request.getId() == null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            sysUserMapper.insert(user);
        } else {
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
            }
            sysUserMapper.updateById(user);
        }

        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()));
        if (request.getRoleIds() != null) {
            request.getRoleIds().stream().distinct().forEach(roleId -> {
                SysUserRole relation = new SysUserRole();
                relation.setUserId(user.getId());
                relation.setRoleId(roleId);
                sysUserRoleMapper.insert(relation);
            });
        }
        return ApiResponse.success(buildUserVo(user));
    }

    @PutMapping("/{userId}")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('admin:user:update')")
    @OperationLog(module = "用户", type = "UPDATE", value = "修改用户")
    public ApiResponse<AdminUserVo> update(@PathVariable Long userId, @Valid @RequestBody AdminUserSaveRequest request) {
        request.setId(userId);
        return save(request);
    }

    @DeleteMapping("/{userId}")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('admin:user:delete')")
    @OperationLog(module = "用户", type = "DELETE", value = "删除用户")
    public ApiResponse<Boolean> delete(@PathVariable Long userId) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        sysUserMapper.deleteById(userId);
        return ApiResponse.success(Boolean.TRUE);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('admin:user:export')")
    @OperationLog(module = "用户", type = "EXPORT", value = "导出用户列表")
    public void export(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
            "attachment;filename*=utf-8''" + URLEncoder.encode("users.xlsx", StandardCharsets.UTF_8));
        EasyExcel.write(response.getOutputStream(), SysUser.class)
            .sheet("users")
            .doWrite(userService.listUsers());
    }

    private AdminUserVo buildUserVo(SysUser user) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()));
        Map<Long, SysRole> roleMap = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>())
            .stream()
            .collect(Collectors.toMap(SysRole::getId, item -> item));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return AdminUserVo.builder()
            .id(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .phone(user.getPhone())
            .avatarUrl(user.getAvatarUrl())
            .status(user.getStatus())
            .userType(user.getUserType())
            .roleIds(roleIds)
            .roleNames(roleIds.stream().map(roleMap::get).filter(Objects::nonNull).map(SysRole::getRoleName).toList())
            .createdTime(user.getCreatedTime())
            .build();
    }

    private void validatePassword(AdminUserSaveRequest request) {
        String password = request.getPassword();
        String confirmPassword = request.getConfirmPassword();
        boolean passwordBlank = password == null || password.isBlank();
        boolean confirmBlank = confirmPassword == null || confirmPassword.isBlank();
        if (request.getId() == null && passwordBlank) {
            throw new BusinessException("初始密码不能为空");
        }
        if (passwordBlank && confirmBlank) {
            return;
        }
        if (passwordBlank || confirmBlank) {
            throw new BusinessException("请完整填写密码和确认密码");
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessException("两次输入的密码不一致");
        }
    }
}
