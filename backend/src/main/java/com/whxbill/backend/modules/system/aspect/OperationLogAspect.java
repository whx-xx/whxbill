package com.whxbill.backend.modules.system.aspect;

import com.whxbill.backend.modules.system.annotation.OperationLog;
import com.whxbill.backend.modules.system.entity.SysOperationLog;
import com.whxbill.backend.modules.system.mapper.SysOperationLogMapper;
import com.whxbill.backend.security.LoginUser;
import com.whxbill.backend.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysOperationLogMapper sysOperationLogMapper;
    private final HttpServletRequest request;

    @AfterReturning(value = "@annotation(operationLog)", returning = "result")
    public void saveLog(JoinPoint joinPoint, OperationLog operationLog, Object result) {
        try {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            SysOperationLog log = new SysOperationLog();
            log.setOperatorId(loginUser.getUserId());
            log.setOperatorName(loginUser.getUsername());
            log.setIpAddress(request.getRemoteAddr());
            log.setModuleName(operationLog.module());
            log.setOperationType(operationLog.type());
            log.setRequestUri(request.getRequestURI());
            log.setRequestMethod(request.getMethod());
            log.setOperationContent(operationLog.value());
            sysOperationLogMapper.insert(log);
        } catch (Exception ignored) {
        }
    }
}
