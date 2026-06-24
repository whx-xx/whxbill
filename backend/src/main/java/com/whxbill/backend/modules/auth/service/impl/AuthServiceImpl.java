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
        checkUserEnabled(request.getUsername());
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

    private void checkUserEnabled(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username)
            .last("limit 1"));
        if (user != null && !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
    }

    private void initDefaultCategories(Long userId) {
        List<DefaultCategoryGroup> groups = List.of(
            new DefaultCategoryGroup("健康医疗", "FirstAidKit", List.of("其他", "买药", "医院", "滋补保健")),
            new DefaultCategoryGroup("送礼人情", "Present", List.of("其他", "打赏", "红包", "借出", "礼物", "孝敬长辈")),
            new DefaultCategoryGroup("文化教育", "Reading", List.of("其他", "培训考试", "书报杂志", "学费")),
            new DefaultCategoryGroup("居家生活", "House", List.of("其他", "家政清洁", "车位费", "房租还贷", "物业费", "燃气费", "水费", "电费", "话费宽带")),
            new DefaultCategoryGroup("休闲娱乐", "Film", List.of("其他", "演出", "酒吧", "棋牌桌游", "足浴按摩", "运动健身", "电影唱歌", "旅游度假")),
            new DefaultCategoryGroup("出行交通", "Van", List.of("其他", "保养修车", "飞机", "火车", "加油", "停车费", "公共交通", "打车")),
            new DefaultCategoryGroup("食品餐饮", "KnifeFork", List.of("其他", "粮油调味", "请客吃饭", "生鲜食品", "休闲零食", "饮料酒水", "晚餐", "午餐", "早餐")),
            new DefaultCategoryGroup("购物消费", "ShoppingBag", List.of("其他", "装修装饰", "办公用品", "宠物用品", "服饰运动", "母婴玩具", "配饰腕表", "生活电器", "虚拟充值", "手机数码", "个护美妆", "日常家居")),
            new DefaultCategoryGroup("其他", "Help", List.of("其他", "慈善捐助", "理财支出", "罚款赔偿"))
        );
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            DefaultCategoryGroup group = groups.get(groupIndex);
            BizCategory parent = buildCategory(userId, 0L, group.name(), group.icon(), 1, (groupIndex + 1) * 10);
            bizCategoryMapper.insert(parent);
            List<String> children = group.children();
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                BizCategory child = buildCategory(
                    userId,
                    parent.getId(),
                    children.get(childIndex),
                    group.icon(),
                    2,
                    (childIndex + 1) * 10
                );
                bizCategoryMapper.insert(child);
            }
        }
        List<DefaultIncomeCategory> incomeCategories = List.of(
            new DefaultIncomeCategory("工资", "Money"),
            new DefaultIncomeCategory("奖金", "Coin"),
            new DefaultIncomeCategory("报销", "Document"),
            new DefaultIncomeCategory("投资收益", "Wallet"),
            new DefaultIncomeCategory("其他收入", "Help")
        );
        for (int index = 0; index < incomeCategories.size(); index++) {
            DefaultIncomeCategory item = incomeCategories.get(index);
            BizCategory category = buildCategory(userId, 0L, item.name(), item.icon(), 1, (index + 1) * 10);
            category.setCategoryType("INCOME");
            bizCategoryMapper.insert(category);
        }
    }

    private BizCategory buildCategory(Long userId, Long parentId, String name, String icon, Integer level, Integer sortOrder) {
        BizCategory category = new BizCategory();
        category.setUserId(userId);
        category.setBookId(0L);
        category.setParentId(parentId);
        category.setCategoryName(name);
        category.setCategoryType("EXPENSE");
        category.setIcon(icon);
        category.setLevel(level);
        category.setSortOrder(sortOrder);
        return category;
    }

    private record DefaultCategoryGroup(String name, String icon, List<String> children) {
    }

    private record DefaultIncomeCategory(String name, String icon) {
    }
}
