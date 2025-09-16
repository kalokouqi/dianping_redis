package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
    private final RedisTemplate redisTemplate;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate, @Qualifier("redisTemplate") RedisTemplate redisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Result queryById(Long id) {
        //从redis里面查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {

            //存在，返回信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //不存在，查找数据库(根据id查询）
        Shop shop = getById(id);
        //数据库存在，把数据写入redis，再返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));

        //数据库不存在，401
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
}
