package com.whxbill.backend.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.modules.system.entity.SysPermission;
import com.whxbill.backend.modules.system.entity.SysRole;
import com.whxbill.backend.modules.system.entity.SysRolePermission;
import com.whxbill.backend.modules.system.entity.SysUser;
import com.whxbill.backend.modules.system.entity.SysUserRole;
import com.whxbill.backend.modules.system.mapper.SysPermissionMapper;
import com.whxbill.backend.modules.system.mapper.SysRoleMapper;
import com.whxbill.backend.modules.system.mapper.SysRolePermissionMapper;
import com.whxbill.backend.modules.system.mapper.SysUserMapper;
import com.whxbill.backend.modules.system.mapper.SysUserRoleMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username)
            .last("limit 1"));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return buildLoginUser(user);
    }

    public LoginUser buildLoginUser(SysUser user) {
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getId()))
            .stream()
            .map(SysUserRole::getRoleId)
            .toList();

        List<SysRole> roles = roleIds.isEmpty() ? List.of() : sysRoleMapper.selectBatchIds(roleIds);
        List<Long> permissionIds = roleIds.isEmpty() ? List.of() : sysRolePermissionMapper
            .selectList(new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, roleIds))
            .stream()
            .map(SysRolePermission::getPermissionId)
            .distinct()
            .toList();
        List<String> permissions = permissionIds.isEmpty() ? List.of() : sysPermissionMapper.selectBatchIds(permissionIds)
            .stream()
            .map(SysPermission::getPermissionCode)
            .toList();

        return LoginUser.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .password(user.getPassword())
            .roles(roles.stream().map(SysRole::getRoleCode).toList())
            .permissions(permissions)
            .status(user.getStatus())
            .build();
    }
}
