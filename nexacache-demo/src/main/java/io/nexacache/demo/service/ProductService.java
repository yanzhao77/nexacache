package io.nexacache.demo.service;

import io.nexacache.annotation.NexaCacheEvict;
import io.nexacache.annotation.NexaCacheable;
import io.nexacache.api.NexaTemplate;
import io.nexacache.demo.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 商品业务服务。
 * 演示 NexaCache 的两种使用方式：
 * <ol>
 *   <li><b>注解方式</b>：{@link NexaCacheable} / {@link NexaCacheEvict}，零代码侵入</li>
 *   <li><b>编程式</b>：通过 {@link NexaTemplate} 精细控制缓存行为</li>
 * </ol>
 *
 * @author azir
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final NexaTemplate nexaTemplate;

    // ==================== 注解方式 ====================

    /**
     * 根据 ID 查询商品（注解方式，自动走缓存）。
     */
    @NexaCacheable(region = "product", key = "#id")
    public Optional<Product> findById(Long id) {
        log.info("【DB查询】findById: id={}", id);
        return nexaTemplate.opsForEntity(Product.class).findById(id);
    }

    /**
     * 更新商品后驱逐缓存（注解方式）。
     */
    @NexaCacheEvict(region = "product", key = "#product.id")
    public Product update(Product product) {
        log.info("【DB更新】update: id={}", product.getId());
        return nexaTemplate.opsForEntity(Product.class).save(product);
    }

    /**
     * 删除商品并驱逐缓存（注解方式，方法执行前驱逐）。
     */
    @NexaCacheEvict(region = "product", key = "#id", beforeInvocation = true)
    public void delete(Long id) {
        log.info("【DB删除】delete: id={}", id);
        nexaTemplate.opsForEntity(Product.class).deleteById(id);
    }

    // ==================== 编程式 ====================

    /**
     * 新增商品（编程式，持久化 + 自动写入缓存）。
     */
    public Product create(Product product) {
        log.info("【DB新增】create: name={}", product.getName());
        return nexaTemplate.opsForEntity(Product.class).save(product);
    }

    /**
     * 查询所有商品（直接查库，不走缓存）。
     */
    public List<Product> findAll() {
        return nexaTemplate.opsForEntity(Product.class)
                .findById(0L) // 仅演示，实际通过 accessor 查全部
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * 将已有商品批量预热到缓存（编程式 load）。
     */
    public void warmUp(List<Product> products) {
        NexaTemplate.EntityOps<Product, Long> ops = nexaTemplate.opsForEntity(Product.class);
        products.forEach(ops::load);
        log.info("[NexaCache] 预热完成，共加载 {} 条商品到缓存", products.size());
    }

    /**
     * 查询缓存中的商品数量。
     */
    public long cacheSize() {
        return nexaTemplate.opsForEntity(Product.class).cacheSize();
    }

    /**
     * 判断指定 ID 的商品是否在缓存指针中。
     */
    public boolean hasPointer(Long id) {
        return nexaTemplate.opsForEntity(Product.class).hasPointer(id);
    }
}
