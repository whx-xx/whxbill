package com.whxbill.backend.modules.admin.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserVo {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatarUrl;
    private Integer status;
    private Integer userType;
    private List<Long> roleIds;
    private List<String> roleNames;
    private LocalDateTime createdTime;
}
