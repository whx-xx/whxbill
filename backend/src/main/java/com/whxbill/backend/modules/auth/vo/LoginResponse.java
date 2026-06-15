package com.whxbill.backend.modules.auth.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String accessToken;
    private String refreshToken;
    private List<String> roles;
    private List<String> permissions;
}
