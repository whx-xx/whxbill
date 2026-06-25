package com.whxbill.backend.modules.bill.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whxbill.backend.common.cache.RedisCacheSupport;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.common.web.PageResult;
import com.whxbill.backend.modules.bill.dto.BillExportRow;
import com.whxbill.backend.modules.bill.dto.BillImportPreviewResponse;
import com.whxbill.backend.modules.bill.dto.BillImportRequest;
import com.whxbill.backend.modules.bill.dto.BillImportRow;
import com.whxbill.backend.modules.bill.dto.BillPageQuery;
import com.whxbill.backend.modules.bill.dto.BillSaveRequest;
import com.whxbill.backend.modules.bill.entity.BizBill;
import com.whxbill.backend.modules.bill.entity.BizBudget;
import com.whxbill.backend.modules.bill.entity.BizCategory;
import com.whxbill.backend.modules.bill.entity.BizAccount;
import com.whxbill.backend.modules.bill.entity.BizBook;
import com.whxbill.backend.modules.bill.mapper.BizAccountMapper;
import com.whxbill.backend.modules.bill.mapper.BizBillMapper;
import com.whxbill.backend.modules.bill.mapper.BizBookMapper;
import com.whxbill.backend.modules.bill.mapper.BizBudgetMapper;
import com.whxbill.backend.modules.bill.mapper.BizCategoryMapper;
import com.whxbill.backend.modules.bill.service.BillService;
import com.whxbill.backend.modules.system.entity.SysMessage;
import com.whxbill.backend.modules.system.mapper.SysMessageMapper;
import com.whxbill.backend.security.SecurityUtils;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillService {

    private static final String SOURCE_TYPE_MANUAL = "MANUAL";
    private static final String SOURCE_TYPE_IMPORT = "IMPORT";
    private static final String SOURCE_TYPE_OCR = "OCR";
    private static final String SOURCE_TYPE_AUTO = "AUTO";
    private static final String MESSAGE_TYPE_BUDGET_ALERT = "BUDGET_ALERT";
    private static final String BUDGET_ALERT_TITLE = "预算超支提醒";
    private static final Long SHARED_CATEGORY_BOOK_ID = 0L;

    private final BizBillMapper bizBillMapper;
    private final BizAccountMapper bizAccountMapper;
    private final BizBookMapper bizBookMapper;
    private final BizBudgetMapper bizBudgetMapper;
    private final BizCategoryMapper bizCategoryMapper;
    private final SysMessageMapper sysMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisCacheSupport redisCacheSupport;

    /**
     * 分页查询账单列表，所有查询条件统一由 buildBillQueryWrapper 组装。
     */
    @Override
    public PageResult<BizBill> pageBills(BillPageQuery pageQuery) {
        Page<BizBill> page = bizBillMapper.selectPage(
            new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()),
            buildBillQueryWrapper(pageQuery));
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    /**
     * 根据当前筛选条件汇总收入、支出和结余，供账单列表顶部统计卡使用。
     */
    @Override
    public Map<String, BigDecimal> summaryBills(BillPageQuery pageQuery) {
        List<BizBill> bills = bizBillMapper.selectList(buildBillQueryWrapper(pageQuery));
        BigDecimal expense = bills.stream()
            .filter(bill -> "EXPENSE".equals(bill.getBillType()))
            .map(BizBill::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = bills.stream()
            .filter(bill -> "INCOME".equals(bill.getBillType()))
            .map(BizBill::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of(
            "expense", expense,
            "income", income,
            "balance", income.subtract(expense)
        );
    }

    /**
     * 导出账单时，把账本、账户、分类 id 转换成中文名称，生成 Excel 行对象。
     */
    @Override
    public List<BillExportRow> exportBills(BillPageQuery pageQuery) {
        List<BizBill> bills = bizBillMapper.selectList(buildBillQueryWrapper(pageQuery));
        if (bills.isEmpty()) {
            return List.of();
        }
        Long userId = SecurityUtils.getUserId();
        Map<Long, BizBook> bookMap = bizBookMapper.selectList(new LambdaQueryWrapper<BizBook>()
                .eq(BizBook::getUserId, userId))
            .stream()
            .collect(Collectors.toMap(BizBook::getId, Function.identity(), (left, right) -> left));
        // 预先查出字典表，避免每一条账单循环里重复查数据库。
        Map<Long, BizAccount> accountMap = bizAccountMapper.selectList(new LambdaQueryWrapper<BizAccount>()
                .eq(BizAccount::getUserId, userId))
            .stream()
            .collect(Collectors.toMap(BizAccount::getId, Function.identity(), (left, right) -> left));
        Map<Long, BizCategory> categoryMap = bizCategoryMapper.selectList(new LambdaQueryWrapper<BizCategory>()
                .eq(BizCategory::getUserId, userId))
            .stream()
            .collect(Collectors.toMap(BizCategory::getId, Function.identity(), (left, right) -> left));
        return bills.stream()
            .map(bill -> {
                BizBook book = bookMap.get(bill.getBookId());
                BizAccount account = accountMap.get(bill.getAccountId());
                BizCategory category = categoryMap.get(bill.getCategoryId());
                return new BillExportRow(
                    bill.getId(),
                    book == null ? "未知账本" : book.getBookName(),
                    "INCOME".equals(bill.getBillType()) ? "收入" : "支出",
                    bill.getAmount(),
                    bill.getBillDate() == null ? "" : bill.getBillDate().toString(),
                    account == null ? "未知账户" : account.getAccountName(),
                    category == null ? "未分类" : category.getCategoryName(),
                    bill.getMerchantName(),
                    bill.getRemark(),
                    displaySourceType(bill.getSourceType())
                );
            })
            .toList();
    }

    /**
     * Excel 导入预览：解析微信账单文件并返回可编辑的预览行，暂时不写入账单表。
     */
    @Override
    public BillImportPreviewResponse previewImport(Long bookId, MultipartFile file) {
        if (bookId == null) {
            throw new BusinessException("请选择导入账本");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        Long userId = SecurityUtils.getUserId();
        BizBook book = bizBookMapper.selectOne(new LambdaQueryWrapper<BizBook>()
            .eq(BizBook::getId, bookId)
            .eq(BizBook::getUserId, userId));
        if (book == null) {
            throw new BusinessException("账本不存在");
        }

        List<List<String>> sheetRows = readExcelRows(file);
        // 微信账单导出的表头位置不固定，所以先逐行扫描定位“交易时间/收支/金额”等列。
        Map<String, Integer> header = findHeader(sheetRows);
        if (header.isEmpty()) {
            throw new BusinessException("未识别到微信账单表头，请确认文件包含交易时间、收/支、金额等列");
        }

        ensureDefaultCategories(userId);
        List<BizAccount> accounts = bizAccountMapper.selectList(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getUserId, userId)
            .eq(BizAccount::getBookId, bookId)
            .orderByAsc(BizAccount::getSortOrder)
            .orderByDesc(BizAccount::getId));
        List<BizCategory> categories = bizCategoryMapper.selectList(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getUserId, userId)
            .orderByAsc(BizCategory::getSortOrder)
            .orderByAsc(BizCategory::getId));

        int headerIndex = (int) header.getOrDefault("__rowIndex", -1);
        List<BillImportRow> rows = new ArrayList<>();
        for (int index = headerIndex + 1; index < sheetRows.size(); index++) {
            List<String> raw = sheetRows.get(index);
            if (raw.stream().allMatch(String::isBlank)) {
                continue;
            }
            // 每一行原始 Excel 数据都会被转换成 BillImportRow，里面带有匹配结果和错误提示。
            BillImportRow row = buildImportRow(index + 1, raw, header, book, accounts, categories);
            if (row.getAmount() != null && row.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                rows.add(row);
            }
        }
        BigDecimal incomeTotal = rows.stream()
            .filter(row -> "INCOME".equals(row.getBillType()))
            .map(BillImportRow::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenseTotal = rows.stream()
            .filter(row -> "EXPENSE".equals(row.getBillType()))
            .map(BillImportRow::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long matched = rows.stream().filter(row -> Boolean.TRUE.equals(row.getValid())).count();
        return BillImportPreviewResponse.builder()
            .fileName(file.getOriginalFilename())
            .total(rows.size())
            .matched((int) matched)
            .incomeTotal(incomeTotal)
            .expenseTotal(expenseTotal)
            .rows(rows)
            .build();
    }

    /**
     * 确认导入：只导入前端勾选的预览行，逐行复用 saveBill 保证余额、预算和缓存都同步更新。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer importBills(BillImportRequest request) {
        int count = 0;
        for (BillImportRow row : request.getRows()) {
            if (!Boolean.TRUE.equals(row.getSelected())) {
                continue;
            }
            // 只导入预览页被用户勾选且字段完整的行，识别失败或用户删除的行不会入库。
            if (row.getBookId() == null || row.getCategoryId() == null
                || row.getAmount() == null || row.getBillDate() == null || row.getBillType() == null) {
                throw new BusinessException("第 " + row.getRowNo() + " 行账单信息不完整");
            }
            BizAccount account = resolveImportAccount(row);
            BillSaveRequest saveRequest = new BillSaveRequest();
            saveRequest.setBookId(row.getBookId());
            saveRequest.setAccountId(account.getId());
            saveRequest.setCategoryId(row.getCategoryId());
            saveRequest.setBillType(row.getBillType());
            saveRequest.setAmount(row.getAmount());
            saveRequest.setBillDate(row.getBillDate());
            saveRequest.setBillTime(parseTradeTime(row.getBillTime()));
            saveRequest.setMerchantName(row.getMerchantName());
            saveRequest.setRemark(row.getRemark());
            saveRequest.setSourceType("IMPORT");
            // 复用标准保存逻辑，导入账单和手动账单拥有一致的校验、余额变化和预算刷新。
            saveBill(saveRequest);
            count++;
        }
        return count;
    }

    /**
     * 使用 EasyExcel 把上传文件读成二维字符串列表。
     */
    private List<List<String>> readExcelRows(MultipartFile file) {
        List<List<String>> rows = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    int maxIndex = data.keySet().stream().max(Integer::compareTo).orElse(0);
                    List<String> row = new ArrayList<>();
                    for (int index = 0; index <= maxIndex; index++) {
                        // 每个单元格都先清理空白和 BOM，后续表头匹配更稳定。
                        row.add(cleanCell(data.get(index)));
                    }
                    rows.add(row);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                }
            }).sheet().headRowNumber(0).doRead();
        } catch (IOException exception) {
            throw new BusinessException("Excel 文件读取失败");
        }
        return rows;
    }

    /**
     * 从微信账单 Excel 中定位表头列，表头不一定在第一行，所以逐行扫描。
     */
    private Map<String, Integer> findHeader(List<List<String>> rows) {
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            Map<String, Integer> header = new HashMap<>();
            for (int column = 0; column < row.size(); column++) {
                String value = row.get(column);
                if (value.contains("交易时间")) header.put("tradeTime", column);
                if (value.contains("交易类型")) header.put("tradeType", column);
                if (value.contains("交易对方")) header.put("counterparty", column);
                if (value.contains("商品")) header.put("goods", column);
                if (value.contains("收/支")) header.put("direction", column);
                if (value.contains("金额")) header.put("amount", column);
                if (value.contains("支付方式")) header.put("paymentMethod", column);
                if (value.contains("当前状态")) header.put("tradeStatus", column);
                if (value.contains("交易单号")) header.put("transactionNo", column);
                if (value.contains("商户单号")) header.put("merchantNo", column);
                if (value.contains("备注")) header.put("remark", column);
            }
            if (header.containsKey("tradeTime") && header.containsKey("direction") && header.containsKey("amount")) {
                // 找到关键字段后记录表头所在行，后续从下一行开始读取真实流水。
                header.put("__rowIndex", rowIndex);
                return header;
            }
        }
        return Map.of();
    }

    /**
     * 把一行 Excel 原始数据转换成可预览、可修改、可导入的 BillImportRow。
     */
    private BillImportRow buildImportRow(int rowNo, List<String> raw, Map<String, Integer> header, BizBook book,
                                         List<BizAccount> accounts, List<BizCategory> categories) {
        String tradeTime = cell(raw, header, "tradeTime");
        String direction = cell(raw, header, "direction");
        String amountText = cell(raw, header, "amount");
        String tradeType = cell(raw, header, "tradeType");
        String counterparty = cell(raw, header, "counterparty");
        String goods = cell(raw, header, "goods");
        String paymentMethod = cell(raw, header, "paymentMethod");
        String tradeStatus = cell(raw, header, "tradeStatus");
        String transactionNo = cell(raw, header, "transactionNo");
        String merchantNo = cell(raw, header, "merchantNo");
        String remark = cell(raw, header, "remark");

        String combinedText = tradeType + " " + counterparty + " " + goods + " " + remark + " " + tradeStatus;
        // 根据收/支列和交易文本推断账单类型。
        String billType = inferBillType(direction, combinedText);
        BigDecimal amount = parseAmount(amountText);
        LocalDateTime dateTime = parseTradeTime(tradeTime);
        // 支付方式会先被转成账户名称，再与当前账本已有账户匹配。
        String suggestedAccountName = inferAccountName(paymentMethod);
        BizAccount account = matchAccount(suggestedAccountName, paymentMethod, accounts);
        BizCategory category = matchCategory(billType, combinedText, dateTime, categories);

        BillImportRow row = new BillImportRow();
        row.setRowNo(rowNo);
        row.setRawColumns(raw);
        row.setRawText(raw.stream().filter(item -> !item.isBlank()).collect(Collectors.joining(" · ")));
        row.setBookId(book.getId());
        row.setBookName(book.getBookName());
        row.setAccountId(account == null ? null : account.getId());
        row.setAccountName(account == null ? suggestedAccountName : account.getAccountName());
        row.setAccountMissing(account == null);
        row.setCategoryId(category == null ? null : category.getId());
        row.setCategoryName(category == null ? "未匹配分类" : category.getCategoryName());
        row.setBillType(billType);
        row.setAmount(amount);
        row.setBillDate(dateTime == null ? null : dateTime.toLocalDate());
        row.setBillTime(dateTime == null ? "" : dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        row.setMerchantName(firstNotBlank(counterparty, goods, tradeType));
        row.setRemark(firstNotBlank(goods, remark, tradeType));
        row.setPaymentMethod(paymentMethod);
        row.setTradeStatus(tradeStatus);
        row.setTransactionNo(transactionNo);
        row.setMerchantNo(merchantNo);
        boolean statusImportable = isImportableTradeStatus(tradeStatus);
        boolean valid = amount != null && dateTime != null && category != null && row.getAccountName() != null && !row.getAccountName().isBlank()
            && statusImportable;
        // valid 表示后端已经识别到导入所需的关键字段，前端默认只勾选 valid 行。
        row.setValid(valid);
        row.setSelected(valid);
        if (valid && "EXPENSE".equals(billType) && isRefundStatus(tradeStatus)) {
            row.setSelected(false);
            row.setWarningMessage("该支出已全额退款，默认不导入；如需保留流水可手动勾选");
        }
        if (!valid) {
            row.setErrorMessage(importErrorMessage(amount, dateTime, category, tradeStatus, statusImportable));
        }
        return row;
    }

    /**
     * 判断微信交易状态是否适合导入，失败、关闭等流水不进入账本。
     */
    private boolean isImportableTradeStatus(String tradeStatus) {
        return tradeStatus == null || tradeStatus.isBlank()
            || tradeStatus.contains("成功")
            || tradeStatus.contains("已存入")
            || tradeStatus.contains("对方已收钱")
            || tradeStatus.contains("已转账")
            || tradeStatus.contains("已全额退款")
            || tradeStatus.contains("已退款")
            || tradeStatus.contains("已到账")
            || tradeStatus.contains("资金已到账");
    }

    /**
     * 判断是否是退款状态；退款流水默认不勾选，避免重复计算支出。
     */
    private boolean isRefundStatus(String tradeStatus) {
        return tradeStatus != null && (tradeStatus.contains("已全额退款") || tradeStatus.contains("已退款"));
    }

    /**
     * 生成导入预览行的错误原因，方便前端展示为什么不能导入。
     */
    private String importErrorMessage(BigDecimal amount, LocalDateTime dateTime, BizCategory category, String tradeStatus, boolean statusImportable) {
        List<String> reasons = new ArrayList<>();
        if (amount == null) reasons.add("金额无法识别");
        if (dateTime == null) reasons.add("交易时间无法识别");
        if (category == null) reasons.add("未匹配到分类");
        if (!statusImportable) reasons.add("交易状态暂不导入：" + firstNotBlank(tradeStatus, "空"));
        return reasons.isEmpty() ? "信息不完整" : String.join("，", reasons);
    }

    /**
     * 导入时解析账户：优先使用预览行已有账户 id，没有则按名称查找或自动创建账户。
     */
    private BizAccount resolveImportAccount(BillImportRow row) {
        Long userId = SecurityUtils.getUserId();
        if (row.getAccountId() != null) {
            BizAccount account = bizAccountMapper.selectOne(new LambdaQueryWrapper<BizAccount>()
                .eq(BizAccount::getId, row.getAccountId())
                .eq(BizAccount::getUserId, userId)
                .eq(BizAccount::getBookId, row.getBookId()));
            if (account != null) {
                return account;
            }
        }
        String accountName = firstNotBlank(row.getAccountName(), inferAccountName(row.getPaymentMethod()));
        if (accountName == null || accountName.isBlank()) {
            accountName = "微信";
        }
        BizAccount existing = bizAccountMapper.selectOne(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getUserId, userId)
            .eq(BizAccount::getBookId, row.getBookId())
            .eq(BizAccount::getAccountName, accountName));
        if (existing != null) {
            return existing;
        }
        // 微信账单里出现的新支付方式会自动创建为账户，减少用户导入前的准备工作。
        BizAccount account = new BizAccount();
        account.setUserId(userId);
        account.setBookId(row.getBookId());
        account.setAccountName(accountName);
        account.setAccountType(inferAccountType(accountName));
        account.setBalance(BigDecimal.ZERO);
        account.setColorTag(inferAccountColor(accountName));
        account.setSortOrder(99);
        bizAccountMapper.insert(account);
        return account;
    }

    /**
     * 老数据兼容：如果用户没有任何分类，导入前补一批基础分类。
     */
    private void ensureDefaultCategories(Long userId) {
        Long categoryCount = bizCategoryMapper.selectCount(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getUserId, userId));
        if (categoryCount != null && categoryCount > 0) {
            return;
        }
        String[][] categories = {
            {"餐饮", "EXPENSE", "Bowl", "10"},
            {"交通", "EXPENSE", "Van", "20"},
            {"购物", "EXPENSE", "ShoppingBag", "30"},
            {"生活缴费", "EXPENSE", "House", "40"},
            {"娱乐", "EXPENSE", "Film", "50"},
            {"医疗", "EXPENSE", "FirstAidKit", "60"},
            {"其他支出", "EXPENSE", "MoreFilled", "90"},
            {"工资", "INCOME", "Money", "10"},
            {"红包转账", "INCOME", "Wallet", "20"},
            {"退款", "INCOME", "RefreshLeft", "30"},
            {"其他收入", "INCOME", "MoreFilled", "90"}
        };
        for (String[] item : categories) {
            BizCategory category = new BizCategory();
            category.setUserId(userId);
            category.setBookId(SHARED_CATEGORY_BOOK_ID);
            category.setParentId(0L);
            category.setCategoryName(item[0]);
            category.setCategoryType(item[1]);
            category.setIcon(item[2]);
            category.setLevel(1);
            category.setSortOrder(Integer.valueOf(item[3]));
            bizCategoryMapper.insert(category);
        }
    }

    /**
     * 根据推断账户名和原始支付方式，在当前账本账户列表里找最匹配的账户。
     */
    private BizAccount matchAccount(String accountName, String paymentMethod, List<BizAccount> accounts) {
        String target = accountName == null ? "" : accountName.toLowerCase(Locale.ROOT);
        String raw = paymentMethod == null ? "" : paymentMethod.toLowerCase(Locale.ROOT);
        return accounts.stream()
            .filter(account -> {
                String name = account.getAccountName().toLowerCase(Locale.ROOT);
                return name.equals(target) || target.contains(name) || raw.contains(name);
            })
            .findFirst()
            .orElse(null);
    }

    /**
     * 从微信支付方式文本推断账户名称。
     */
    private String inferAccountName(String paymentMethod) {
        String value = paymentMethod == null ? "" : paymentMethod;
        if (value.contains("零钱通")) return "零钱通";
        if (value.contains("零钱")) return "微信零钱";
        if (value.contains("微信")) return "微信";
        if (value.contains("支付宝")) return "支付宝";
        if (value.contains("银行卡") || value.contains("银行") || value.matches(".*\\d{4}.*")) return "银行卡";
        if (value.contains("信用卡")) return "信用卡";
        return value.isBlank() || "/".equals(value) ? "微信" : value;
    }

    /**
     * 根据收支方向和交易关键词推断账单类型。
     */
    private String inferBillType(String direction, String text) {
        if (direction != null && direction.contains("收入")) {
            return "INCOME";
        }
        if (direction != null && direction.contains("支出")) {
            return "EXPENSE";
        }
        String key = text == null ? "" : text;
        if (containsAny(key, "领红包", "红包到账", "已到账", "资金已到账", "转入", "收款", "退款")) {
            return "INCOME";
        }
        if (containsAny(key, "转出", "付款", "消费")) {
            return "EXPENSE";
        }
        return "EXPENSE";
    }

    /**
     * 根据账户名称推断账户类型。
     */
    private String inferAccountType(String accountName) {
        if (accountName.contains("信用卡")) return "CREDIT";
        if (accountName.contains("银行卡") || accountName.contains("银行")) return "BANK";
        return "CASH";
    }

    /**
     * 给自动创建的账户选择一个默认颜色。
     */
    private String inferAccountColor(String accountName) {
        if (accountName.contains("支付宝")) return "#1677ff";
        if (accountName.contains("银行")) return "#4f6bed";
        return "#20a995";
    }

    /**
     * 分类匹配：先看交易文本是否包含分类名，匹配不到再按关键词推断。
     */
    private BizCategory matchCategory(String billType, String text, LocalDateTime dateTime, List<BizCategory> categories) {
        List<BizCategory> sameType = categories.stream()
            .filter(category -> billType.equals(category.getCategoryType()))
            .toList();
        String key = text == null ? "" : text;
        return sameType.stream()
            .filter(category -> category.getCategoryName() != null && key.contains(category.getCategoryName()))
            .findFirst()
            .orElseGet(() -> inferCategory(billType, key, dateTime, sameType));
    }

    /**
     * 根据交易文本和时间推断分类，例如餐饮按时间进一步分早餐/午餐/晚餐。
     */
    private BizCategory inferCategory(String billType, String text, LocalDateTime dateTime, List<BizCategory> categories) {
        if ("INCOME".equals(billType)) {
            if (containsAny(text, "工资", "薪资")) return findCategory(categories, "工资");
            if (containsAny(text, "奖金", "激励")) return findCategory(categories, "奖金");
            if (containsAny(text, "报销")) return findCategory(categories, "报销");
            if (containsAny(text, "补贴")) return findCategory(categories, "补贴");
            if (containsAny(text, "领红包", "红包", "礼金")) return findCategory(categories, "礼金人情", "其他");
            if (containsAny(text, "转账", "转入", "到账", "收款")) return findCategory(categories, "借入", "其他");
            return findCategory(categories, "其他", "收入");
        }
        if (containsAny(text, "零钱通", "基金", "理财", "转出到", "资金已到账")) {
            return findCategory(categories, "理财支出", "其他");
        }
        if (containsAny(text, "美团", "饿了么", "饭", "餐", "咖啡", "奶茶", "食", "早餐", "午餐", "晚餐")) {
            if (containsAny(text, "早餐")) return findCategory(categories, "早餐", "食品餐饮", "其他");
            if (containsAny(text, "晚餐", "夜宵")) return findCategory(categories, "晚餐", "食品餐饮", "其他");
            if (containsAny(text, "午餐")) return findCategory(categories, "午餐", "食品餐饮", "其他");
            if (dateTime != null) {
                int hour = dateTime.getHour();
                if (hour < 10) return findCategory(categories, "早餐", "食品餐饮", "其他");
                if (hour >= 16) return findCategory(categories, "晚餐", "食品餐饮", "其他");
                if (hour >= 10 && hour <= 15) return findCategory(categories, "午餐", "食品餐饮", "其他");
            }
            return findCategory(categories, "食品餐饮", "其他");
        }
        if (containsAny(text, "地铁", "公交", "打车", "滴滴", "加油", "停车", "交通")) {
            return findCategory(categories, "公共交通", "打车", "出行交通", "其他");
        }
        if (containsAny(text, "药", "医院", "医疗", "挂号")) {
            return findCategory(categories, "买药", "医院", "健康医疗", "其他");
        }
        if (containsAny(text, "学", "课程", "书", "教育", "培训")) {
            return findCategory(categories, "学费", "文化教育", "其他");
        }
        if (containsAny(text, "红包", "转账", "礼物")) {
            return findCategory(categories, "红包", "送礼人情", "其他");
        }
        if (containsAny(text, "电影", "游戏", "娱乐", "旅游")) {
            return findCategory(categories, "电影唱歌", "休闲娱乐", "其他");
        }
        if (containsAny(text, "超市", "便利", "京东", "淘宝", "拼多多", "购物")) {
            return findCategory(categories, "日常家居", "购物消费", "其他");
        }
        return findCategory(categories, "其他", "食品餐饮");
    }

    /**
     * 按名称依次查找分类，找不到时返回当前类型下第一个分类兜底。
     */
    private BizCategory findCategory(List<BizCategory> categories, String... names) {
        for (String name : names) {
            Optional<BizCategory> matched = categories.stream()
                .filter(category -> name.equals(category.getCategoryName()) || category.getCategoryName().contains(name))
                .findFirst();
            if (matched.isPresent()) {
                return matched.get();
            }
        }
        return categories.isEmpty() ? null : categories.get(0);
    }

    /**
     * 判断文本是否包含任意关键词。
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按表头 key 读取某一列内容。
     */
    private String cell(List<String> raw, Map<String, Integer> header, String key) {
        Integer index = header.get(key);
        return index == null || index >= raw.size() ? "" : cleanCell(raw.get(index));
    }

    /**
     * 清理 Excel 单元格内容。
     */
    private String cleanCell(String value) {
        return value == null ? "" : value.trim().replace("\uFEFF", "");
    }

    /**
     * 解析金额，去掉货币符号和千分位，统一为正数金额。
     */
    private BigDecimal parseAmount(String value) {
        String normalized = value == null ? "" : value.replace("¥", "").replace("￥", "").replace(",", "").trim();
        if (normalized.isBlank() || "/".equals(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized).abs().setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 解析微信账单里的交易时间，兼容多种日期格式。
     */
    private LocalDateTime parseTradeTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value.trim(), formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 返回第一个非空字符串，导入字段兜底时使用。
     */
    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"/".equals(value.trim())) {
                return value.trim();
            }
        }
        return "微信账单";
    }

    /**
     * 构造账单查询条件，列表、汇总、导出都复用同一套筛选逻辑。
     */
    private LambdaQueryWrapper<BizBill> buildBillQueryWrapper(BillPageQuery pageQuery) {
        String keyword = pageQuery.getKeyword();
        List<Long> childCategoryIds = pageQuery.getParentCategoryId() == null ? List.of() : bizCategoryMapper
            .selectList(new LambdaQueryWrapper<BizCategory>()
                .eq(BizCategory::getUserId, SecurityUtils.getUserId())
                .eq(BizCategory::getParentId, pageQuery.getParentCategoryId()))
            .stream()
            .map(BizCategory::getId)
            .toList();
        List<Long> categoryIds = pageQuery.getParentCategoryId() == null
            ? List.of()
            : java.util.stream.Stream.concat(java.util.stream.Stream.of(pageQuery.getParentCategoryId()), childCategoryIds.stream()).toList();
        // 如果前端传的是 categoryId，就是查某个具体分类
        // 如果前端传的是 parentCategoryId，后端会先查这个一级分类下面的所有子分类
        return new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getUserId, SecurityUtils.getUserId())
            .eq(pageQuery.getBookId() != null, BizBill::getBookId, pageQuery.getBookId())
            .like(pageQuery.getMonth() != null && !pageQuery.getMonth().isBlank(), BizBill::getBillDate, pageQuery.getMonth())
            .eq(pageQuery.getBillType() != null && !pageQuery.getBillType().isBlank(), BizBill::getBillType, pageQuery.getBillType())
            .and(hasText(pageQuery.getSourceType()), wrapper -> applySourceTypeFilter(wrapper, pageQuery.getSourceType()))
            .eq(pageQuery.getAccountId() != null, BizBill::getAccountId, pageQuery.getAccountId())
            .eq(pageQuery.getCategoryId() != null, BizBill::getCategoryId, pageQuery.getCategoryId())
            .in(!categoryIds.isEmpty(), BizBill::getCategoryId, categoryIds)
            .ge(pageQuery.getStartDate() != null, BizBill::getBillDate, pageQuery.getStartDate())
            .le(pageQuery.getEndDate() != null, BizBill::getBillDate, pageQuery.getEndDate())
            .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                .like(BizBill::getMerchantName, keyword)
                .or()
                .like(BizBill::getRemark, keyword))
            .orderByDesc(BizBill::getBillDate)
            .orderByDesc(BizBill::getId);
    }

    /**
     * 新增或修改账单：校验归属 -> 保存账单 -> 调整账户余额 -> 刷新预算和统计缓存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizBill saveBill(BillSaveRequest request) {
        Long userId = SecurityUtils.getUserId();
        BizBook book = bizBookMapper.selectOne(new LambdaQueryWrapper<BizBook>()
            .eq(BizBook::getId, request.getBookId())
            .eq(BizBook::getUserId, userId));
        if (book == null) {
            throw new BusinessException("账本不存在");
        }
        BizAccount account = bizAccountMapper.selectOne(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getId, request.getAccountId())
            .eq(BizAccount::getUserId, userId)
            .eq(BizAccount::getBookId, request.getBookId()));
        if (account == null) {
            throw new BusinessException("账户不存在或不属于当前账本");
        }
        BizCategory category = bizCategoryMapper.selectOne(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getId, request.getCategoryId())
            .eq(BizCategory::getUserId, userId));
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        if (!category.getCategoryType().equals(request.getBillType())) {
            throw new BusinessException("账单类型与分类类型不一致");
        }

        BizBill oldBill = request.getId() == null ? null : bizBillMapper.selectOne(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getId, request.getId())
            .eq(BizBill::getUserId, userId));
        BizBill bill = request.getId() == null ? new BizBill() : oldBill;
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        Long oldAccountId = oldBill == null ? null : oldBill.getAccountId();
        String oldBillType = oldBill == null ? null : oldBill.getBillType();
        BigDecimal oldAmount = oldBill == null ? null : oldBill.getAmount();
        Long oldBookId = oldBill == null ? null : oldBill.getBookId();
        LocalDate oldBillDate = oldBill == null ? null : oldBill.getBillDate();
        bill.setUserId(userId);
        bill.setBookId(request.getBookId());
        bill.setAccountId(request.getAccountId());
        bill.setCategoryId(request.getCategoryId());
        bill.setBillType(request.getBillType());
        bill.setAmount(request.getAmount());
        bill.setBillDate(request.getBillDate());
        bill.setBillTime(request.getBillTime() == null ? request.getBillDate().atStartOfDay() : request.getBillTime());
        bill.setMerchantName(request.getMerchantName());
        bill.setRemark(request.getRemark());
        bill.setSourceType(normalizeSourceType(request.getSourceType()));

        if (request.getId() == null) {
            bizBillMapper.insert(bill);
        } else {
            bizBillMapper.updateById(bill);
        }

        if (oldBill != null) {
            // 修改账单时先撤销旧账单对账户余额的影响，再应用新账单影响。
            adjustAccountBalance(oldAccountId, oldBillType, oldAmount.negate(), userId);
            refreshBudgetUsage(oldBookId, oldBillDate);
        }
        adjustAccountBalance(bill.getAccountId(), bill.getBillType(), bill.getAmount(), userId);
        refreshBudgetUsage(bill.getBookId(), bill.getBillDate());
        clearDashboardCache(userId);
        return bill;
    }

    /**
     * 把来源编码转换成页面和 Excel 里更友好的中文名称。
     */
    private String displaySourceType(String sourceType) {
        String normalized = normalizeSourceType(sourceType);
        if (SOURCE_TYPE_AUTO.equals(normalized)) {
            return "\u81ea\u52a8\u8bb0\u8d26";
        }
        if (SOURCE_TYPE_IMPORT.equals(normalized)) {
            return "Excel\u5bfc\u5165";
        }
        if (SOURCE_TYPE_OCR.equals(normalized)) {
            return "OCR\u5bfc\u5165";
        }
        return "\u624b\u52a8\u8bb0\u8d26";
    }

    /**
     * 兼容历史中文值和英文值，统一成 MANUAL/IMPORT/OCR/AUTO。
     */
    private String normalizeSourceType(String sourceType) {
        if (!hasText(sourceType)) {
            return SOURCE_TYPE_MANUAL;
        }
        String normalized = sourceType.trim().toUpperCase();
        if (SOURCE_TYPE_AUTO.equals(normalized) || "\u81ea\u52a8\u8bb0\u8d26".equals(sourceType)) {
            return SOURCE_TYPE_AUTO;
        }
        if ("OCR".equals(normalized) || "OCR识别".equals(sourceType) || "OCR导入".equals(sourceType)) {
            return SOURCE_TYPE_OCR;
        }
        if ("IMPORT".equals(normalized) || "EXCEL".equals(normalized) || "Excel导入".equals(sourceType)) {
            return SOURCE_TYPE_IMPORT;
        }
        return SOURCE_TYPE_MANUAL.equals(normalized) || "手动记账".equals(sourceType) ? SOURCE_TYPE_MANUAL : sourceType.trim();
    }

    /**
     * 删除单条账单，并同步回滚账户余额、刷新预算和统计缓存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBill(Long billId) {
        Long userId = SecurityUtils.getUserId();
        BizBill bill = bizBillMapper.selectOne(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getId, billId)
            .eq(BizBill::getUserId, userId));
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        bizBillMapper.deleteById(billId);
        adjustAccountBalance(bill.getAccountId(), bill.getBillType(), bill.getAmount().negate(), userId);
        refreshBudgetUsage(bill.getBookId(), bill.getBillDate());
        clearDashboardCache(userId);
    }

    /**
     * 批量删除账单，复用单条删除逻辑保证副作用一致。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBills(List<Long> billIds) {
        // 批量删除复用单条删除，确保权限归属、余额回滚和预算刷新规则完全一致。
        billIds.forEach(this::deleteBill);
    }

    /**
     * 日历视图按日期读取账单列表。
     */
    @Override
    public List<BizBill> listBillsByDate(String date, Long bookId) {
        return bizBillMapper.selectList(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getUserId, SecurityUtils.getUserId())
            .eq(bookId != null, BizBill::getBookId, bookId)
            .eq(BizBill::getBillDate, LocalDate.parse(date))
            .orderByDesc(BizBill::getId));
    }

    /**
     * 根据账单类型调整账户余额：收入加，支出减。
     */
    private void adjustAccountBalance(Long accountId, String billType, BigDecimal amount, Long userId) {
        BizAccount account = bizAccountMapper.selectOne(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getId, accountId)
            .eq(BizAccount::getUserId, userId));
        if (account == null) {
            throw new BusinessException("账户不存在");
        }
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        BigDecimal delta = "INCOME".equals(billType) ? amount : amount.negate();
        account.setBalance(balance.add(delta));
        bizAccountMapper.updateById(account);
    }

    /**
     * 重新计算某账本某月份的预算已用金额，并在超支时推送提醒。
     */
    private void refreshBudgetUsage(Long bookId, LocalDate billDate) {
        String budgetMonth = billDate.toString().substring(0, 7);
        Long userId = SecurityUtils.getUserId();
        List<BizBudget> budgets = bizBudgetMapper.selectList(new LambdaQueryWrapper<BizBudget>()
            .eq(BizBudget::getUserId, userId)
            .eq(BizBudget::getBookId, bookId)
            .eq(BizBudget::getBudgetMonth, budgetMonth));
        if (budgets.isEmpty()) {
            return;
        }
        List<BizBill> expenseBills = bizBillMapper.selectList(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getUserId, userId)
            .eq(BizBill::getBookId, bookId)
            .eq(BizBill::getBillType, "EXPENSE")
            .like(BizBill::getBillDate, budgetMonth));
        List<Long> categoryIds = expenseBills.stream().map(BizBill::getCategoryId).distinct().toList();
        List<BizCategory> categories = categoryIds.isEmpty() ? List.of() : bizCategoryMapper.selectList(new LambdaQueryWrapper<BizCategory>()
            .eq(BizCategory::getUserId, userId)
            .in(BizCategory::getId, categoryIds));

        for (BizBudget budget : budgets) {
            BigDecimal usedAmount = expenseBills.stream()
                // 总预算 categoryId 为 null，分类预算只统计该分类及其子分类账单。
                .filter(bill -> budget.getCategoryId() == null || matchesBudgetCategory(budget.getCategoryId(), bill.getCategoryId(), categories))
                .map(BizBill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            budget.setUsedAmount(usedAmount);
            bizBudgetMapper.updateById(budget);
            if (budget.getUsedAmount().compareTo(budget.getBudgetAmount()) > 0) {
                pushBudgetAlert(userId, budgetMonth);
            }
        }
    }

    /**
     * 判断账单分类是否命中预算分类，支持一级分类预算包含二级分类账单。
     */
    private boolean matchesBudgetCategory(Long budgetCategoryId, Long billCategoryId, List<BizCategory> categories) {
        if (budgetCategoryId.equals(billCategoryId)) {
            return true;
        }
        return categories.stream()
            .filter(category -> category.getId().equals(billCategoryId))
            .anyMatch(category -> budgetCategoryId.equals(category.getParentId()));
    }

    /**
     * 账单变化后清除当前用户统计缓存，避免统计页展示旧数据。
     */
    private void clearDashboardCache(Long userId) {
        redisCacheSupport.deleteByPattern(StatisticsServiceImpl.DASHBOARD_CACHE_PREFIX + userId + ":*");
    }

    /**
     * 来源类型筛选兼容历史中文值和英文枚举值。
     */
    private void applySourceTypeFilter(LambdaQueryWrapper<BizBill> wrapper, String sourceType) {
        String normalized = normalizeSourceType(sourceType);
        if (SOURCE_TYPE_MANUAL.equals(normalized)) {
            wrapper.eq(BizBill::getSourceType, SOURCE_TYPE_MANUAL)
                .or()
                .isNull(BizBill::getSourceType)
                .or()
                .eq(BizBill::getSourceType, "")
                .or()
                .eq(BizBill::getSourceType, "手动记账");
            return;
        }
        if (SOURCE_TYPE_IMPORT.equals(normalized)) {
            wrapper.eq(BizBill::getSourceType, SOURCE_TYPE_IMPORT)
                .or()
                .eq(BizBill::getSourceType, "EXCEL")
                .or()
                .eq(BizBill::getSourceType, "Excel导入");
            return;
        }
        if (SOURCE_TYPE_OCR.equals(normalized)) {
            wrapper.eq(BizBill::getSourceType, SOURCE_TYPE_OCR)
                .or()
                .eq(BizBill::getSourceType, "OCR识别")
                .or()
                .eq(BizBill::getSourceType, "OCR导入");
            return;
        }
        if (SOURCE_TYPE_AUTO.equals(normalized)) {
            wrapper.eq(BizBill::getSourceType, SOURCE_TYPE_AUTO)
                .or()
                .eq(BizBill::getSourceType, "\u81ea\u52a8\u8bb0\u8d26");
            return;
        }
        wrapper.eq(BizBill::getSourceType, sourceType);
    }

    /**
     * 预算超支时写入站内消息，并通过 WebSocket 推送给当前用户。
     */
    private void pushBudgetAlert(Long userId, String budgetMonth) {
        SysMessage message = new SysMessage();
        message.setUserId(userId);
        message.setMessageType(MESSAGE_TYPE_BUDGET_ALERT);
        message.setTitle(BUDGET_ALERT_TITLE);
        message.setContent("预算 " + budgetMonth + " 已超支，请留意支出。");
        message.setReadStatus(0);
        sysMessageMapper.insert(message);
        // convertAndSendToUser 对应前端订阅的 /user/queue/notifications。
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", message);
        // topic 通道用于兼容按用户 id 订阅的通知入口。
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, message);
    }

    /**
     * 简单字符串非空判断，避免重复写 null 和 blank 判断。
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
