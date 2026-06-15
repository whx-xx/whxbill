package com.whxbill.backend.modules.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.modules.admin.dto.AdminDictSaveRequest;
import com.whxbill.backend.modules.system.annotation.OperationLog;
import com.whxbill.backend.modules.system.entity.SysDict;
import com.whxbill.backend.modules.system.mapper.SysDictMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dicts")
@RequiredArgsConstructor
public class AdminDictController {

    private final SysDictMapper sysDictMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:dict:list')")
    public ApiResponse<List<SysDict>> list() {
        return ApiResponse.success(sysDictMapper.selectList(new LambdaQueryWrapper<SysDict>().orderByAsc(SysDict::getId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('admin:dict:create') or hasAuthority('admin:dict:update')")
    @OperationLog(module = "字典", type = "SAVE", value = "保存字典")
    public ApiResponse<SysDict> save(@Valid @RequestBody AdminDictSaveRequest request) {
        SysDict dict = request.getId() == null ? new SysDict() : sysDictMapper.selectById(request.getId());
        if (dict == null) {
            dict = new SysDict();
        }
        dict.setDictType(request.getDictType());
        dict.setDictLabel(request.getDictLabel());
        dict.setDictValue(request.getDictValue());
        dict.setSortOrder(request.getSortOrder());
        dict.setStatus(request.getStatus());
        if (request.getId() == null) {
            sysDictMapper.insert(dict);
        } else {
            sysDictMapper.updateById(dict);
        }
        return ApiResponse.success(dict);
    }

    @DeleteMapping("/{dictId}")
    @PreAuthorize("hasAuthority('admin:dict:delete')")
    @OperationLog(module = "字典", type = "DELETE", value = "删除字典")
    public ApiResponse<Boolean> delete(@PathVariable Long dictId) {
        sysDictMapper.deleteById(dictId);
        return ApiResponse.success(Boolean.TRUE);
    }
}
