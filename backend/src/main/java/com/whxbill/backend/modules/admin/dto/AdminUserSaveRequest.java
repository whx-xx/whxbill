package com.whxbill.backend.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Data;

@Data
public class AdminUserSaveRequest {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "昵称不能为空")
    private String nickname;

    @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*\\d).{8,20}$", message = "密码需为8到20位并同时包含字母和数字")
    private String password;

    private String confirmPassword;

    private String email;

    private String phone;

    private String avatarUrl;

    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotNull(message = "用户类型不能为空")
    private Integer userType;

    private List<Long> roleIds;
}
