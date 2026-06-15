package com.whxbill.backend.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.modules.system.dto.UserProfileUpdateRequest;
import com.whxbill.backend.modules.system.entity.SysUser;
import com.whxbill.backend.modules.system.mapper.SysUserMapper;
import com.whxbill.backend.modules.system.service.UserService;
import com.whxbill.backend.modules.system.vo.CurrentUserProfile;
import com.whxbill.backend.security.LoginUser;
import com.whxbill.backend.security.SecurityUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;

    @Override
    public CurrentUserProfile getCurrentUserProfile() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        return buildProfile(user, loginUser);
    }

    @Override
    public CurrentUserProfile updateCurrentUserProfile(UserProfileUpdateRequest request) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatarUrl(request.getAvatarUrl());
        sysUserMapper.updateById(user);
        return buildProfile(user, loginUser);
    }

    @Override
    public List<SysUser> listUsers() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId));
    }

    private CurrentUserProfile buildProfile(SysUser user, LoginUser loginUser) {
        return CurrentUserProfile.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .phone(user.getPhone())
            .avatarUrl(user.getAvatarUrl())
            .roles(loginUser.getRoles())
            .permissions(loginUser.getPermissions())
            .build();
    }
}
