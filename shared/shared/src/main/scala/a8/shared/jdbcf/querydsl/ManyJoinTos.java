package a8.shared.jdbcf.querydsl;

import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ManyJoinTos {
    JoinTo[] value();
}
