package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
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

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate redisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate, @Qualifier("redisTemplate") RedisTemplate redisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ShopType> queryTypeList() {
        //1.得到的是list，然后在redis操作要转换成json才可以
        String key = "login:type";

        //2.第一步去redis里面查有没有
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        //2.1判断
        //if (StrUtil.isNotBlank(shopTypeJson)) {
        if(StrUtil.isNotBlank(shopTypeJson)) {
            //2.2如果有，就返回结果,注意要把从redis取出来的json转为list再返回给前端
            List<ShopType> TypeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return TypeList;
        }
            //2.3如果没有，就去数据库找
        //等同于 SELECT * FROM shop_type ORDER BY sort ASC;
            List<ShopType> typeList = this.query().orderByAsc("sort").list();


            //3判断数据库有没有
        // 3.1数据库如果有就写到redis，然后返回结果，注意要返回list而不是json
        //4.数据库也没有就返回空链表
        if (typeList.isEmpty()) {

          //return ("status"+401);因为返回值是list，所以不能这么写
            return new ArrayList<>();
        }
        //4.1如果成功
        //把数据库的写到redis
        //返回链表给前端
        // 6. 存在，写入redis
        //这句写错了redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList)).
       stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        // 7. 返回
        return typeList;


    }
}
