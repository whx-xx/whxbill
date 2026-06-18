package com.whxbill.backend.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.api.ResultCode;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.modules.auth.dto.LoginRequest;
import com.whxbill.backend.modules.auth.dto.RefreshTokenRequest;
import com.whxbill.backend.modules.auth.dto.RegisterRequest;
import com.whxbill.backend.modules.auth.service.AuthService;
import com.whxbill.backend.modules.auth.vo.LoginResponse;
import com.whxbill.backend.modules.bill.entity.BizBook;
import com.whxbill.backend.modules.bill.entity.BizCategory;
import com.whxbill.backend.modules.bill.mapper.BizBookMapper;
import com.whxbill.backend.modules.bill.mapper.BizCategoryMapper;
import com.whxbill.backend.modules.system.entity.SysRole;
import com.whxbill.backend.modules.system.entity.SysUser;
import com.whxbill.backend.modules.system.entity.SysUserRole;
import com.whxbill.backend.modules.system.mapper.SysRoleMapper;
import com.whxbill.backend.modules.system.mapper.SysUserMapper;
import com.whxbill.backend.modules.system.mapper.SysUserRoleMapper;
import com.whxbill.backend.security.CustomUserDetailsService;
import com.whxbill.backend.security.JwtTokenProvider;
import com.whxbill.backend.security.LoginUser;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_PREFIX = "whx:bill:token:";
    private static final String REFRESH_PREFIX = "whx:bill:refresh:";
    private static final String USER_REFRESH_PREFIX = "whx:bill:user-refresh:";
    private static final String LIMIT_PREFIX = "whx:bill:limit:login:";

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final BizBookMapper bizBookMapper;
    private final BizCategoryMapper bizCategoryMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 先做登录频率限制，再交给 Spring Security 校验用户名和密码。
        checkLoginRateLimit(request.getUsername());
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        LoginUser loginUser = (LoginUser) customUserDetailsService.loadUserByUsername(request.getUsername());
        return issueToken(loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        // 注册时要确认两次密码一致，并且用户名不能重复。
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, request.getUsername())
            .last("limit 1"));
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setUserType(1);
        sysUserMapper.insert(user);

        SysRole userRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleCode, "ROLE_USER").last("limit 1"));
        if (userRole != null) {
            SysUserRole userRoleRelation = new SysUserRole();
            userRoleRelation.setUserId(user.getId());
            userRoleRelation.setRoleId(userRole.getId());
            sysUserRoleMapper.insert(userRoleRelation);
        }

        BizBook defaultBook = new BizBook();
        // 新用户创建后顺手生成默认账本，避免首次登录后还要手动初始化。
        defaultBook.setUserId(user.getId());
        defaultBook.setBookName("默认账本");
        defaultBook.setCurrencyCode("CNY");
        defaultBook.setIsDefault(1);
        bizBookMapper.insert(defaultBook);
        initDefaultCategories(user.getId());

        LoginUser loginUser = customUserDetailsService.buildLoginUser(user);
        return issueToken(loginUser);
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String refreshKey = REFRESH_PREFIX + request.getRefreshToken();
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(refreshKey))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "刷新令牌已失效");
        }
        Claims claims = jwtTokenProvider.parseClaims(request.getRefreshToken());
        if (!"refresh".equals(claims.get("type"))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "刷新令牌无效");
        }
        String username = claims.getSubject();
        stringRedisTemplate.delete(refreshKey);
        stringRedisTemplate.opsForSet().remove(USER_REFRESH_PREFIX + username, request.getRefreshToken());
        LoginUser loginUser = (LoginUser) customUserDetailsService.loadUserByUsername(username);
        return issueToken(loginUser);
    }

    @Override
    public void logout(String accessToken) {
        if (accessToken != null) {
            stringRedisTemplate.delete(TOKEN_PREFIX + accessToken);
            try {
                String username = jwtTokenProvider.parseClaims(accessToken).getSubject();
                Set<String> refreshTokens = stringRedisTemplate.opsForSet().members(USER_REFRESH_PREFIX + username);
                if (refreshTokens != null && !refreshTokens.isEmpty()) {
                    stringRedisTemplate.delete(refreshTokens.stream().map(token -> REFRESH_PREFIX + token).toList());
                }
                stringRedisTemplate.delete(USER_REFRESH_PREFIX + username);
            } catch (Exception ignored) {
            }
        }
    }

    private LoginResponse issueToken(LoginUser loginUser) {
        // 统一签发访问令牌和刷新令牌，并把 token 存到 Redis，方便后续校验和退出登录失效。
        String accessToken = jwtTokenProvider.createAccessToken(loginUser);
        String refreshToken = jwtTokenProvider.createRefreshToken(loginUser);
        stringRedisTemplate.opsForValue().set(TOKEN_PREFIX + accessToken, loginUser.getUsername(), Duration.ofHours(2));
        stringRedisTemplate.opsForValue().set(REFRESH_PREFIX + refreshToken, loginUser.getUsername(), Duration.ofDays(7));
        stringRedisTemplate.opsForSet().add(USER_REFRESH_PREFIX + loginUser.getUsername(), refreshToken);
        stringRedisTemplate.expire(USER_REFRESH_PREFIX + loginUser.getUsername(), Duration.ofDays(7));
        return LoginResponse.builder()
            .userId(loginUser.getUserId())
            .username(loginUser.getUsername())
            .nickname(loginUser.getNickname())
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .roles(loginUser.getRoles())
            .permissions(loginUser.getPermissions())
            .build();
    }

    private void checkLoginRateLimit(String username) {
        // 简单的登录限流，避免短时间内反复尝试密码。
        String key = LIMIT_PREFIX + username;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(1));
        }
        if (count != null && count > 10) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(), "登录过于频繁，请稍后重试");
        }
    }

    private void initDefaultCategories(Long userId) {
        String[][] categories = {
            {"餐饮", "EXPENSE", "Bowl", "10"},
            {"交通", "EXPENSE", "Van", "20"},
            {"购物", "EXPENSE", "ShoppingBag", "30"},
            {"生活缴费", "EXPENSE", "House", "40"},
            {"娱乐", "EXPENSE", "Film", "50"},
            {"医疗", "EXPENSE", "FirstAidKit", "60"},
            {"其他支出", "EXPENSE", "MoreFilled", "90"},
            {"工资", "INCOME", "Money", "10"},
            {"红包转账", "INCOME", "Wallet", "20"},
            {"退款", "INCOME", "RefreshLeft", "30"},
            {"其他收入", "INCOME", "MoreFilled", "90"}
        };
        for (String[] item : categories) {
            BizCategory category = new BizCategory();
            category.setUserId(userId);
            category.setBookId(0L);
            category.setParentId(0L);
            category.setCategoryName(item[0]);
            category.setCategoryType(item[1]);
            category.setIcon(item[2]);
            category.setLevel(1);
            category.setSortOrder(Integer.valueOf(item[3]));
            bizCategoryMapper.insert(category);
        }
    }
}
