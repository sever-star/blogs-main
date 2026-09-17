package com.syt.blog.repository;

import com.syt.blog.common.PageResult;
import com.syt.blog.jooq.tables.pojos.BlogCategories;
import com.syt.blog.jooq.tables.records.BlogCategoriesRecord;
import org.jooq.DSLContext;
import org.jooq.SelectConditionStep;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.syt.blog.jooq.Tables.BLOG_CATEGORIES;


@Repository
public class CategoryRepository {
    private final DSLContext dsl;
    public CategoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PageResult findPaged(int pageNumber, int pageSize, String keyword){
        SelectConditionStep<BlogCategoriesRecord> where = dsl.selectFrom(BLOG_CATEGORIES).where(BLOG_CATEGORIES.NAME.like("%" + keyword + "%"));
        List<BlogCategories> result = where.orderBy(BLOG_CATEGORIES.ID.desc()).limit(pageSize).offset((pageNumber - 1) * pageSize).fetchInto(BlogCategories.class);
        long total = where.fetchStream().count();
        PageResult pageResult=new PageResult(result,total,pageNumber-1,pageSize,(int)Math.ceil(total/pageSize));
        return pageResult;
    }
    public PageResult findByNameContaining(int pageNumber,int pageSize) {
        List<BlogCategories> tags = dsl.selectFrom(BLOG_CATEGORIES).orderBy(BLOG_CATEGORIES.ID.desc()).limit(pageSize).offset((pageNumber - 1) * pageSize).fetchInto(BlogCategories.class);
        long total = dsl.fetchCount(BLOG_CATEGORIES);
        return new PageResult(tags, total, pageNumber-1, pageSize, (int) Math.ceil(total / pageSize));
    }
    public boolean existsByName(String name) {
        return dsl.fetchExists(dsl.selectFrom(BLOG_CATEGORIES).where(BLOG_CATEGORIES.NAME.eq(name)));
    }
    public boolean existsByNameAndIdNot(String name, Integer id) {
        return dsl.fetchExists(dsl.selectFrom(BLOG_CATEGORIES).where(BLOG_CATEGORIES.NAME.eq(name).and(BLOG_CATEGORIES.ID.ne(id))));
    }
    public BlogCategories update(BlogCategories blogCategories) {
        BlogCategoriesRecord record=dsl.newRecord(BLOG_CATEGORIES, blogCategories);
        for (int i=0; i<record.size(); i++){
            if (record.get(i)==null){
                record.changed(i,false);
            }
        }
        record.update();
        return record.into(BlogCategories.class);
    }
}
