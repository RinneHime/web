package cn.lili.common.verification.aop.annotation;


import cn.lili.common.verification.enums.VerificationEnums;

import java.lang.annotation.*;

/**
 * 限流注解
 *
 * @author Chopper
 * @since 2018-02-05
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Verification {
    /**
     * uuid
     *
     * @return String
     */
    String uuid();

    /**
     * 验证类型
     *
     * @return
     */
    VerificationEnums type();
}