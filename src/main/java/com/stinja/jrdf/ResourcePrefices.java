package com.stinja.jrdf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Containing annotation type to allow multiple @ResourcePrefix
 * annotations on the same target.
 */

@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourcePrefices {
	ResourcePrefix[] value();
}