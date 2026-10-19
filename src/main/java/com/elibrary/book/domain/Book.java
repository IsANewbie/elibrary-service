package com.elibrary.book.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 书籍聚合，许可名额的占用与释放规则在聚合内
 * 字段 setter 仅为 ORM 映射与测试搭建保留，名额变更必须走 borrow/release。
 *
 * @author link
 * @date 2026/10/19
 */
@Getter
@Setter
@TableName("books")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;              // 主键
    private String title;         // 书名（keyword 匹配列之一）
    private String author;        // 作者（keyword 匹配列之一）
    private String category;      // 类别（TECHNOLOGY/LITERATURE/JOURNAL，电子书与期刊不单独建模）
    private String description;   // 简介（仅详情接口返回）
    private Integer totalLicenses;      // 总许可数（并发借阅上限，CHECK 保证 available ≤ total）
    private Integer availableLicenses;  // 当前可借名额（借 -1/还 +1）

    // 乐观锁版本号：冲突时 updateById 影响 0 行；DB 加载后必非 null（列 NOT NULL DEFAULT 0）
    @Version
    private Integer version;

    public void borrow() {
        // total/available 两列 NOT NULL（schema 契约），null 即半初始化对象属编程错误，不在此防御
        if (availableLicenses <= 0) {
            throw new NoLicenseException("可借名额已耗尽: " + title);
        }
        availableLicenses--;
    }

    public void release() {
        if (availableLicenses >= totalLicenses) {
            // 双放是调用方 bug（loan 状态机已挡重复归还），用程序错误而非业务异常
            throw new IllegalStateException("名额已满，禁止超额释放: " + title);
        }
        availableLicenses++;
    }
}
