package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageRequest {
    @NotNull
    private Integer receiverId;
    @NotNull
    @NotEmpty
    @Size(min = 1, max = 2000)
    private String content;
}
