package io.paradaux.chestshop.breeze.Configuration.Annotations;

import io.paradaux.chestshop.breeze.Configuration.ValueParser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a parser
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Parser {
    /**
     * This option's comment
     *
     * @return Comment
     */
    public String value();
}
