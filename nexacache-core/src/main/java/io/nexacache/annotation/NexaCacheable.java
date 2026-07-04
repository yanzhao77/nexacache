package io.nexacache.annotation;

import java.lang.annotation.*;

/**
 * 标记在 Service 方法上，使该方法的返回值自动走 NexaCache 缓存。
 * 若缓存命中则直接返回，不执行方法体；未命中则执行方法并将结果写入缓存。
 *
 * <p>示例：
 * <pre>{@code
 * @NexaCacheable(region = "user", key = "#id")
 * public User findById(Long id) { ... }
 * }</pre>
 *
 * @author azir
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NexaCacheable {

    /**
     * 缓存区域，对应 {@link NexaEntity#region()}。
     */
    String region();

    /**
     * 缓存键 SpEL 表达式，支持方法参数引用，如 {@code "#id"} 或 {@code "#user.id"}。
     */
    String key();
}
