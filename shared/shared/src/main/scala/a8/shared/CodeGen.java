package a8.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
// any target
@Target({
        ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.LOCAL_VARIABLE,
        ElementType.METHOD,
        ElementType.PACKAGE,
        ElementType.PARAMETER,
        ElementType.TYPE,
        ElementType.TYPE_PARAMETER,
        ElementType.TYPE_USE
})
public @interface CodeGen {
    boolean messagePack() default false;
    boolean jdbcMapper() default false;
    boolean queryDsl() default false;
    boolean qubesMapper() default false;
    boolean circeCodec() default false;
    boolean jsonCodec() default false;
    boolean scala3() default false;
    boolean cats() default false;
    boolean zio() default false;
}
