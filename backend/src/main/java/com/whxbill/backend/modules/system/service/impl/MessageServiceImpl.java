package com.whxbill.backend.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.modules.system.entity.SysMessage;
import com.whxbill.backend.modules.system.mapper.SysMessageMapper;
import com.whxbill.backend.modules.system.service.MessageService;
import com.whxbill.backend.security.SecurityUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final SysMessageMapper sysMessageMapper;

    @Override
    public List<SysMessage> listCurrentUserMessages() {
        return sysMessageMapper.selectList(new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getUserId, SecurityUtils.getUserId())
            .orderByAsc(SysMessage::getReadStatus)
            .orderByDesc(SysMessage::getId));
    }

    @Override
    public void markRead(Long messageId) {
        SysMessage message = sysMessageMapper.selectById(messageId);
        if (message == null || !message.getUserId().equals(SecurityUtils.getUserId())) {
            throw new BusinessException("消息不存在");
        }
        message.setReadStatus(1);
        sysMessageMapper.updateById(message);
    }
}
