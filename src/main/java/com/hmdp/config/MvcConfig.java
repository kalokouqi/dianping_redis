package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    //注册了一个LoginInterceptor实例，并传入了stringRedisTemplate作为依赖
    //通过excludePathPatterns方法指定了不需要被该拦截器拦截的路径
    //使用**作为通配符，表示匹配该路径下的所有子路径
    private StringRedisTemplate stringRedisTemplate;
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/upload/hot",
                        "/voucher/hot",
                        "/shop/**",
                        "/blog/hot",
                        "/shop-type/**" ,
                        "/user/code" ,
                        "/user/login"
                ).order(1);
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
