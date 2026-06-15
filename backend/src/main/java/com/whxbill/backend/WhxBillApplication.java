package com.whxbill.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WhxBillApplication {

    public static void main(String[] args) {
        // Spring Boot 应用入口，启动后会自动扫描当前包及子包下的 Controller、Service、Mapper 等组件。
        SpringApplication.run(WhxBillApplication.class, args);
    }
}
