package io.nexacache.demo.config;

import io.nexacache.adapter.mybatis.AbstractMyBatisAccessor;
import io.nexacache.demo.entity.Product;
import io.nexacache.demo.mapper.ProductMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

/**
 * Product 实体的 MyBatis 数据访问适配器。
 * 继承 {@link AbstractMyBatisAccessor} 即可获得完整 CRUD 能力，无需编写任何方法体。
 *
 * @author azir
 */
@Component
public class ProductAccessor extends AbstractMyBatisAccessor<Product, Long> {

    public ProductAccessor(SqlSessionFactory sqlSessionFactory) {
        super(sqlSessionFactory, Product.class, ProductMapper.class);
    }
}
