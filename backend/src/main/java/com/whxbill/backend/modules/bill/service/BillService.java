package com.whxbill.backend.modules.bill.service;

import com.whxbill.backend.common.web.PageResult;
import com.whxbill.backend.modules.bill.dto.BillPageQuery;
import com.whxbill.backend.modules.bill.dto.BillExportRow;
import com.whxbill.backend.modules.bill.dto.BillImportPreviewResponse;
import com.whxbill.backend.modules.bill.dto.BillImportRequest;
import com.whxbill.backend.modules.bill.dto.BillSaveRequest;
import com.whxbill.backend.modules.bill.entity.BizBill;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface BillService {

    PageResult<BizBill> pageBills(BillPageQuery pageQuery);

    Map<String, BigDecimal> summaryBills(BillPageQuery pageQuery);

    List<BillExportRow> exportBills(BillPageQuery pageQuery);

    BillImportPreviewResponse previewImport(Long bookId, MultipartFile file);

    Integer importBills(BillImportRequest request);

    BizBill saveBill(BillSaveRequest request);

    void deleteBill(Long billId);

    void deleteBills(List<Long> billIds);

    List<BizBill> listBillsByDate(String date, Long bookId);
}
