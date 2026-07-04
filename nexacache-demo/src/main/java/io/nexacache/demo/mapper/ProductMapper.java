package io.nexacache.demo.mapper;

import io.nexacache.demo.entity.Product;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 商品 MyBatis Mapper 接口。
 * 使用注解方式定义 SQL，无需 XML 文件。
 *
 * @author azir
 */
@Mapper
public interface ProductMapper {

    @Select("SELECT * FROM product WHERE id = #{id}")
    Product selectById(Long id);

    @Select("SELECT * FROM product")
    List<Product> selectAll();

    @Insert("INSERT INTO product(name, price, stock, category) VALUES(#{name}, #{price}, #{stock}, #{category})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Product product);

    @Update("UPDATE product SET name=#{name}, price=#{price}, stock=#{stock}, category=#{category} WHERE id=#{id}")
    int updateById(Product product);

    @Delete("DELETE FROM product WHERE id = #{id}")
    int deleteById(Long id);
}
