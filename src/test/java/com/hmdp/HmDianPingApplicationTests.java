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
        for(long id = 1; id <= 14; id++){
            Shop shop = shopService.getById(id);
            cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+id,shop,10L,
                    TimeUnit.SECONDS);
        }


    }




}
