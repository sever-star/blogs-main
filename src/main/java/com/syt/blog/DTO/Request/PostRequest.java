package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {
    @NotBlank
    @Size(min = 1, max = 200)
    private String title;
    @NotBlank
    @Size(min = 50)
    private String contentMd;

    private String summary;

    private String coverImage;
    @NotBlank
    private Integer categoryId;
    @NotBlank
    private List<Integer> tags;
    @NotBlank
    private Integer readingTime=15;
    @NotBlank
    private Integer status=2;
    @NotBlank
    private Integer allowComment=1;
}
