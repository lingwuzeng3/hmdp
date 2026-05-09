package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.MessageConstants;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate,
                           RedissonClient redissonClient){
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    /**
     * 根据id查询店铺信息--逻辑过期
     * @param id
     * @return
     * 缓存已存在但过期 → 逻辑过期方案（异步重建）
     * 缓存完全不存在 → 互斥锁方案（同步重建）
     */
    @Override
    public Result queryById(Long id) {

        //1.根据id得到redis中店铺信息
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        //2.若redis中不存在信息，需要从数据库查询并构建缓存
        if(StrUtil.isEmpty(shopJson)){
            // 缓存击穿解决：使用互斥锁
            return handleCacheBreakdown(id, shopKey);
        }

        //3.命中 反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = BeanUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //4.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期 直接返回
            return Result.ok(shop);
        }

        //5.已过期,获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        RLock lock = redissonClient.getLock(lockKey);
        
        // 尝试获取锁，不等待，立即返回
        boolean flag = lock.tryLock();
        
        //是否获取锁成功
        if (flag) {
            //成功 异步重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShopToRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁 - Redisson 保证只释放当前线程持有的锁
                    lock.unlock();
                }
            });
        }
        //返回过期商铺信息
        return Result.ok(shop);
    }

    /**
     * 处理缓存击穿：缓存不存在时从数据库加载
     * @param id 店铺ID
     * @param shopKey Redis键
     * @return 查询结果
     */
    private Result handleCacheBreakdown(Long id, String shopKey) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 1. 获取互斥锁 - 最多等待1秒，锁自动释放时间10秒
            boolean isLock = lock.tryLock(1, 10, java.util.concurrent.TimeUnit.SECONDS);
            if (!isLock) {
                // 获取锁失败，休眠重试（改为循环而非递归）
                Thread.sleep(50);
                return queryById(id); // 递归查询
            }
            
            // 2. 获取锁成功，再次检查缓存（双重检查）
            String cacheData = stringRedisTemplate.opsForValue().get(shopKey);
            if (StrUtil.isNotEmpty(cacheData)) {
                // 其他线程已经重建了缓存
                return buildResultFromCache(cacheData);
            }
            
            // 3. 查询数据库
            Shop shop = getById(id);
            if (shop == null) {
                // 数据库也不存在，缓存空值防止缓存穿透
                stringRedisTemplate.opsForValue().set(
                    shopKey, "", 
                    RedisConstants.CACHE_NULL_TTL, 
                    java.util.concurrent.TimeUnit.MINUTES
                );
                return Result.fail(MessageConstants.SHOP_NOT_EXIST);
            }
            
            // 4. 数据库存在，写入Redis（使用逻辑过期）
            saveShopToRedis(id, 20L);
            
            // 5. 返回结果
            return Result.ok(shop);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("查询店铺被中断", e);
        } finally {
            // 释放锁 - Redisson 保证只释放当前线程持有的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 从缓存数据构建返回结果
     * @param shopJson 缓存中的JSON字符串
     * @return 查询结果
     */
    private Result buildResultFromCache(String shopJson) {

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = BeanUtil.toBean(jsonObject, Shop.class);
        return Result.ok(shop);
    }

    /**
     * 更新店铺
     * @param shop
     * @return
     */
    @Override
    public Result updateShop(Shop shop) {

        //1.判断该店铺是否存在
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺不存在");
        }

        //2.修改店铺信息
        updateById(shop);

        //3.删除redis中的缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok();

    }

    /**
     * 用一个公共redis数据充当锁,它的值随意
     * @param key
     * @return
     */
    public boolean tryLock(String key){
        boolean flag =  stringRedisTemplate.opsForValue().setIfAbsent(key,"1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 保存店铺到Redis（带逻辑过期时间）
     * @param id 店铺ID
     * @param expireSeconds 逻辑过期时间（秒）
     */
    public void saveShopToRedis(Long id, Long expireSeconds) {
        //查询店铺数据
        Shop shop = getById(id);
        
        //封装逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        
        //写入Redis
        stringRedisTemplate.opsForValue().set(
            RedisConstants.CACHE_SHOP_KEY + id, 
            JSONUtil.toJsonStr(redisData)
        );
    }
}
