package com.elibrary.book.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 书籍聚合，许可名额的占用与释放规则在聚合内
 *
 * @author link
 * @date 2026/10/19
 */
@Getter
@Setter
@TableName("books")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private String category;
    private String description;
    private Integer totalLicenses;
    private Integer availableLicenses;

    @Version
    private Integer version;

    public void borrow() {
        if (availableLicenses == null || availableLicenses <= 0) {
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
