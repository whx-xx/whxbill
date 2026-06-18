package com.whxbill.backend.modules.system.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whxbill.backend.modules.system.annotation.OperationLog;
import com.whxbill.backend.modules.system.entity.SysOperationLog;
import com.whxbill.backend.modules.system.mapper.SysOperationLogMapper;
import com.whxbill.backend.security.LoginUser;
import com.whxbill.backend.security.SecurityUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final int MAX_CONTENT_LENGTH = 240;
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "confirmPassword", "token", "accessToken", "refreshToken", "authorization", "secret");

    private final SysOperationLogMapper sysOperationLogMapper;
    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;

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
            log.setOperationContent(buildOperationContent(joinPoint, operationLog));
            sysOperationLogMapper.insert(log);
        } catch (Exception ignored) {
        }
    }

    private String buildOperationContent(JoinPoint joinPoint, OperationLog operationLog) {
        StringBuilder content = new StringBuilder(operationLog.value());
        String queryParams = serializeQueryParams();
        if (!queryParams.isBlank()) {
            content.append("；查询参数：").append(queryParams);
        }
        String arguments = serializeArguments(joinPoint);
        if (!arguments.isBlank()) {
            content.append("；请求数据：").append(arguments);
        }
        return truncate(content.toString());
    }

    private String serializeQueryParams() {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap.isEmpty()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.valueToTree(parameterMap);
            sanitizeNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "";
        }
    }

    private String serializeArguments(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "";
        }
        String[] names = joinPoint.getSignature() instanceof CodeSignature signature
            ? signature.getParameterNames()
            : new String[0];
        ObjectNode root = objectMapper.createObjectNode();
        for (int index = 0; index < args.length; index++) {
            Object arg = args[index];
            if (shouldSkipArgument(arg)) {
                continue;
            }
            String name = names.length > index && names[index] != null ? names[index] : "arg" + index;
            try {
                JsonNode node = objectMapper.valueToTree(arg);
                sanitizeNode(node);
                root.set(name, node);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (root.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean shouldSkipArgument(Object arg) {
        return arg == null
            || arg instanceof ServletRequest
            || arg instanceof ServletResponse
            || arg instanceof MultipartFile
            || arg instanceof MultipartFile[];
    }

    private void sanitizeNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.forEach(fieldName -> {
                if (isSensitiveField(fieldName)) {
                    objectNode.put(fieldName, "***");
                } else {
                    sanitizeNode(objectNode.get(fieldName));
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::sanitizeNode);
        }
    }

    private boolean isSensitiveField(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase();
        return SENSITIVE_FIELDS.stream().anyMatch(normalized::contains);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_CONTENT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CONTENT_LENGTH - 3) + "...";
    }
}
