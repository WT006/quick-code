package org.example.quickcode;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("org.example.quickcode.mapper")
@EnableCaching  //支持SpringData缓存注解
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
public class QuickCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickCodeApplication.class, args);
    }

}
