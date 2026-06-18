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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dicts")
@RequiredArgsConstructor
public class AdminDictController {

    private final SysDictMapper sysDictMapper;
    private final StringRedisTemplate stringRedisTemplate;

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
        dict.setDictExtra(request.getDictExtra());
        dict.setSortOrder(request.getSortOrder());
        dict.setStatus(request.getStatus());
        if (request.getId() == null) {
            sysDictMapper.insert(dict);
        } else {
            sysDictMapper.updateById(dict);
        }
        evictDictCache(dict.getDictType());
        return ApiResponse.success(dict);
    }

    @PutMapping("/{dictId}")
    @PreAuthorize("hasAuthority('admin:dict:update')")
    @OperationLog(module = "字典", type = "UPDATE", value = "修改字典")
    public ApiResponse<SysDict> update(@PathVariable Long dictId, @Valid @RequestBody AdminDictSaveRequest request) {
        request.setId(dictId);
        return save(request);
    }

    @DeleteMapping("/{dictId}")
    @PreAuthorize("hasAuthority('admin:dict:delete')")
    @OperationLog(module = "字典", type = "DELETE", value = "删除字典")
    public ApiResponse<Boolean> delete(@PathVariable Long dictId) {
        SysDict dict = sysDictMapper.selectById(dictId);
        sysDictMapper.deleteById(dictId);
        if (dict != null) {
            evictDictCache(dict.getDictType());
        }
        return ApiResponse.success(Boolean.TRUE);
    }

    private void evictDictCache(String dictType) {
        if (dictType != null && !dictType.isBlank()) {
            stringRedisTemplate.delete("whx:bill:dict:" + dictType);
        }
    }
}
