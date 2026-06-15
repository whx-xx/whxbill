package com.whxbill.backend.modules.system.service;

import com.whxbill.backend.modules.system.dto.NoticeSaveRequest;
import com.whxbill.backend.modules.system.entity.SysNotice;
import java.util.List;

public interface NoticeService {

    List<SysNotice> listPublishedNotices();

    List<SysNotice> listAllNotices();

    SysNotice saveNotice(NoticeSaveRequest request);

    void deleteNotice(Long noticeId);
}
