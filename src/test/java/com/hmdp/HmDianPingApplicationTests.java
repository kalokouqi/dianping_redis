package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
private RedisIdWorker redisIdWorker;
    @Resource
    private ShopServiceImpl shopService;


   private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testSaveShop() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = ()-> {
            for (int i = 0; i < 100; i++) {// Lambda表达式定义
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };// 这里的分号是必须的，因为这是一个变量赋值语句的结束
            long begin = System.currentTimeMillis();

            for(int i=0;i<300;i++){
                es.submit(task);
            }
            latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time="+(end-begin));
        }
    }


