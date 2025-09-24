package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKIKK_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    //当前类初始化完毕之后执行
    private void init() {
        SECKIKK_ORDER_EXECUTOR.submit(new VoucherOrderHander());
    }

    private class VoucherOrderHander implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //获取队列中订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常");
                }
            }

        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户
        Long userId = voucherOrder.getUserId();

        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order" + userId);
        // 然后再获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取锁失败，返回错误或者重试
            log.error(("不允许重复下单"));
            // return Result.fail("不允许重复下单");
        }


        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            proxy.createVoucherOrder(voucherOrder);
            // createVoucherOrder变成了一个带有事务的对象
        } finally {
            //释放锁
            lock.unlock();

        }
    }

    IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            //2.1不为0，没有购买资格，返回异常信息
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2为0，将优惠卷id，用户id，订单id存入阻塞队列
        //long orderId = redisIdWorker.nextId("order");
        //3异步下单//TODO 保存阻塞队列
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        //7.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        //7.2用户id
        voucherOrder.setUserId(userId);

        //7.3代金卷id
        voucherOrder.setVoucherId(voucherId);

        //创建阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 4返回订单id
        return Result.ok(0);
    }


//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        //判断开始时间在我们的当前时间之后，如果在这之后，则没开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//        //3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束！");
//        }
//        //4.库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足！");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//        //synchronized (userId.toString().intern())
//        //创建锁对象
//      // SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//       RLock lock = redissonClient.getLock("lock:order" + userId);
//        // 然后再获取锁
//        boolean isLock = lock.tryLock();
//        //判断是否获取锁成功
//        if (!isLock) {
//            //获取锁失败，返回错误或者重试
//            return Result.fail("不允许重复下单");
//        }
//
//        //这种调用方式实上是用this调用的
//         //相当于    return this.createVoucherOrder(voucherId);
//            //而不是他的代理对象
//            //而@Translational注解是spring对当前的类（VoucherOrderServiceImpl)做了动态代理
//            // ，拿到了VoucherOrderServiceImpl的代理对象，用这个代理对象做的事务处理
//            //而此时的return this.createVoucherOrder(voucherId);这个this指的是代理非对象（目标对象）没有事务功能
//            //这个是spring事务失效的情况之一
//
//            //所以我们需要拿到这个事务的代理对象才行
//            //得到的是IVoucherOrderService的代理对象(事务）
//        try {
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return  proxy.createVoucherOrder(voucherId);
//            // createVoucherOrder变成了一个带有事务的对象
//        } finally {
//            //释放锁
//            lock.unlock();
//
//        }
//
//    }
    //做一人一单的判断
    //根据优惠卷id和用户id查询订单

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 5. 一人一单
        Long userId =voucherOrder.getUserId();

        //5.1查询订单
        int count = query().eq("userId", userId).eq("voucherId", voucherOrder.getVoucherId()).count();
        //5.2判断订单是否存在
        if (count > 0) {
            //存在，返回异常(用户已经购买过了）
            log.error("用户已经购买过了");
        }

        //5.3.充足，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()) // 精准定位当前优惠券
                .gt("stock", 0) // 确保库存>0才扣减（防止超卖）
                .update();
        if (!success) {
            //库存不足
            log.error("库存不足");
        }

        save(voucherOrder);
    }
}


