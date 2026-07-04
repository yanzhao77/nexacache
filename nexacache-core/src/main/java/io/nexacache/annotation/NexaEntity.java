package io.nexacache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 标记一个实体类受 NexaCache 管理。
 * 框架将为其自动维护指针池与数据缓存，无需继承任何基类。
 *
 * <p>示例：
 * <pre>{@code
 * @NexaEntity(region = "user", maxSize = 5000, ttl = 30, timeUnit = TimeUnit.MINUTES)
 * public class User { ... }
 * }</pre>
 *
 * @author azir
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NexaEntity {

    /**
     * 缓存区域名称，用于隔离不同实体的缓存空间。
     * 默认使用类名（小写）。
     */
    String region() default "";

    /**
     * 缓存最大条目数，超出后按 LRU 策略淘汰。
     */
    long maxSize() default 1000L;

    /**
     * 缓存条目存活时间（写入后）。0 表示永不过期。
     */
    long ttl() default 0L;

    /**
     * TTL 时间单位，默认分钟。
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;
}
