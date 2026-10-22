package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementRequest {
    @NotEmpty
    @Size(min = 1, max = 100)
    private String title;
    @NotEmpty
    @Size(min = 1, max = 450)
    private String content;
}
