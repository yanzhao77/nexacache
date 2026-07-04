## 🎉 NexaCache v1.1.0 — 引入记录集高级 API

在这个版本中，我们在保持原有简洁 API（CRUD）零侵入的基础上，引入了全新的**记录集高级 API（RecordSet API）**。

这套 API 借鉴了数据库游标（Cursor）的思想，为复杂的业务场景提供了更精细的控制能力，包括**游标导航**和**无侵入乐观锁**。

---

### ✨ 新增特性

**1. 记录集会话（RecordSetSession）**
通过 `nexaTemplate.opsForRecordSet(Entity.class)` 获取，实现了 `AutoCloseable`，支持 `try-with-resources` 自动释放游标资源。

**2. 数据库游标风格操作**
支持以下操作语义：
- `start(id)`：将单条记录加载到缓存并建立指针（FETCH）
- `open(list)` / `openAll()`：批量加载记录并打开游标（OPEN CURSOR）
- `read()` / `current()`：读取当前游标指向的记录
- `next()` / `prev()` / `first()` / `last()`：游标位置导航
- `write()` / `delete()`：增删记录并同步缓存

**3. 无侵入乐观锁（REWRITE 操作）**
- 原理：在 `start()` 或 `open()` 时，框架会自动记录实体的版本快照（基于对象 `hashCode`）。
- 校验：在调用 `rewrite()` 更新时，框架会比对当前缓存实体与快照。若期间有其他线程修改了该记录，将抛出 `ConcurrentModificationException`。
- **优势：实体类无需增加任何 `@Version` 字段，完全由框架在内存中透明维护。**

---

### 🧪 测试覆盖

核心引擎单元测试增加至 **35 个**，覆盖了记录集 API 的所有生命周期状态（IDLE/READY/OPEN/EOF/CLOSED）及乐观锁校验，全部通过。

---

### 📦 升级指南

直接将版本号升级至 `1.1.0` 即可，完全向后兼容 `1.0.0`。
