package com.hmdp.config;

import org.redisson.Redisson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 包名：com.hmdp.config
 * 用户：admin
 * 日期：2026-04-15
 * 项目名称：hm-dianping
 */
@Configuration
public class RedissonClient {

    @Bean RedissonClient redissonClient(){
        
        return Redisson.create();
    }
}
