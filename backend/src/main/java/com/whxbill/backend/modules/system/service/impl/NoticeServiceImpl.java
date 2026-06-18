package com.whxbill.backend.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.modules.system.dto.NoticeSaveRequest;
import com.whxbill.backend.modules.system.entity.SysMessage;
import com.whxbill.backend.modules.system.entity.SysNotice;
import com.whxbill.backend.modules.system.entity.SysUser;
import com.whxbill.backend.modules.system.mapper.SysMessageMapper;
import com.whxbill.backend.modules.system.mapper.SysNoticeMapper;
import com.whxbill.backend.modules.system.mapper.SysUserMapper;
import com.whxbill.backend.modules.system.service.NoticeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private static final String NOTICE_MESSAGE_TYPE = "NOTICE";
    private static final int PUBLISHED = 1;
    private static final int ENABLED_STATUS = 1;

    private final SysNoticeMapper sysNoticeMapper;
    private final SysUserMapper sysUserMapper;
    private final SysMessageMapper sysMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public List<SysNotice> listPublishedNotices() {
        return sysNoticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getPublishStatus, 1)
            .orderByDesc(SysNotice::getId));
    }

    @Override
    public List<SysNotice> listAllNotices() {
        return sysNoticeMapper.selectList(new LambdaQueryWrapper<SysNotice>().orderByDesc(SysNotice::getId));
    }

    @Override
    public SysNotice saveNotice(NoticeSaveRequest request) {
        SysNotice notice = request.getId() == null ? new SysNotice() : sysNoticeMapper.selectById(request.getId());
        String previousMessageTitle = notice == null ? null : messageTitle(notice.getTitle());
        boolean shouldNotify = request.getPublishStatus() == PUBLISHED;
        if (notice == null) {
            notice = new SysNotice();
        }
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setCoverUrl(request.getCoverUrl());
        notice.setPublishStatus(request.getPublishStatus());
        if (request.getId() == null) {
            sysNoticeMapper.insert(notice);
        } else {
            sysNoticeMapper.updateById(notice);
        }
        if (shouldNotify) {
            syncPublishedNoticeMessages(notice, previousMessageTitle);
        } else {
            removeNoticeMessages(previousMessageTitle, messageTitle(notice.getTitle()));
        }
        return notice;
    }

    @Override
    public void deleteNotice(Long noticeId) {
        SysNotice notice = sysNoticeMapper.selectById(noticeId);
        sysNoticeMapper.deleteById(noticeId);
        if (notice != null) {
            removeNoticeMessages(messageTitle(notice.getTitle()));
        }
    }

    private void syncPublishedNoticeMessages(SysNotice notice, String previousMessageTitle) {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getStatus, ENABLED_STATUS));
        users.forEach(user -> {
            SysMessage message = findNoticeMessage(user.getId(), previousMessageTitle, messageTitle(notice.getTitle()));
            boolean created = message == null;
            if (created) {
                message = new SysMessage();
                message.setUserId(user.getId());
                message.setMessageType(NOTICE_MESSAGE_TYPE);
                message.setReadStatus(0);
            }
            message.setTitle(messageTitle(notice.getTitle()));
            message.setContent(toPlainText(notice.getContent()));
            if (created) {
                sysMessageMapper.insert(message);
                messagingTemplate.convertAndSendToUser(String.valueOf(user.getId()), "/queue/notifications", message);
                messagingTemplate.convertAndSend("/topic/notifications/" + user.getId(), message);
            } else {
                sysMessageMapper.updateById(message);
            }
        });
    }

    private SysMessage findNoticeMessage(Long userId, String previousTitle, String currentTitle) {
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getUserId, userId)
            .eq(SysMessage::getMessageType, NOTICE_MESSAGE_TYPE);
        if (previousTitle != null && !previousTitle.equals(currentTitle)) {
            wrapper.and(query -> query.eq(SysMessage::getTitle, previousTitle).or().eq(SysMessage::getTitle, currentTitle));
        } else {
            wrapper.eq(SysMessage::getTitle, currentTitle);
        }
        wrapper.last("limit 1");
        return sysMessageMapper.selectOne(wrapper);
    }

    private void removeNoticeMessages(String... titles) {
        if (titles == null || titles.length == 0) {
            return;
        }
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getMessageType, NOTICE_MESSAGE_TYPE);
        wrapper.and(query -> {
            boolean hasTitle = false;
            for (String title : titles) {
                if (title != null && !title.isBlank()) {
                    if (hasTitle) {
                        query.or();
                    }
                    query.eq(SysMessage::getTitle, title);
                    hasTitle = true;
                }
            }
        });
        sysMessageMapper.delete(wrapper);
    }

    private String messageTitle(String noticeTitle) {
        return "新公告：" + noticeTitle;
    }

    private String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "管理员发布了一条新公告，请前往消息中心查看。";
        }
        String text = html.replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replaceAll("\\s+", " ")
            .trim();
        if (text.isBlank()) {
            return "管理员发布了一条新公告，请前往消息中心查看。";
        }
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }
}
