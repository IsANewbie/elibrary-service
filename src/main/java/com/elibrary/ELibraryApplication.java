package com.elibrary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.elibrary.**.mapper")
public class ELibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ELibraryApplication.class, args);
    }
}
