package com.syt.blog.service;

import com.syt.blog.Vo.TagResponse;
import com.syt.blog.common.DuplicateNameException;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.dto.TagUpdateDTO;
import com.syt.blog.entity.BlogTag;
import com.syt.blog.repository.BlogTagRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlogTagService {

    private final BlogTagRepository blogTagRepository;
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 保存标签
     * @param tagUpdateDTO
     * @return
     */
    public TagResponse saveBlogTag(TagUpdateDTO tagUpdateDTO) {
        BlogTag blogTag = blogTagRepository.findByName(tagUpdateDTO.getName());
        if (blogTag != null) {
            throw new DuplicateNameException("标签名称重复");
        }
        blogTag.setName(tagUpdateDTO.getName());
        blogTagRepository.save(blogTag);
        log.info("保存标签：{}", blogTag);
        TagResponse tagResponse = new TagResponse();
        toTagResponse(blogTag, tagResponse);

        return tagResponse;
    }

    /**
     * 获取全量标签（供选择器使用，无分页）。
     * 支持可选 keyword 名称模糊过滤。
     *
     * @param keyword 名称关键词，可为空
     * @return 标签全量列表
     */
    public List<BlogTag> getAllBlogTags(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return blogTagRepository.findByNameContaining(keyword);
        }
        return blogTagRepository.findAll();
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
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<BlogTag> pageResult;
        if (keyword != null && !keyword.trim().isEmpty()) {
            pageResult = blogTagRepository.findByNameContaining(keyword, pageable);
        } else {
            pageResult = blogTagRepository.findAll(pageable);
        }
        return new PageResult(pageResult.getContent(),
                pageResult.getTotalElements(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalPages());
    }
    /**
     * 根据id获取标签
     * @param id
     * @return
     */
    public TagResponse getById(Integer id) {

        BlogTag blogTag = blogTagRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("标签不存在"));
        TagResponse tagResponse = new TagResponse();
        toTagResponse(blogTag, tagResponse);
        return tagResponse;
    }
    /**
     * 更新标签
     * @param id
     * @param tagUpdateDTO
     * @return
     */

    @Transactional
    public TagResponse updateBlogTag(Integer id, TagUpdateDTO tagUpdateDTO) {
        BlogTag blogTag = blogTagRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("标签不存在"));
        boolean exists = blogTagRepository.existsByNameAndIdNot(tagUpdateDTO.getName().trim(), id);
        if (exists)
            throw new DuplicateNameException("标签名称重复");
        blogTag.setName(tagUpdateDTO.getName().trim());
        TagResponse tagResponse = new TagResponse();
        toTagResponse(blogTag, tagResponse);
        return tagResponse;
    }
    /**
     * 删除标签
     * @param id
     */
    public void deleteBlogTag(Integer id) {
        blogTagRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("标签不存在"));
        blogTagRepository.deleteById(id);
        // TODO 删除标签需要清除和文章之间的关联
    }

    private void toTagResponse(BlogTag blogTag, TagResponse tagResponse) {
        tagResponse.setId(blogTag.getId());
        tagResponse.setName(blogTag.getName());
        tagResponse.setCreatedAt(blogTag.getCreatedAt().format(FMT));
    }
}
