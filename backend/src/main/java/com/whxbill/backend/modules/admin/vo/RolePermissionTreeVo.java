package com.whxbill.backend.modules.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RolePermissionTreeVo {

    private Long roleId;
    private String roleCode;
    private String roleName;
    private List<PermissionItem> permissions;

    @Data
    @Builder
    public static class PermissionItem {
        private Long id;
        private String permissionCode;
        private String permissionName;
    }
}
