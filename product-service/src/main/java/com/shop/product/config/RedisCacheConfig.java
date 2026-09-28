package com.shop.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisCacheConfig {

    public static final String PRODUCTS_CACHE = "products";

    /** Cấu hình chung: key dạng String, value dạng JSON, không cache null. */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
    }

    /** Cache "products" có TTL 30 phút. Key trong Redis: products::<product_id> */
    @Bean
    public RedisCacheManagerBuilderCustomizer productsCacheCustomizer(
            RedisCacheConfiguration base,
            @Value("${app.cache.products-ttl:30m}") Duration ttl) {
        return builder -> builder.withCacheConfiguration(PRODUCTS_CACHE, base.entryTtl(ttl));
    }
}
