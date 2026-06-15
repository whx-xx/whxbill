package com.whxbill.backend.modules.system.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurrentUserProfile {

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatarUrl;
    private List<String> roles;
    private List<String> permissions;
}
