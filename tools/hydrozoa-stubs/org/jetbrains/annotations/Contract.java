package org.jetbrains.annotations; public @interface Contract { String value() default ""; boolean pure() default false; }
