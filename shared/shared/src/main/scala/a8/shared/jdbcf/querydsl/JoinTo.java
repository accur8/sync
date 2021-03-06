package a8.shared.jdbcf.querydsl;

import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(ManyJoinTos.class)
public @interface JoinTo {
    String name();
    String expr();
    String to();
}
