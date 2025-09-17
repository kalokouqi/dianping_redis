package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS  = 32;
    private final StringRedisTemplate stringRedisTemplate;

    //在 Java 中，这段代码的问题出在 构造函数不能有返回值类型，包括 void。具体原因如下：
    //public void RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1获取当天日期，精确到天
        String data = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //2.2自增长
        Long count = stringRedisTemplate.opsForValue().increment("inc" + keyPrefix + ":" + data);
        //3.拼接并返回
        return  timestamp << COUNT_BITS | count;

    }
    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }

}
