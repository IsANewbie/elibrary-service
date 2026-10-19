package com.elibrary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 应用入口，集中装配 MapperScan 与配置属性扫描
 *
 * @author link
 * @date 2026/10/19
 */
@SpringBootApplication
@ConfigurationPropertiesScan
// lazyInitialization：@WebMvcTest 切片上下文没有 SqlSessionFactory，eager mapper 会让切片全部启动失败
@MapperScan(value = "com.elibrary.**.mapper", lazyInitialization = "true")
public class ELibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ELibraryApplication.class, args);
    }
}
