package com.whxbill.backend.modules.bill.controller;

import com.alibaba.excel.EasyExcel;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.common.web.BatchIdsRequest;
import com.whxbill.backend.modules.bill.dto.BillExportRow;
import com.whxbill.backend.modules.bill.dto.BillImportRequest;
import com.whxbill.backend.modules.bill.dto.BillPageQuery;
import com.whxbill.backend.modules.bill.dto.BillSaveRequest;
import com.whxbill.backend.modules.bill.service.BillService;
import com.whxbill.backend.modules.system.annotation.OperationLog;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @GetMapping
    @PreAuthorize("hasAuthority('bill:list')")
    public ApiResponse<?> page(BillPageQuery pageQuery) {
        return ApiResponse.success(billService.pageBills(pageQuery));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('bill:list')")
    public ApiResponse<?> summary(BillPageQuery pageQuery) {
        return ApiResponse.success(billService.summaryBills(pageQuery));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('bill:list')")
    @OperationLog(module = "账单", type = "EXPORT", value = "导出账单流水")
    public void export(BillPageQuery pageQuery, HttpServletResponse response) throws Exception {
        List<BillExportRow> rows = billService.exportBills(pageQuery);
        String filename = "账单流水-" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
            "attachment;filename*=utf-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        EasyExcel.write(response.getOutputStream(), BillExportRow.class)
            .sheet("账单流水")
            .doWrite(rows);
    }

    @PostMapping("/import/preview")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<?> previewImport(@RequestParam Long bookId, @RequestParam MultipartFile file) {
        return ApiResponse.success(billService.previewImport(bookId, file));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('bill:create')")
    @OperationLog(module = "账单", type = "IMPORT", value = "导入账单流水")
    public ApiResponse<?> importBills(@Valid @RequestBody BillImportRequest request) {
        return ApiResponse.success(billService.importBills(request));
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('bill:list')")
    public ApiResponse<?> calendar(@RequestParam String date, @RequestParam(required = false) Long bookId) {
        return ApiResponse.success(billService.listBillsByDate(date, bookId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('bill:create')")
    @OperationLog(module = "账单", type = "SAVE", value = "保存账单")
    public ApiResponse<?> save(@Valid @RequestBody BillSaveRequest request) {
        return ApiResponse.success(billService.saveBill(request));
    }

    @PutMapping("/{billId}")
    @PreAuthorize("hasAuthority('bill:create')")
    @OperationLog(module = "账单", type = "UPDATE", value = "修改账单")
    public ApiResponse<?> update(@PathVariable Long billId, @Valid @RequestBody BillSaveRequest request) {
        request.setId(billId);
        return ApiResponse.success(billService.saveBill(request));
    }

    @DeleteMapping("/{billId}")
    @PreAuthorize("hasAuthority('bill:create')")
    @OperationLog(module = "账单", type = "DELETE", value = "删除账单")
    public ApiResponse<Void> delete(@PathVariable Long billId) {
        billService.deleteBill(billId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('bill:create')")
    @OperationLog(module = "账单", type = "DELETE", value = "批量删除账单")
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody BatchIdsRequest request) {
        billService.deleteBills(request.getIds());
        return ApiResponse.success(null);
    }
}
