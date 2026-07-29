package com.snippetvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    // StringRedisTemplate is a narrower version of RedisTemplate that uses
    // String serializers for both keys and values out of the box.
    // This is exactly what we need for rate limiting counters:
    //   key   → "rate_limit:127.0.0.1"   (String)
    //   value → "7"                        (String, cast to Long via Redis INCR)
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}