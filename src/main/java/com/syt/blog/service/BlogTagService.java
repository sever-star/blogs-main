package com.syt.blog.service;

import com.syt.blog.Vo.TagResponse;
import com.syt.blog.dto.TagUpdateDTO;
import com.syt.blog.entity.BlogTag;

import java.util.List;

public interface BlogTagService {
    BlogTag saveBlogTag(BlogTag blogTag);

    List<BlogTag> getAllBlogTags();
    BlogTag getById(Integer id);

    TagResponse updateBlogTag(Integer id, TagUpdateDTO tagUpdateDTO );
}
