package io.queryanalyzer.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtendWith(NoNPlusOneExtension.class)
public @interface NoNPlusOne {
    

    int threshold() default 3;
    

    String[] ignore() default {};
}
