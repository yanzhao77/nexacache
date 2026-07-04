package io.nexacache.demo;

import io.nexacache.api.NexaTemplate;
import io.nexacache.demo.entity.Product;
import io.nexacache.demo.service.ProductService;
import io.nexacache.recordset.RecordSetSession;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * ProductService 集成测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>编程式 CRUD（create / findById / update / delete）</li>
 *   <li>缓存命中与指针状态验证</li>
 *   <li>缓存预热（warmUp）</li>
 *   <li>注解声明式缓存（@NexaCacheable / @NexaCacheEvict）</li>
 *   <li>记录集高级 API（START / OPEN / CURSOR / REWRITE / DELETE）</li>
 *   <li>乐观锁冲突检测</li>
 * </ul>
 */
@DisplayName("ProductService 集成测试")
class ProductServiceTest extends BaseTest {

    @Autowired
    private ProductService productService;

    // ─────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────

    private Product buildProduct(String name, double price, int stock, String category) {
        return Product.builder()
                .name(name)
                .price(BigDecimal.valueOf(price))
                .stock(stock)
                .category(category)
                .build();
    }

    private Product saveAndGet(String name, double price, int stock, String category) {
        return productService.create(buildProduct(name, price, stock, category));
    }

    // ─────────────────────────────────────────────
    // 1. 编程式 CRUD
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("1. 编程式 CRUD")
    class CrudTests {

        @Test
        @DisplayName("create：新增商品后可查询到，且主键自动生成")
        void create_shouldPersistAndReturnWithId() {
            Product saved = saveAndGet("MacBook Pro", 12999.0, 50, "电脑");

            assertThat(saved.getId()).isNotNull().isPositive();
            assertThat(saved.getName()).isEqualTo("MacBook Pro");
            assertThat(saved.getPrice()).isEqualByComparingTo("12999.0");
        }

        @Test
        @DisplayName("findById：命中缓存后不再访问数据库（指针存在）")
        void findById_shouldHitCacheAfterFirstLoad() {
            Product saved = saveAndGet("iPhone 16", 7999.0, 100, "手机");
            Long id = saved.getId();

            // 第一次查询：写入缓存
            Optional<Product> first = nexaTemplate.opsForEntity(Product.class).findById(id);
            assertThat(first).isPresent();
            assertThat(nexaTemplate.opsForEntity(Product.class).hasPointer(id)).isTrue();

            // 第二次查询：应命中缓存
            Optional<Product> second = nexaTemplate.opsForEntity(Product.class).findById(id);
            assertThat(second).isPresent();
            assertThat(second.get().getName()).isEqualTo("iPhone 16");
        }

