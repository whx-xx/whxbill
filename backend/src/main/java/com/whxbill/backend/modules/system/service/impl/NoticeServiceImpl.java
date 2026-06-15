package com.whxbill.backend.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.whxbill.backend.modules.system.dto.NoticeSaveRequest;
import com.whxbill.backend.modules.system.entity.SysNotice;
import com.whxbill.backend.modules.system.mapper.SysNoticeMapper;
import com.whxbill.backend.modules.system.service.NoticeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final SysNoticeMapper sysNoticeMapper;

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
        return notice;
    }

    @Override
    public void deleteNotice(Long noticeId) {
        sysNoticeMapper.deleteById(noticeId);
    }
}
