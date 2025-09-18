package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Transactional
    //加上事务之后遇到问题可以及时回滚

   @Override
    public Result seckillVoucher(Long voucherId) {
       //1.查询优惠卷
       SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
       //2.判断秒杀是否开始
       //判断开始时间在我们的当前时间之后，如果在这之后，则没开始
       if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
           //尚未开始
           return Result.fail("秒杀尚未开始！");
       }
       //3.判断秒杀是否已经结束
       if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
           return Result.fail("秒杀已经结束！");
           }
           //4.库存是否充足
       if (voucher.getStock()<1) {
           return Result.fail("库存不足！");
       }


       //TODO 做一人一单的判断
        //根据优惠卷id和用户id查询订单
        Long userId = UserHolder.getUser().getId();
       //5.1查询订单
        int count = query().eq("userId", userId).eq("voucherId", voucherId).count();
        //5.2判断订单是否存在
        if (count > 0) {
            //存在，返回异常(用户已经购买过了）
            return Result.fail("用户已经购买过了");
        }

        //5.3.充足，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId) // 精准定位当前优惠券
                .gt("stock", 0) // 确保库存>0才扣减（防止超卖）
                .update();
        if (!success) {
            //库存不足
            return Result.fail("库存不足！");
        }
                 //6/创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

       //7.1订单id
       long orderId = redisIdWorker.nextId("order");
       voucherOrder.setId(orderId);

       //7.2用户id
       voucherOrder.setUserId(userId);

       //7.3代金卷id
       voucherOrder.setVoucherId(voucherId);
       save(voucherOrder);
       //7.返回订单id
           return Result.ok(orderId);
       }
   }


