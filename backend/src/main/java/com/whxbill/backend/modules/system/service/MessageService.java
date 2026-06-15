package com.whxbill.backend.modules.system.service;

import com.whxbill.backend.modules.system.entity.SysMessage;
import java.util.List;

public interface MessageService {

    List<SysMessage> listCurrentUserMessages();

    void markRead(Long messageId);
}
