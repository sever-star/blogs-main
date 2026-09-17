package com.syt.blog.service;

import com.syt.blog.DTO.Mapper.TagMapper;
import com.syt.blog.DTO.Response.TagResponse;
import com.syt.blog.common.DuplicateNameException;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.DTO.Request.TagRequest;
import com.syt.blog.jooq.tables.daos.BlogTagsDao;
import com.syt.blog.jooq.tables.pojos.BlogTags;
import com.syt.blog.repository.TagRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlogTagService {

    private final TagRepository tagRepository;
    private final BlogTagsDao blogTagsDao;
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 保存标签
     * @param blogTags
     * @return
     */
    @Transactional
    public TagResponse saveBlogTag(BlogTags blogTags) {
        if (tagRepository.existsByName(blogTags.getName())) {
            throw new DuplicateNameException("标签名称重复");
        }
        blogTagsDao.insert(blogTags);
        log.info("保存标签：{}", blogTags);
        TagResponse tagResponse = new TagResponse();
        tagResponse = TagMapper.INSTANCE.blogTagsToTagResponse(blogTags);

        return tagResponse;
    }

    /**
     * 获取全量标签（供选择器使用，无分页）。
     * 支持可选 keyword 名称模糊过滤。
     *
     * @param keyword 名称关键词，可为空
     * @return 标签全量列表
     */
    public List<TagResponse> getAllBlogTags(String keyword) {
        List<TagResponse> tagResponses = new ArrayList<>();
        List<BlogTags> blogTags;
        if (keyword != null && !keyword.trim().isEmpty()) {
            blogTags = blogTagsDao.fetchByName(keyword);
        }else{
            blogTags=blogTagsDao.findAll();
        }
        for(BlogTags blogTag : blogTags) {
            TagResponse tagResponse=TagMapper.INSTANCE.blogTagsToTagResponse(blogTag);
            tagResponses.add(tagResponse);
        }
        return tagResponses;
    }

    /**
     * 分页获取标签（供管理表格使用，支持 keyword 模糊搜索）。
     *
     * @param keyword  名称关键词，可为空
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult getBlogTagsPaged(String keyword, Integer page, Integer pageSize) {
        PageResult pageResult;
        if (keyword != null && !keyword.trim().isEmpty()) {
            pageResult = tagRepository.findPaged(page, pageSize, keyword);
        } else {
            pageResult = tagRepository.findByNameContaining(page, pageSize);
        }
        return pageResult;
    }
    /**
     * 根据id获取标签
     * @param id
     * @return
     */
    public TagResponse getById(Integer id) {
        BlogTags blogTag = blogTagsDao.findById(id);
        if (blogTag == null){
            throw new ResourceNotFoundException("标签不存在");
        }
        TagResponse tagResponse;
        tagResponse = TagMapper.INSTANCE.blogTagsToTagResponse(blogTag);
        return tagResponse;
    }
    /**
     * 更新标签
     * @param id
     * @param blogTags
     * @return
     */

    @Transactional
    public TagResponse updateBlogTag(Integer id, BlogTags blogTags) {
        BlogTags blogTag = blogTagsDao.findById(id);
        if (blogTag == null) {
            throw new ResourceNotFoundException("标签不存在");
            
        }
        boolean exists = tagRepository.existsByNameAndIdNot(blogTags.getName().trim(), id);
        if (exists)
            throw new DuplicateNameException("标签名称重复");
        blogTags.setId(id);
        log.info("更新标签：{}",id);
        blogTag= tagRepository.update(blogTags);
        log.info("更新标签：{}", blogTag);
        TagResponse tagResponse = TagMapper.INSTANCE.blogTagsToTagResponse(blogTag);
        return tagResponse;
    }
    /**
     * 删除标签
     * @param id
     */
    @Transactional
    public void deleteBlogTag(Integer id) {
        BlogTags blogTag = blogTagsDao.findById(id);
        if (blogTag == null) {
            throw new ResourceNotFoundException("标签不存在");
        }
        blogTagsDao.deleteById(id);
        // TODO 删除标签需要清除和文章之间的关联
    }
}
