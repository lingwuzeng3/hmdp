package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    /**
     * 非空时优先使用（如 {@code redis://127.0.0.1:6379}）；否则与 {@code spring.redis.host/port} 拼接。
     */
    @Value("${redisson.single-server-address:}")
    private String redissonAddressOverride;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = (redissonAddressOverride != null && !redissonAddressOverride.isBlank())
                ? redissonAddressOverride
                : "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer().setAddress(address);
        return Redisson.create(config);
    }
}
