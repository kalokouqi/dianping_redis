--1.参数列表

--1.1优惠劵id
local voucherId = ARGV[1]
--1.2用户id
local userId = ARGV[2]


--2.数据key

--2.1库存key
local stockKey = 'seckill:stock'..VoucherId
--2.2订单key
local orderKeyKey = 'seckill:Order'..voucher

--3业务脚本
--3.1判断库存是否充足
if(tonumber(redis.call('get',stockKey))<=0)then
    --3.2否，返回
    return 1
end
--3.3充足，判断用户是否下单 SISMEMBER orderKey,userId
if (redis.call('sismenber',orderKey,userId) ==1)then
    --4.用户下过单，返回2

    return 2
end
--5.用户没下过单
--5.1扣减库存
redis.call('incrby',stockKey,-1)
--5.2将userI存入当前优惠卷的set集合
redis.call('sadd',orderKey,userId)
return 0