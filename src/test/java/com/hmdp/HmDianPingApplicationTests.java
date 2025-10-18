package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;


@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private IShopService shopService;

    @Autowired
    public CacheClient cacheClient;

    @Test
    void testShop(){
        Shop shop = shopService.getById(5L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+5,shop,10L,
                TimeUnit.SECONDS);

    }


}
