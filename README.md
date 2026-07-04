<div align="center">

# 🚀 NexaCache

**基于注解与双层缓存架构的高性能数据访问层框架**

[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Caffeine](https://img.shields.io/badge/Caffeine-3.1.8-orange.svg)](https://github.com/ben-manes/caffeine)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)

*零侵入注解驱动 · Caffeine + 指针双层架构 · SPI 适配解耦*

</div>

---

## 📖 项目简介

**NexaCache** 是一个全新设计的现代 Java 内存缓存框架。它采用**领域驱动设计（DDD）**和**微内核+插件**架构，旨在为关系型数据库提供极速的本地缓存支持，同时保持业务代码的绝对纯净。

### ✨ 核心亮点

- 🛡️ **零代码侵入**：纯注解驱动（`@NexaEntity`, `@NexaId`），实体类无需继承任何基类。
- ⚡ **极致性能**：底层采用 Java 生态最快的本地缓存库 **Caffeine**，结合 `ConcurrentHashMap` 维护高速指针池。
- 🧩 **SPI 适配架构**：核心引擎与 ORM 框架完全解耦。内置 MyBatis 适配器，未来可轻松扩展 JPA、MyBatis-Plus 等。
- 🎯 **双重编程模型**：
  - **声明式**：`@NexaCacheable` / `@NexaCacheEvict` 自动拦截，隐式管理缓存。
  - **编程式**：`NexaTemplate` 提供类似 `RedisTemplate` 的直觉 API，精细控制操作。
- 🚀 **开箱即用**：提供 `nexacache-spring-boot-starter`，引入依赖即可使用。

---

## 🏗️ 架构设计

NexaCache 采用分层与 SPI 解耦设计：

```mermaid
graph TD
    A[业务应用层 Application] -->|@NexaCacheable 注解| B(AOP 拦截器)
    A -->|编程式调用| C(NexaTemplate 门面)
    
    B --> D{NexaCache 核心引擎}
    C --> D
    
    D --> E[CacheRegistry 全局注册表]
    E --> F[CacheRegion 双层缓存区域]
    
    F --> G[指针层: ConcurrentHashMap]
    F --> H[数据层: Caffeine Cache]
    
    D --> I{DataAccessor SPI 适配层}
    I --> J[MyBatis Adapter]
    I --> K[JPA Adapter (扩展)]
    
    J --> L[(数据库)]
```

---

## 🚀 快速开始

### 1. 引入依赖

在 Spring Boot 项目中引入 Starter：

```xml
<dependency>
    <groupId>io.nexacache</groupId>
    <artifactId>nexacache-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<!-- 引入 MyBatis 适配器 -->
<dependency>
    <groupId>io.nexacache</groupId>
    <artifactId>nexacache-adapter-mybatis</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 标记实体类

无需继承任何基类，直接在 POJO 上添加注解：

```java
@Data
@NexaEntity(region = "product", maxSize = 2000, ttl = 10, timeUnit = TimeUnit.MINUTES)
public class Product {
    
    @NexaId
    private Long id;
    
    private String name;
    private BigDecimal price;
}
```

### 3. 配置适配器

继承 `AbstractMyBatisAccessor` 即可自动获得 CRUD 能力：

```java
@Component
public class ProductAccessor extends AbstractMyBatisAccessor<Product, Long> {
    public ProductAccessor(SqlSessionFactory factory) {
        super(factory, Product.class, ProductMapper.class);
    }
}
```

### 4. 业务使用

**方式一：声明式注解（推荐）**

```java
@Service
public class ProductService {
    
    // 自动查询缓存，未命中则查库并回填
    @NexaCacheable(region = "product", key = "#id")
    public Optional<Product> findById(Long id) {
        // ... 原生查库逻辑
    }

    // 更新数据库后自动驱逐缓存
    @NexaCacheEvict(region = "product", key = "#product.id")
    public void update(Product product) {
        // ... 原生更新逻辑
    }
}
```

**方式二：编程式 API**

```java
@Service
public class ProductService {
    @Autowired
    private NexaTemplate nexaTemplate;

    public void demo(Product p) {
        // 保存并自动写入缓存
        nexaTemplate.opsForEntity(Product.class).save(p);
        
        // 优先读缓存
        Optional<Product> cached = nexaTemplate.opsForEntity(Product.class).findById(p.getId());
    }
}
```

---

## 📂 模块说明

- `nexacache-core`: 核心引擎，包含双层缓存架构、AOP 拦截器、SPI 定义。
- `nexacache-adapter-mybatis`: 基于 MyBatis 的持久层 SPI 实现。
- `nexacache-spring-boot-starter`: Spring Boot 自动装配模块。
- `nexacache-demo`: 完整的可运行示例。

---

## 🤝 贡献与支持

欢迎提交 Issue 和 Pull Request！

**License**: MIT
