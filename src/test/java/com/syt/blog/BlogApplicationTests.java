package com.syt.blog;

import com.syt.blog.controller.BlogUserController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BlogApplicationTests {
    @Autowired
    BlogUserController blogUserController;

    @Test
    void contextLoads() {


    }

}
