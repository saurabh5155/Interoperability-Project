package com.healthcare.interop.common.audit;

import java.lang.annotation.*;

/**
 * Marks a controller method as an admin action that should be persisted
 * to the admin audit log.  Applied by AdminAuditAspect in each service.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminAudit {
    String action();
    String resource() default "";
}
