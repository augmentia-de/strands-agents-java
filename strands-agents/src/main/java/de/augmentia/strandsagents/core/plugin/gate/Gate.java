package de.augmentia.strandsagents.core.plugin.gate;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Gate {
    GateType type();
    String duration() default "";
    String schedule() default "";
    String condition() default "";
    String on() default "";
}
