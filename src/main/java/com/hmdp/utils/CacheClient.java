package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.service.impl.ShopServiceImpl.CACHE_REBUILD_EXECUTOR;
import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private final ShopServiceImpl shopServiceImpl;

    public CacheClient(StringRedisTemplate stringRedisTemplate, ShopServiceImpl shopServiceImpl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shopServiceImpl = shopServiceImpl;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //写两个set
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    //写两个get方法


    //此处返回值并不确定
    //所以用泛型,dbFallbacks意为数据库失败的后续逻辑
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //从redis里面查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在，返回信息
            return JSONUtil.toBean(json, type);
        }
        //判断命中的是否为空值,如果if语句成立，说明说明shopJson里面是我们刚刚存进去的空字符串
        if (json != null) {
            return null;
        }
        //redis不存在，查找数据库(根据id查询）
        R r = dbFallback.apply(id);
        //数据库不存在
        if (r == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据库存在，把数据写入redis，再返回(timeunit是时间单位的意思）
        this.set(key, r, time, unit);
        return r;
    }

    //public Shop queryWithLogicalExpire(Long id)
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R>dbFallback, Long time, TimeUnit unit) {
        //1.从redis里面查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //3不存在，返回
            return null;
        }

        //4.如果命中，（需要先把json转化为对象，反序列化
        //错误写法Shop shop = JSONUtil.toBean(shopJson,.class);
        //TODO 我了个，我只能说多练，卧槽，牛逼
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断缓存是否过期
        if (expireTime.isBefore(LocalDateTime.now())) {
            //5.1未过期，直接返回店铺信息
            return r;
        }
        //5.2已过期，需要缓存重载
        //6.缓存重建
        //6.2尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.3判断是否获取锁
        //错误思路  if(!isLock) { //6.4没有获取到锁，返回商铺信息
        if (isLock) {
            //TODO 6.5获取到锁，开启独立线程,缓存重载
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //缓存重建
                    //this.saveShop2Redis(id,20L);

                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);

                    // 写入缓存
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });

        }
        return r;

    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", CACHE_NULL_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}