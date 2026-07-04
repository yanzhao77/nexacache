package io.nexacache.annotation;

import java.lang.annotation.*;

/**
 * 标记在 Service 方法上，方法执行后自动驱逐指定缓存条目。
 * 通常用于更新和删除操作，保证缓存与数据库的一致性。
 *
 * <p>示例：
 * <pre>{@code
 * @NexaCacheEvict(region = "user", key = "#user.id")
 * public void updateUser(User user) { ... }
 * }</pre>
 *
 * @author azir
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NexaCacheEvict {

    /**
     * 缓存区域。
     */
    String region();

    /**
     * 要驱逐的缓存键 SpEL 表达式。
     */
    String key();

    /**
     * 是否在方法执行前驱逐（默认为 false，即方法执行后驱逐）。
     */
    boolean beforeInvocation() default false;
}