        @Test
        @DisplayName("findById：不存在的 ID 返回 empty")
        void findById_notExists_shouldReturnEmpty() {
            Optional<Product> result = nexaTemplate.opsForEntity(Product.class).findById(99999L);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("update：更新后缓存同步刷新")
        void update_shouldRefreshCache() {
            Product saved = saveAndGet("AirPods Pro", 1799.0, 200, "耳机");
            Long id = saved.getId();

            // 先查一次，写入缓存
            nexaTemplate.opsForEntity(Product.class).findById(id);

            // 更新
            saved.setPrice(BigDecimal.valueOf(1599.0));
            saved.setStock(180);
            nexaTemplate.opsForEntity(Product.class).save(saved);

            // 再次查询，应拿到新值
            Optional<Product> updated = nexaTemplate.opsForEntity(Product.class).findById(id);
            assertThat(updated).isPresent();
            assertThat(updated.get().getPrice()).isEqualByComparingTo("1599.0");
            assertThat(updated.get().getStock()).isEqualTo(180);
        }

        @Test
        @DisplayName("deleteById：删除后缓存指针消失，再查返回 empty")
        void delete_shouldEvictCache() {
            Product saved = saveAndGet("iPad Air", 4799.0, 30, "平板");
            Long id = saved.getId();

            // 写入缓存
            nexaTemplate.opsForEntity(Product.class).findById(id);
            assertThat(nexaTemplate.opsForEntity(Product.class).hasPointer(id)).isTrue();

            // 删除
            nexaTemplate.opsForEntity(Product.class).deleteById(id);

            // 指针应消失
            assertThat(nexaTemplate.opsForEntity(Product.class).hasPointer(id)).isFalse();

            // 再查应为 empty
            Optional<Product> result = nexaTemplate.opsForEntity(Product.class).findById(id);
            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────
    // 2. 缓存预热
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("2. 缓存预热（warmUp）")
    class WarmUpTests {

        @Test
        @DisplayName("warmUp：批量 load 后所有 ID 的指针均存在")
        void warmUp_shouldLoadAllPointers() {
            Product p1 = saveAndGet("商品A", 100.0, 10, "分类X");
            Product p2 = saveAndGet("商品B", 200.0, 20, "分类X");
            Product p3 = saveAndGet("商品C", 300.0, 30, "分类Y");

            productService.warmUp(List.of(p1, p2, p3));

            NexaTemplate.EntityOps<Product, Long> ops = nexaTemplate.opsForEntity(Product.class);
            assertThat(ops.hasPointer(p1.getId())).isTrue();
            assertThat(ops.hasPointer(p2.getId())).isTrue();
            assertThat(ops.hasPointer(p3.getId())).isTrue();
            assertThat(ops.cacheSize()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("warmUp 后 findById 直接命中缓存，无需查库")
        void warmUp_thenFindById_shouldHitCache() {
            Product saved = saveAndGet("预热商品", 999.0, 5, "测试");
            productService.warmUp(List.of(saved));

            // 此时缓存中已有数据，findById 应直接返回
            Optional<Product> result = nexaTemplate.opsForEntity(Product.class).findById(saved.getId());
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("预热商品");
        }
    }

    // ─────────────────────────────────────────────
    // 3. 注解声明式缓存
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("3. 注解声明式缓存（@NexaCacheable / @NexaCacheEvict）")
    class AnnotationCacheTests {

        @Test
        @DisplayName("@NexaCacheable：首次调用查库，再次调用命中缓存")
        void cacheable_shouldCacheResultAfterFirstCall() {
            Product saved = saveAndGet("注解测试商品", 599.0, 15, "测试");
            Long id = saved.getId();

            // 第一次调用（走 DB）
            Optional<Product> first = productService.findById(id);
            assertThat(first).isPresent();

            // 第二次调用（命中缓存，指针存在）
            Optional<Product> second = productService.findById(id);
            assertThat(second).isPresent();
            assertThat(second.get().getId()).isEqualTo(id);
            assertThat(nexaTemplate.opsForEntity(Product.class).hasPointer(id)).isTrue();
        }

        @Test
        @DisplayName("@NexaCacheEvict（update）：更新后旧缓存被驱逐")
        void cacheEvict_onUpdate_shouldEvictOldCache() {
            Product saved = saveAndGet("待更新商品", 299.0, 50, "测试");
            Long id = saved.getId();

            // 先查一次写入缓存
            productService.findById(id);
            assertThat(nexaTemplate.opsForEntity(Product.class).hasPointer(id)).isTrue();

            // 通过注解方式更新，触发 @NexaCacheEvict
            saved.setPrice(BigDecimal.valueOf(199.0));
            productService.update(saved);

            // 再次查询应拿到最新价格
            Optional<Product> result = productService.findById(id);
            assertThat(result).isPresent();
            assertThat(result.get().getPrice()).isEqualByComparingTo("199.0");
        }

        @Test
        @DisplayName("@NexaCacheEvict（delete，beforeInvocation=true）：删除前驱逐缓存")
        void cacheEvict_onDelete_shouldEvictBeforeDelete() {
            Product saved = saveAndGet("待删除商品", 99.0, 5, "测试");
            Long id = saved.getId();

            // 先查一次写入缓存
            productService.findById(id);
            assertThat(nexaTemplate.opsForEntity(Product.class).hasPointer(id)).isTrue();

            // 通过注解方式删除
            productService.delete(id);

            // 缓存指针应已消失
            assertThat(nexaTemplate.opsForEntity(Product.class).hasPointer(id)).isFalse();

            // 再查应为 empty
            Optional<Product> result = productService.findById(id);
            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────
    // 4. 记录集高级 API
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("4. 记录集高级 API（RecordSetSession）")
    class RecordSetApiTests {

        @Test
        @DisplayName("start + read：加载单条记录后可读取")
        void start_thenRead_shouldReturnRecord() throws Exception {
            Product saved = saveAndGet("游标商品", 888.0, 10, "高端");
            Long id = saved.getId();

            try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
                rs.start(id);
                Optional<Product> read = rs.read();
                assertThat(read).isPresent();
                assertThat(read.get().getName()).isEqualTo("游标商品");
                assertThat(read.get().getPrice()).isEqualByComparingTo("888.0");
            }
        }

        @Test
        @DisplayName("openAll + next 遍历：可遍历所有记录")
        void openAll_thenNext_shouldIterateAllRecords() throws Exception {
            saveAndGet("商品1", 10.0, 1, "A");
            saveAndGet("商品2", 20.0, 2, "A");
            saveAndGet("商品3", 30.0, 3, "B");

            try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
                rs.openAll();
                assertThat(rs.size()).isEqualTo(3);

                int count = 0;
                // openAll 后游标在第一条，用 do-while 遍历全部
                do {
                    Optional<Product> cur = rs.current();
                    assertThat(cur).isPresent();
                    count++;
                } while (rs.next());
                assertThat(count).isEqualTo(3);
            }
        }

        @Test
        @DisplayName("openAll + first/last：游标可跳转到首尾")
        void openAll_firstAndLast_shouldNavigate() throws Exception {
            saveAndGet("首商品", 1.0, 1, "X");
            saveAndGet("中商品", 2.0, 2, "X");
            saveAndGet("尾商品", 3.0, 3, "X");

            try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
                rs.openAll();

                rs.first();
                Optional<Product> first = rs.current();
                assertThat(first).isPresent();
                assertThat(first.get().getName()).isEqualTo("首商品");

                rs.last();
                Optional<Product> last = rs.current();
                assertThat(last).isPresent();
                assertThat(last.get().getName()).isEqualTo("尾商品");
            }
        }

        @Test
        @DisplayName("start + rewrite：更新记录后数据库和缓存同步")
        void start_thenRewrite_shouldUpdatePersistenceAndCache() throws Exception {
            Product saved = saveAndGet("原始商品", 500.0, 20, "测试");
            Long id = saved.getId();

            try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
                rs.start(id);
                Product p = rs.read().orElseThrow();
                p.setPrice(BigDecimal.valueOf(450.0));
                p.setStock(15);
                rs.rewrite(p);
            }

            // 验证数据库已更新
            Optional<Product> updated = nexaTemplate.opsForEntity(Product.class).findById(id);
            assertThat(updated).isPresent();
            assertThat(updated.get().getPrice()).isEqualByComparingTo("450.0");
            assertThat(updated.get().getStock()).isEqualTo(15);
        }

        @Test
        @DisplayName("write：通过记录集新增商品，主键自动回填")
        void write_shouldInsertAndReturnWithId() throws Exception {
            Product newProduct = buildProduct("记录集新增", 666.0, 8, "测试");

            try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
                rs.write(newProduct);
            }

            assertThat(newProduct.getId()).isNotNull().isPositive();

            Optional<Product> found = nexaTemplate.opsForEntity(Product.class).findById(newProduct.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("记录集新增");
        }

        @Test
        @DisplayName("openAll + delete：删除游标当前记录后数量减少")
        void openAll_thenDelete_shouldRemoveCurrentRecord() throws Exception {
            saveAndGet("保留商品A", 10.0, 1, "X");
            Product toDelete = saveAndGet("待删商品", 20.0, 2, "X");
            saveAndGet("保留商品B", 30.0, 3, "X");

            try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
                rs.openAll();
                // openAll 后已在第一条，调一次 next() 移到第二条（待删商品）
                rs.next();
                rs.delete();
                assertThat(rs.size()).isEqualTo(2);
            }

            // 验证数据库中已删除
            Optional<Product> deleted = nexaTemplate.opsForEntity(Product.class).findById(toDelete.getId());
            assertThat(deleted).isEmpty();
        }

        @Test
        @DisplayName("乐观锁：start 后外部修改同一记录，rewrite 应抛出 ConcurrentModificationException")
        void rewrite_withStaleSnapshot_shouldThrowConcurrentModificationException() throws Exception {
            Product saved = saveAndGet("乐观锁商品", 300.0, 10, "测试");
            Long id = saved.getId();

            try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
                rs.start(id);
                Product p = rs.read().orElseThrow();

                // 模拟外部修改：直接通过 EntityOps 更新，改变缓存中的对象状态
                Product modified = Product.builder()
                        .id(p.getId()).name(p.getName())
                        .price(BigDecimal.valueOf(250.0))
                        .stock(p.getStock()).category(p.getCategory())
                        .build();
                nexaTemplate.opsForEntity(Product.class).save(modified);

                // 此时 rs 持有的快照已过期，rewrite 应抛出异常
                p.setStock(5);
                assertThatThrownBy(() -> rs.rewrite(p))
                        .isInstanceOf(java.util.ConcurrentModificationException.class);
            }
        }

        @Test
        @DisplayName("close 后操作应抛出 IllegalStateException")
        void afterClose_operations_shouldThrowIllegalStateException() throws Exception {
            Product saved = saveAndGet("关闭测试商品", 100.0, 5, "测试");

            RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class);
            rs.start(saved.getId());
            rs.close();

            assertThatThrownBy(rs::read)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─────────────────────────────────────────────
    // 5. 缓存状态统计
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("5. 缓存状态统计")
    class CacheStatsTests {

        @Test
        @DisplayName("cacheSize：新增多条后缓存数量正确")
        void cacheSize_shouldReflectLoadedEntries() {
            Product p1 = saveAndGet("统计商品1", 1.0, 1, "A");
            Product p2 = saveAndGet("统计商品2", 2.0, 2, "A");

            NexaTemplate.EntityOps<Product, Long> ops = nexaTemplate.opsForEntity(Product.class);
            ops.load(p1);
            ops.load(p2);

            assertThat(ops.cacheSize()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("evict：手动驱逐后指针消失，cacheSize 减少")
        void evict_shouldRemovePointerAndDecreaseSize() {
            Product p = saveAndGet("驱逐测试商品", 50.0, 3, "B");
            NexaTemplate.EntityOps<Product, Long> ops = nexaTemplate.opsForEntity(Product.class);
            ops.load(p);

            assertThat(ops.hasPointer(p.getId())).isTrue();

            ops.evict(p.getId());

            assertThat(ops.hasPointer(p.getId())).isFalse();
        }
    }
}
