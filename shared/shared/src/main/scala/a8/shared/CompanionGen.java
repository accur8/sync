package a8.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface CompanionGen {
    boolean messagePack() default false;
    boolean jdbcMapper() default false;
    boolean qubesMapper() default false;
    boolean circeCodec() default false;
    boolean jsonCodec() default false;
}
