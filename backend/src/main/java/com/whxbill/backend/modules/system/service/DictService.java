package com.whxbill.backend.modules.system.service;

import com.whxbill.backend.modules.system.entity.SysDict;
import java.util.List;

public interface DictService {

    List<SysDict> listByType(String dictType);
}
