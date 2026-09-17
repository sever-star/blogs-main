package com.syt.blog;

import com.syt.blog.controller.BlogUserController;
import com.syt.blog.DTO.Request.UserRequest;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import com.syt.blog.DTO.Mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
class BlogApplicationTests {
    @Autowired
    BlogUserController blogUserController;

    @Test
    void contextLoads() {

    }

}
