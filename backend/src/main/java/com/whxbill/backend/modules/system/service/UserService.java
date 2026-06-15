package com.whxbill.backend.modules.system.service;

import com.whxbill.backend.modules.system.dto.UserProfileUpdateRequest;
import com.whxbill.backend.modules.system.entity.SysUser;
import com.whxbill.backend.modules.system.vo.CurrentUserProfile;
import java.util.List;

public interface UserService {

    CurrentUserProfile getCurrentUserProfile();

    CurrentUserProfile updateCurrentUserProfile(UserProfileUpdateRequest request);

    List<SysUser> listUsers();
}
