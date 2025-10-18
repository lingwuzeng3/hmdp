package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商户类型列表
     * @return
     */
    @Override
    public Result queryTypeList() {

        //1.redis中查询店铺类型
        String shopTypeKey = RedisConstants.CACHE_SHOP_TYPE_KEY;
        String shopTypeStr = stringRedisTemplate.opsForValue().get(shopTypeKey);

        //2.若redis中有,返回数据，商店类型永久存在不用刷新
        if(StrUtil.isNotEmpty(shopTypeStr)){
            List<ShopType> shopType = new ArrayList<>();
            String[] split = shopTypeStr.split(";");
            for(String s : split){
                shopType.add(JSONUtil.toBean(s, ShopType.class));
            }
        }

        //3.redis没有，向数据库查询
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        //4.把查出来的数据包装成字符串，存入redis
        StringBuilder jsonStr = new StringBuilder();
        for(ShopType shopType : shopTypeList){
            jsonStr.append(JSONUtil.toJsonStr(shopType));
        }
        stringRedisTemplate.opsForValue().set(shopTypeKey,jsonStr.toString());

        //5.返回
        return Result.ok(shopTypeList);
    }
}
