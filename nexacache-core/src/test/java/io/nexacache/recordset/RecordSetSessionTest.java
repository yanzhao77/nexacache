package io.nexacache.recordset;

import io.nexacache.annotation.NexaEntity;
import io.nexacache.annotation.NexaId;
import io.nexacache.cache.CacheRegion;
import io.nexacache.domain.EntityMeta;
import io.nexacache.exception.NexaCacheException;
import io.nexacache.spi.DataAccessor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecordSetSession 记录集高级 API 单元测试。
 *
 * @author azir
 * @since 1.1.0
 */
@DisplayName("RecordSet 高级 API 测试")
class RecordSetSessionTest {

    // ===================== 测试用实体 =====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @NexaEntity(region = "rs_test_item", maxSize = 100, ttl = 5, timeUnit = TimeUnit.MINUTES)
    static class Item {
        @NexaId
        private Long id;
        private String name;
        private int stock;
    }

    // ===================== 内存 DataAccessor（测试桩） =====================

    static class InMemoryItemAccessor implements DataAccessor<Item, Long> {
        private final Map<Long, Item> db = new LinkedHashMap<>();
        private long seq = 1L;

        InMemoryItemAccessor() {
            db.put(1L, Item.builder().id(1L).name("苹果").stock(100).build());
            db.put(2L, Item.builder().id(2L).name("香蕉").stock(200).build());
            db.put(3L, Item.builder().id(3L).name("橙子").stock(150).build());
        }

        @Override public Optional<Item> findById(Long id) { return Optional.ofNullable(db.get(id)); }
        @Override public List<Item> findAll() { return new ArrayList<>(db.values()); }
        @Override public int insert(Item e) { e.setId(++seq + 10); db.put(e.getId(), e); return 1; }
        @Override public int update(Item e) { db.put(e.getId(), e); return 1; }
        @Override public int deleteById(Long id) { db.remove(id); return 1; }
        @Override public Class<Item> entityType() { return Item.class; }
    }

    // ===================== 测试基础设施 =====================

    private CacheRegion<Item, Long> region;
    private InMemoryItemAccessor accessor;
    private RecordSetSession<Item, Long> rs;

    @BeforeEach
    void setUp() {
        EntityMeta<Item> meta = EntityMeta.of(Item.class);
        region = new CacheRegion<>(meta);
        accessor = new InMemoryItemAccessor();
        rs = new RecordSetSession<>(region, accessor);
    }

    @AfterEach
    void tearDown() {
        rs.close();
    }

    // ===================== START 操作测试 =====================

    @Nested
    @DisplayName("START 操作")
    class StartTests {

        @Test
        @DisplayName("start() 应将记录加载到缓存并建立指针")
        void start_shouldLoadRecordIntoCache() {
            rs.start(1L);
            assertTrue(region.hasPointer(1L), "指针层应存在 id=1 的指针");
            assertEquals(CursorState.READY, rs.state());
        }

        @Test
        @DisplayName("start() 后 read() 应返回正确记录")
        void start_thenRead_shouldReturnEntity() {
            rs.start(1L);
            Optional<Item> item = rs.read();
            assertTrue(item.isPresent());
            assertEquals("苹果", item.get().getName());
        }

        @Test
        @DisplayName("start() 不存在的 id 应抛出异常")
        void start_nonExistentId_shouldThrow() {
            assertThrows(NexaCacheException.class, () -> rs.start(999L));
        }

        @Test
        @DisplayName("start() 缓存已存在时不应重复查库")
        void start_cacheHit_shouldNotQueryDb() {
            // 先手动写入缓存
            Item cached = Item.builder().id(1L).name("缓存中的苹果").stock(50).build();
            region.put(cached);
            rs.start(1L);
            // 应读到缓存中的值，而非数据库中的值
            assertEquals("缓存中的苹果", rs.read().get().getName());
        }
    }

    // ===================== OPEN / 游标导航测试 =====================

    @Nested
    @DisplayName("OPEN + 游标导航")
    class OpenAndCursorTests {

        @Test
        @DisplayName("openAll() 应加载全部记录并打开游标")
        void openAll_shouldLoadAllRecords() {
            rs.openAll();
            assertEquals(3, rs.size());
            assertEquals(CursorState.OPEN, rs.state());
        }

        @Test
        @DisplayName("next() 应逐条遍历所有记录")
        void next_shouldIterateAllRecords() {
            rs.openAll();
            List<String> names = new ArrayList<>();
            // 游标初始在第 0 条，先读当前再 next
            rs.current().ifPresent(i -> names.add(i.getName()));
            while (rs.next()) {
                rs.current().ifPresent(i -> names.add(i.getName()));
            }
            assertEquals(3, names.size());
            assertEquals(List.of("苹果", "香蕉", "橙子"), names);
        }

