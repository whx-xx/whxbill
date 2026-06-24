package com.whxbill.backend.modules.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.modules.admin.dto.AdminStatusUpdateRequest;
import com.whxbill.backend.modules.admin.dto.AdminRoleSaveRequest;
import com.whxbill.backend.modules.admin.vo.RolePermissionTreeVo;
import com.whxbill.backend.modules.system.annotation.OperationLog;
import com.whxbill.backend.modules.system.entity.SysPermission;
import com.whxbill.backend.modules.system.entity.SysRole;
import com.whxbill.backend.modules.system.entity.SysRolePermission;
import com.whxbill.backend.modules.system.mapper.SysPermissionMapper;
import com.whxbill.backend.modules.system.mapper.SysRoleMapper;
import com.whxbill.backend.modules.system.mapper.SysRolePermissionMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRoleController {

    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('admin:role:list')")
    public ApiResponse<List<SysRole>> roles() {
        return ApiResponse.success(sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByDesc(SysRole::getId)));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('admin:permission:list')")
    public ApiResponse<List<SysPermission>> permissions() {
        return ApiResponse.success(sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSortOrder)));
    }

    @GetMapping("/role-permissions")
    @PreAuthorize("hasAuthority('admin:role:list')")
    public ApiResponse<List<RolePermissionTreeVo>> rolePermissions() {
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByDesc(SysRole::getId));
        List<SysRolePermission> relations = sysRolePermissionMapper.selectList(new LambdaQueryWrapper<>());
        List<SysPermission> permissions = sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSortOrder));

        return ApiResponse.success(roles.stream().map(role -> RolePermissionTreeVo.builder()
            .roleId(role.getId())
            .roleCode(role.getRoleCode())
            .roleName(role.getRoleName())
            .permissions(relations.stream()
                .filter(relation -> relation.getRoleId().equals(role.getId()))
                .map(relation -> permissions.stream()
                    .filter(permission -> permission.getId().equals(relation.getPermissionId()))
                    .findFirst()
                    .map(permission -> RolePermissionTreeVo.PermissionItem.builder()
                        .id(permission.getId())
                        .permissionCode(permission.getPermissionCode())
                        .permissionName(permission.getPermissionName())
                        .build())
                    .orElse(null))
                .filter(Objects::nonNull)
                .toList())
            .build()).toList());
    }

    @PostMapping("/roles")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('admin:role:create') or hasAuthority('admin:role:update')")
    @OperationLog(module = "角色", type = "SAVE", value = "保存角色")
    public ApiResponse<SysRole> saveRole(@Valid @RequestBody AdminRoleSaveRequest request) {
        SysRole existing = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleCode, request.getRoleCode())
            .ne(request.getId() != null, SysRole::getId, request.getId())
            .last("limit 1"));
        if (existing != null) {
            throw new BusinessException("角色编码已存在");
        }

        SysRole role = request.getId() == null ? new SysRole() : sysRoleMapper.selectById(request.getId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus());
        if (request.getId() == null) {
            sysRoleMapper.insert(role);
        } else {
            sysRoleMapper.updateById(role);
        }

        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, role.getId()));
        if (request.getPermissionIds() != null) {
            request.getPermissionIds().stream().distinct().forEach(permissionId -> {
                SysRolePermission relation = new SysRolePermission();
                relation.setRoleId(role.getId());
                relation.setPermissionId(permissionId);
                sysRolePermissionMapper.insert(relation);
            });
        }
        return ApiResponse.success(role);
    }

    @PutMapping("/roles/{roleId}")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('admin:role:update')")
    @OperationLog(module = "角色", type = "UPDATE", value = "修改角色")
    public ApiResponse<SysRole> updateRole(@PathVariable Long roleId, @Valid @RequestBody AdminRoleSaveRequest request) {
        request.setId(roleId);
        return saveRole(request);
    }

    @PutMapping("/roles/{roleId}/status")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('admin:role:update')")
    @OperationLog(module = "角色", type = "STATUS", value = "修改角色状态")
    public ApiResponse<SysRole> updateRoleStatus(
        @PathVariable Long roleId,
        @Valid @RequestBody AdminStatusUpdateRequest request
    ) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        role.setStatus(request.getStatus());
        sysRoleMapper.updateById(role);
        return ApiResponse.success(role);
    }

    @DeleteMapping("/roles/{roleId}")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('admin:role:delete')")
    @OperationLog(module = "角色", type = "DELETE", value = "删除角色")
    public ApiResponse<Boolean> deleteRole(@PathVariable Long roleId) {
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        sysRoleMapper.deleteById(roleId);
        return ApiResponse.success(Boolean.TRUE);
    }
}
