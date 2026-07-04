package io.nexacache.demo;

import io.nexacache.api.NexaTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Demo 集成测试基类。
 * 每个测试方法执行前重建 product 表，保证测试隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected NexaTemplate nexaTemplate;

    @BeforeEach
    void setUp() {
        // 每次测试前清空缓存，防止跨测试数据污染
        nexaTemplate.clearAll();
        jdbcTemplate.execute("DROP TABLE IF EXISTS product");
        jdbcTemplate.execute("""
                CREATE TABLE product (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    name     TEXT    NOT NULL,
                    price    REAL    NOT NULL,
                    stock    INTEGER NOT NULL DEFAULT 0,
                    category TEXT
                )
                """);
    }
}
