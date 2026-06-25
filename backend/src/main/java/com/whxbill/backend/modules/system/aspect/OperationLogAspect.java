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
    private final ObjectMapper objectMapper; // 用 ObjectMapper 把参数转成 JSON 字符串

    /**
     * AOP 后置通知：目标方法成功返回后自动保存一条操作日志。
     */
    @AfterReturning(value = "@annotation(operationLog)", returning = "result")
    public void saveLog(JoinPoint joinPoint, OperationLog operationLog, Object result) {
        try {
            // 只有标注 @OperationLog 且正常返回的方法才会记录，避免把失败操作误记为成功。
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
            // 最终落到 sys_operation_log 表，后台日志管理页就是查这张表。
            sysOperationLogMapper.insert(log);
        } catch (Exception ignored) {
            // 日志记录失败不能影响主业务，比如保存账单成功时不能因为日志失败而回滚。
        }
    }

    /**
     * 拼接日志内容：注解描述 + URL 参数 + 方法入参。
     */
    private String buildOperationContent(JoinPoint joinPoint, OperationLog operationLog) {
        StringBuilder content = new StringBuilder(operationLog.value());
        // 操作描述、URL 查询参数、请求体参数会拼成一段审计内容，展示在后台操作日志里。
        String queryParams = serializeQueryParams();
        if (!queryParams.isBlank()) {
            content.append("；查询参数：").append(queryParams);
        }
        String arguments = serializeArguments(joinPoint);
        if (!arguments.isBlank()) {
            content.append("；请求数据：").append(arguments);
        }
        return truncate(content.toString());// truncate限制长度
    }

    /**
     * 序列化 query string 参数，例如 ?moduleName=用户&type=DELETE。
     */
    private String serializeQueryParams() {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap.isEmpty()) {
            return "";
        }
        try {
            // 先转成 JsonNode，方便统一递归脱敏。
            JsonNode node = objectMapper.valueToTree(parameterMap);
            sanitizeNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * 序列化 Controller 方法参数，例如 @RequestBody 中的 JSON 对象。
     */
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
            // 能拿到真实参数名就用真实参数名，否则使用 arg0、arg1 兜底。
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
            // 最后再转回字符串存入 operation_content。
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return "";
        }
    }

    // 跳过不能序列化的参数， 这些对象不适合直接转 JSON
    private boolean shouldSkipArgument(Object arg) {
        return arg == null
            || arg instanceof ServletRequest
            || arg instanceof ServletResponse
            || arg instanceof MultipartFile
            || arg instanceof MultipartFile[];
    }

    /**
     * 递归遍历 JSON 节点，把敏感字段替换成 ***。
     */
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
                    // 密码、token、secret 等敏感字段只保留掩码，防止日志泄露关键信息。
                    objectNode.put(fieldName, "***");
                } else {
                    sanitizeNode(objectNode.get(fieldName));
                }
            });
        } else if (node.isArray()) {
            // 数组里的每个元素也可能包含 password/token，所以继续递归。
            node.forEach(this::sanitizeNode);
        }
    }

    /**
     * 判断字段名是否包含敏感关键词，包含就需要脱敏。
     */
    private boolean isSensitiveField(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase();
        return SENSITIVE_FIELDS.stream().anyMatch(normalized::contains);
    }

    /**
     * 控制日志内容长度，避免请求体太大导致日志表字段超长。
     */
    private String truncate(String value) {
        if (value == null || value.length() <= MAX_CONTENT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CONTENT_LENGTH - 3) + "...";
    }
}
