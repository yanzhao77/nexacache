package io.nexacache.demo.entity;

import io.nexacache.annotation.NexaEntity;
import io.nexacache.annotation.NexaId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * 商品实体类。
 *
 * <p>注意：此类是一个纯 POJO，无需继承任何基类，
 * 仅通过 {@link NexaEntity} 和 {@link NexaId} 注解接入 NexaCache 框架。
 *
 * @author azir
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@NexaEntity(region = "product", maxSize = 2000, ttl = 10, timeUnit = TimeUnit.MINUTES)
public class Product {

    @NexaId
    private Long id;

    private String name;

    private BigDecimal price;

    private Integer stock;

    private String category;
}
