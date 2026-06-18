package com.whxbill.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureDictExtraColumn();
        ensureCurrencyDict("人民币", "CNY", "{\"symbol\":\"¥\",\"locale\":\"zh-CN\",\"fractionDigits\":2}", 1);
        ensureCurrencyDict("美元", "USD", "{\"symbol\":\"$\",\"locale\":\"en-US\",\"fractionDigits\":2}", 2);
        ensureCurrencyDict("欧元", "EUR", "{\"symbol\":\"€\",\"locale\":\"de-DE\",\"fractionDigits\":2}", 3);
    }

    private void ensureDictExtraColumn() {
        Integer count = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = database()
              and table_name = 'sys_dict'
              and column_name = 'dict_extra'
            """, Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute("alter table sys_dict add column dict_extra varchar(500) null comment '字典扩展配置'");
        }
    }

    private void ensureCurrencyDict(String label, String value, String extra, int sortOrder) {
        Integer count = jdbcTemplate.queryForObject("""
            select count(*)
            from sys_dict
            where dict_type = 'currency'
              and dict_value = ?
              and deleted = 0
            """, Integer.class, value);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                insert into sys_dict
                  (dict_type, dict_label, dict_value, dict_extra, sort_order, status, deleted, created_time, updated_time)
                values
                  ('currency', ?, ?, ?, ?, 1, 0, now(), now())
                """, label, value, extra, sortOrder);
            return;
        }
        jdbcTemplate.update("""
            update sys_dict
            set dict_extra = case
                when dict_extra is null or dict_extra = '' then ?
                else dict_extra
              end,
              updated_time = now()
            where dict_type = 'currency'
              and dict_value = ?
              and deleted = 0
            """, extra, value);
    }
}