        @Test
        @DisplayName("next() 到末尾后状态应变为 EOF")
        void next_pastEnd_shouldBeEof() {
            rs.openAll();
            while (rs.next()) { /* 遍历到底 */ }
            assertEquals(CursorState.EOF, rs.state());
        }

        @Test
        @DisplayName("first() 应将游标重置到第一条")
        void first_shouldResetCursorToFirst() {
            rs.openAll();
            rs.next(); rs.next(); // 移到第三条
            rs.first();
            assertEquals("苹果", rs.current().get().getName());
        }

        @Test
        @DisplayName("last() 应将游标移到最后一条")
        void last_shouldMoveCursorToLast() {
            rs.openAll();
            rs.last();
            assertEquals("橙子", rs.current().get().getName());
        }

        @Test
        @DisplayName("prev() 应向前移动游标")
        void prev_shouldMoveCursorBackward() {
            rs.openAll();
            rs.last();
            rs.prev();
            assertEquals("香蕉", rs.current().get().getName());
        }

        @Test
        @DisplayName("open(空列表) 后 size 应为 0")
        void open_emptyList_shouldHaveZeroSize() {
            rs.open(List.of());
            assertEquals(0, rs.size());
        }
    }

    // ===================== WRITE 操作测试 =====================

    @Nested
    @DisplayName("WRITE 操作")
    class WriteTests {

        @Test
        @DisplayName("write() 应持久化记录并写入缓存")
        void write_shouldPersistAndCache() {
            Item newItem = Item.builder().name("葡萄").stock(80).build();
            rs.write(newItem);
            assertNotNull(newItem.getId(), "INSERT 后主键应被回填");
            assertTrue(region.hasPointer(newItem.getId()), "缓存指针层应存在新记录");
        }
    }

    // ===================== REWRITE 操作（乐观锁）测试 =====================

    @Nested
    @DisplayName("REWRITE 操作（乐观锁）")
    class RewriteTests {

        @Test
        @DisplayName("rewrite() 应更新数据库和缓存")
        void rewrite_shouldUpdateDbAndCache() {
            rs.start(1L);
            Item updated = Item.builder().id(1L).name("红苹果").stock(90).build();
            rs.rewrite(updated);
            // 缓存中应是新值
            assertEquals("红苹果", region.get(1L).get().getName());
            // 数据库中也应是新值
            assertEquals("红苹果", accessor.findById(1L).get().getName());
        }

        @Test
        @DisplayName("乐观锁：未经 start() 直接 rewrite() 应正常通过（无快照则跳过检查）")
        void rewrite_withoutSnapshot_shouldPass() {
            Item updated = Item.builder().id(1L).name("绿苹果").stock(70).build();
            assertDoesNotThrow(() -> rs.rewrite(updated));
        }
    }

    // ===================== DELETE 操作测试 =====================

    @Nested
    @DisplayName("DELETE 操作")
    class DeleteTests {

        @Test
        @DisplayName("delete() 应删除当前游标记录（数据库 + 缓存）")
        void delete_shouldRemoveFromDbAndCache() {
            rs.openAll();
            // 游标在第一条（苹果）
            rs.delete();
            assertFalse(accessor.findById(1L).isPresent(), "数据库中应已删除");
            assertFalse(region.hasPointer(1L), "缓存指针层应已清除");
        }

        @Test
        @DisplayName("deleteById() 应按主键删除")
        void deleteById_shouldWork() {
            rs.deleteById(2L);
            assertFalse(accessor.findById(2L).isPresent());
        }
    }

    // ===================== CLOSE 操作测试 =====================

    @Nested
    @DisplayName("CLOSE 操作")
    class CloseTests {

        @Test
        @DisplayName("close() 后状态应变为 CLOSED")
        void close_shouldSetStateToClosed() {
            rs.openAll();
            rs.close();
            assertEquals(CursorState.CLOSED, rs.state());
        }

        @Test
        @DisplayName("close() 后调用任何操作应抛出异常")
        void close_thenOperate_shouldThrow() {
            rs.openAll();
            rs.close();
            assertThrows(NexaCacheException.class, () -> rs.next());
        }

        @Test
        @DisplayName("try-with-resources 应自动关闭记录集")
        void tryWithResources_shouldAutoClose() {
            RecordSetSession<Item, Long> session;
            try (RecordSetSession<Item, Long> s = new RecordSetSession<>(region, accessor)) {
                s.openAll();
                session = s;
            }
            assertEquals(CursorState.CLOSED, session.state());
        }
    }
}
