package com.syt.blog.service.impl;

import com.syt.blog.Vo.TagResponse;
import com.syt.blog.dto.TagUpdateDTO;
import com.syt.blog.entity.BlogTag;
import com.syt.blog.repository.BlogTagRepository;
import com.syt.blog.service.BlogTagService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogTagServiceImpl implements BlogTagService {

    private final BlogTagRepository blogTagRepository;
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public BlogTag saveBlogTag(BlogTag blogTag) {
        boolean exists = blogTagRepository.existsByName(blogTag.getName());
        if (exists) {
            return null;
        }
        return blogTagRepository.save(blogTag);
    }

    @Override
    public List<BlogTag> getAllBlogTags() {
        return blogTagRepository.findAll();
    }

    @Override
    public BlogTag getById(Integer id) {
        BlogTag blogTag = blogTagRepository.findById(id).orElse(null);
        return blogTag;
    }

    @Override
    @Transactional
    public TagResponse updateBlogTag(Integer id, TagUpdateDTO tagUpdateDTO) {
        BlogTag blogTag = blogTagRepository.findById(id).orElseThrow(() -> new RuntimeException("标签不存在"));
        boolean exists = blogTagRepository.existsByNameAndIdNot(tagUpdateDTO.getName().trim(), id);
        if (exists)
            throw new RuntimeException("标签名称重复");
        blogTag.setName(tagUpdateDTO.getName().trim());
        TagResponse tagResponse = new TagResponse();
        tagResponse.setId(blogTag.getId());
        tagResponse.setName(blogTag.getName());
        tagResponse.setCreatedAt(blogTag.getCreatedAt().format(FMT));
        return tagResponse;
    }
}
