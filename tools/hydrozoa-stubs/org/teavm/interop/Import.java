package org.teavm.interop; import java.lang.annotation.*; @Retention(RetentionPolicy.CLASS) public @interface Import { String module() default ""; String name(); }
