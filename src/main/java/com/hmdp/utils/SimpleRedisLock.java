package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    //线程标识
    private static final String ID_PREFIX = UUID.fastUUID().toString(true) + "-";

    private static final String KEY_PREFIX = "lock";

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取锁
        //获取线程标识
        //long threadId = Thread.currentThread().getId();
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        //获取线程id
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //防止success为空，空指针
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        //判断标识是否一致
        if (threadId.equals(id)) {

            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);

        }
    }
}