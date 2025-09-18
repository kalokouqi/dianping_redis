package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock( String name,StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock";

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取锁
        //获取线程标识
        long threadId = Thread.currentThread().getId();
        //获取线程id
        Boolean success = stringRedisTemplate.opsForValue()

                .setIfAbsent(KEY_PREFIX +name, threadId+"", timeoutSec, TimeUnit.SECONDS);
        //防止success为空，空指针
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //释放锁
        stringRedisTemplate.delete(KEY_PREFIX +name);

    }
}
