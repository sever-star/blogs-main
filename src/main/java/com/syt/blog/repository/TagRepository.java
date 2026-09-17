package com.syt.blog.repository;

import com.syt.blog.common.PageResult;
import com.syt.blog.jooq.tables.pojos.BlogCategories;
import com.syt.blog.jooq.tables.pojos.BlogTags;
import com.syt.blog.jooq.tables.records.BlogCategoriesRecord;
import com.syt.blog.jooq.tables.records.BlogTagsRecord;
import org.jooq.DSLContext;
import org.jooq.SelectConditionStep;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static com.syt.blog.jooq.Tables.BLOG_CATEGORIES;
import static com.syt.blog.jooq.tables.BlogTags.BLOG_TAGS;

@Repository
public class TagRepository {
    private final DSLContext dsl;
    public TagRepository(DSLContext dsl) {
        this.dsl = dsl;
    }
    public PageResult findByNameContaining(int pageNumber,int pageSize) {
        List<BlogTags> tags = dsl.selectFrom(BLOG_TAGS).orderBy(BLOG_TAGS.ID.desc()).limit(pageSize).offset((pageNumber - 1) * pageSize).fetchInto(BlogTags.class);
        long total = dsl.fetchCount(BLOG_TAGS);
        return new PageResult(tags, total, pageNumber-1, pageSize, (int) Math.ceil(total / pageSize));
    }
    public boolean existsByName(String name){
        return dsl.fetchExists(dsl.selectFrom(BLOG_TAGS).where(BLOG_TAGS.NAME.eq(name)));
    }
    public boolean existsByNameAndIdNot(String name, Integer id) {
        return dsl.fetchExists(dsl.selectFrom(BLOG_TAGS).where(BLOG_TAGS.NAME.eq(name).and(BLOG_TAGS.ID.ne(id))));
    }
    public PageResult findPaged(int pageNumber, int pageSize, String keyword){
        SelectConditionStep<BlogTagsRecord> where = dsl.selectFrom(BLOG_TAGS).where(BLOG_TAGS.NAME.like("%" + keyword + "%"));
        List<BlogTags> result = where.orderBy(BLOG_TAGS.ID.desc()).limit(pageSize).offset((pageNumber - 1) * pageSize).fetchInto(BlogTags.class);
        long total = where.fetchStream().count();
        PageResult pageResult=new PageResult(result,total,pageNumber-1,pageSize,(int)Math.ceil(total/pageSize));
        return pageResult;
    }
    public BlogTags update(BlogTags blogTags) {
        BlogTagsRecord record=dsl.newRecord(BLOG_TAGS, blogTags);
        for (int i=0; i<record.size(); i++){
            if (record.get(i)==null){
                record.changed(i,false);
            }
        }
        record.update();
        return record.into(BlogTags.class);
    }
}
