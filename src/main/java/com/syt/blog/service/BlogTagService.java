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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogTagService {

    private final BlogTagRepository blogTagRepository;
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 保存标签
     */
    

    public BlogTag saveBlogTag(BlogTag blogTag) {
        boolean exists = blogTagRepository.existsByName(blogTag.getName());
        if (exists) {
            throw new DuplicateNameException("标签名称重复");
        }
        return blogTagRepository.save(blogTag);
    }

    /**
     * 获取所有标签
     */
    
    public Object getAllBlogTags(String keyword, Integer page, Integer size) {
        if (page != null || size != null){
            Pageable pageable = PageRequest.of(page-1, size);
            Page<BlogTag> pageResult;
            if (keyword != null) {
                pageResult =blogTagRepository.findByNameContaining(keyword, pageable);
            }else{
                pageResult =blogTagRepository.findAll(pageable);
            }
            return new PageResult(pageResult.getContent(),
                    pageResult.getTotalElements(),
                    pageResult.getNumber(),
                    pageResult.getSize(),
                    pageResult.getTotalPages());
        }

        return blogTagRepository.findAll();
    }
    /**
     * 根据id获取标签
     */

    
    public BlogTag getById(Integer id) {

        BlogTag blogTag = blogTagRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("标签不存在"));
        return blogTag;
    }
    /**
     * 更新标签
     */
    
    @Transactional
    public TagResponse updateBlogTag(Integer id, TagUpdateDTO tagUpdateDTO) {
        BlogTag blogTag = blogTagRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("标签不存在"));
        boolean exists = blogTagRepository.existsByNameAndIdNot(tagUpdateDTO.getName().trim(), id);
        if (exists)
            throw new DuplicateNameException("标签名称重复");
        blogTag.setName(tagUpdateDTO.getName().trim());
        TagResponse tagResponse = new TagResponse();
        tagResponse.setId(blogTag.getId());
        tagResponse.setName(blogTag.getName());
        tagResponse.setCreatedAt(blogTag.getCreatedAt().format(FMT));
        return tagResponse;
    }

    /**
     * 删除标签
     */
    
    public void deleteBlogTag(Integer id) {
        blogTagRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("标签不存在"));
        blogTagRepository.deleteById(id);
        // TODO 删除标签需要清除和文章之间的关联
    }
}
