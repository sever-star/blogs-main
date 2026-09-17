package com.syt.blog.DTO.Mapper;

import com.syt.blog.DTO.Request.CategoryRequest;
import com.syt.blog.DTO.Response.CategoryResponse;
import com.syt.blog.jooq.tables.pojos.BlogCategories;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CategoryMapper {
    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    @Mapping(target="parentId",ignore = true)
    @Mapping(target="sortOrder",ignore = true)
    @Mapping(target="createdAt",ignore = true)
    BlogCategories categoryRequestToBlogCategories(CategoryRequest categoryRequest);


    CategoryResponse blogCategoriesToCategoryResponse(BlogCategories blogCategories);

}
