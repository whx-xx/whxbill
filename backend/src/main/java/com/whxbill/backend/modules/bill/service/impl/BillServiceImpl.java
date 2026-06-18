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

    private final BizBillMapper bizBillMapper;
    private final BizAccountMapper bizAccountMapper;
    private final BizBookMapper bizBookMapper;
    private final BizBudgetMapper bizBudgetMapper;
    private final BizCategoryMapper bizCategoryMapper;
    private final SysMessageMapper sysMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisCacheSupport redisCacheSupport;

    @Override
    public PageResult<BizBill> pageBills(BillPageQuery pageQuery) {
        Page<BizBill> page = bizBillMapper.selectPage(
            new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()),
            buildBillQueryWrapper(pageQuery));
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

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
        Map<String, Integer> header = findHeader(sheetRows);
        if (header.isEmpty()) {
            throw new BusinessException("未识别到微信账单表头，请确认文件包含交易时间、收/支、金额等列");
        }

        List<BizAccount> accounts = bizAccountMapper.selectList(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getUserId, userId)
            .eq(BizAccount::getBookId, bookId)
            .orderByAsc(BizAccount::getSortOrder)
            .orderByDesc(BizAccount::getId));
        if (accounts.isEmpty()) {
            throw new BusinessException("当前账本暂无账户，请先创建账户");
        }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer importBills(BillImportRequest request) {
        int count = 0;
        for (BillImportRow row : request.getRows()) {
            if (!Boolean.TRUE.equals(row.getSelected())) {
                continue;
            }
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
            saveBill(saveRequest);
            count++;
        }
        return count;
    }

    private List<List<String>> readExcelRows(MultipartFile file) {
        List<List<String>> rows = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    int maxIndex = data.keySet().stream().max(Integer::compareTo).orElse(0);
                    List<String> row = new ArrayList<>();
                    for (int index = 0; index <= maxIndex; index++) {
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
                header.put("__rowIndex", rowIndex);
                return header;
            }
        }
        return Map.of();
    }

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
        String billType = inferBillType(direction, combinedText);
        BigDecimal amount = parseAmount(amountText);
        LocalDateTime dateTime = parseTradeTime(tradeTime);
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

    private boolean isRefundStatus(String tradeStatus) {
        return tradeStatus != null && (tradeStatus.contains("已全额退款") || tradeStatus.contains("已退款"));
    }

    private String importErrorMessage(BigDecimal amount, LocalDateTime dateTime, BizCategory category, String tradeStatus, boolean statusImportable) {
        List<String> reasons = new ArrayList<>();
        if (amount == null) reasons.add("金额无法识别");
        if (dateTime == null) reasons.add("交易时间无法识别");
        if (category == null) reasons.add("未匹配到分类");
        if (!statusImportable) reasons.add("交易状态暂不导入：" + firstNotBlank(tradeStatus, "空"));
        return reasons.isEmpty() ? "信息不完整" : String.join("，", reasons);
    }

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
        BizAccount existing = bizAccountMapper.selectOne(new LambdaQueryWrapper<BizAccount>()
            .eq(BizAccount::getUserId, userId)
            .eq(BizAccount::getBookId, row.getBookId())
            .eq(BizAccount::getAccountName, accountName));
        if (existing != null) {
            return existing;
        }
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

    private String inferAccountType(String accountName) {
        if (accountName.contains("信用卡")) return "CREDIT";
        if (accountName.contains("银行卡") || accountName.contains("银行")) return "BANK";
        return "CASH";
    }

    private String inferAccountColor(String accountName) {
        if (accountName.contains("支付宝")) return "#1677ff";
        if (accountName.contains("银行")) return "#4f6bed";
        return "#20a995";
    }

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

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String cell(List<String> raw, Map<String, Integer> header, String key) {
        Integer index = header.get(key);
        return index == null || index >= raw.size() ? "" : cleanCell(raw.get(index));
    }

    private String cleanCell(String value) {
        return value == null ? "" : value.trim().replace("\uFEFF", "");
    }

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

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"/".equals(value.trim())) {
                return value.trim();
            }
        }
        return "微信账单";
    }

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
            adjustAccountBalance(oldAccountId, oldBillType, oldAmount.negate(), userId);
            refreshBudgetUsage(oldBookId, oldBillDate);
        }
        adjustAccountBalance(bill.getAccountId(), bill.getBillType(), bill.getAmount(), userId);
        refreshBudgetUsage(bill.getBookId(), bill.getBillDate());
        clearDashboardCache(userId);
        return bill;
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBills(List<Long> billIds) {
        // 批量删除复用单条删除，确保权限归属、余额回滚和预算刷新规则完全一致。
        billIds.forEach(this::deleteBill);
    }

    @Override
    public List<BizBill> listBillsByDate(String date, Long bookId) {
        return bizBillMapper.selectList(new LambdaQueryWrapper<BizBill>()
            .eq(BizBill::getUserId, SecurityUtils.getUserId())
            .eq(bookId != null, BizBill::getBookId, bookId)
            .eq(BizBill::getBillDate, LocalDate.parse(date))
            .orderByDesc(BizBill::getId));
    }

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

    private boolean matchesBudgetCategory(Long budgetCategoryId, Long billCategoryId, List<BizCategory> categories) {
        if (budgetCategoryId.equals(billCategoryId)) {
            return true;
        }
        return categories.stream()
            .filter(category -> category.getId().equals(billCategoryId))
            .anyMatch(category -> budgetCategoryId.equals(category.getParentId()));
    }

    private void clearDashboardCache(Long userId) {
        redisCacheSupport.deleteByPattern(StatisticsServiceImpl.DASHBOARD_CACHE_PREFIX + userId + ":*");
    }

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

    private void pushBudgetAlert(Long userId, String budgetMonth) {
        SysMessage message = new SysMessage();
        message.setUserId(userId);
        message.setMessageType(MESSAGE_TYPE_BUDGET_ALERT);
        message.setTitle(BUDGET_ALERT_TITLE);
        message.setContent("预算 " + budgetMonth + " 已超支，请留意支出。");
        message.setReadStatus(0);
        sysMessageMapper.insert(message);
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", message);
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
