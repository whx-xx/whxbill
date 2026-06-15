package com.whxbill.backend.modules.auth.service;

import com.whxbill.backend.modules.auth.dto.LoginRequest;
import com.whxbill.backend.modules.auth.dto.RefreshTokenRequest;
import com.whxbill.backend.modules.auth.dto.RegisterRequest;
import com.whxbill.backend.modules.auth.vo.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);

    LoginResponse refreshToken(RefreshTokenRequest request);

    void logout(String accessToken);
}
