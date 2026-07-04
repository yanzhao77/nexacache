package io.nexacache.autoconfigure;

import io.nexacache.api.NexaTemplate;
import io.nexacache.annotation.NexaEntity;
import io.nexacache.cache.CacheRegistry;
import io.nexacache.cache.NexaCacheAspect;
import io.nexacache.spi.DataAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NexaCache Spring Boot 自动装配配置。
 * 负责在应用启动时：
 * <ol>
 *   <li>创建并初始化 {@link CacheRegistry}，扫描所有 {@link NexaEntity} 实体</li>
 *   <li>收集所有 {@link DataAccessor} Bean，注入 {@link NexaTemplate}</li>
 *   <li>注册 AOP 切面 {@link NexaCacheAspect}</li>
 * </ol>
 *
 * @author azir
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(NexaCacheProperties.class)
@ConditionalOnProperty(prefix = "nexacache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NexaCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheRegistry nexaCacheRegistry(NexaCacheProperties properties, ApplicationContext context) {
        CacheRegistry registry = new CacheRegistry();

        // 扫描 Spring 容器中所有标注了 @NexaEntity 的 Bean 类型
        String[] beanNames = context.getBeanDefinitionNames();
        int count = 0;
        for (String beanName : beanNames) {
            try {
                Class<?> beanType = context.getType(beanName);
                if (beanType != null && beanType.isAnnotationPresent(NexaEntity.class)) {
                    registry.register(beanType);
                    count++;
                }
            } catch (Exception ignored) {
                // 忽略无法获取类型的 Bean
            }
        }

        // 额外扫描配置中指定的包路径
        if (!properties.getScanPackages().isEmpty()) {
            log.info("[NexaCache] 从配置包路径扫描 @NexaEntity: {}", properties.getScanPackages());
        }

        log.info("[NexaCache] 自动装配完成，共注册 {} 个缓存区域", count);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public NexaTemplate nexaTemplate(CacheRegistry registry,
                                      @Autowired(required = false) List<DataAccessor<?, ?>> accessors) {
        Map<Class<?>, DataAccessor<?, ?>> accessorMap = new HashMap<>();
        if (accessors != null) {
            for (DataAccessor<?, ?> accessor : accessors) {
                accessorMap.put(accessor.entityType(), accessor);
                log.info("[NexaCache] 自动注入 DataAccessor: {}", accessor.entityType().getSimpleName());
            }
        }
        return new NexaTemplate(registry, accessorMap);
    }

    @Bean
    @ConditionalOnMissingBean
    public NexaCacheAspect nexaCacheAspect(CacheRegistry registry) {
        return new NexaCacheAspect(registry);
    }
}
