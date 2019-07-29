package com.stinja.jrdf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation may be born on packages or on classes, and is
 * used by the system to generate SPARQL 'PREFIX' statements. If
 * it is not used, all @FooProperty.property fields must bear the
 * full URI of the RDF property used to persist that field.
 *
 * If multiple @ResourcePrefix annotations are present for the same
 * prefix, the most-specific one takes precedence. Thus, if both
 * the package and the class define the "schema:" prefix differently,
 * the class's definition will be used for all operations involving
 * that class, but the package definition will be used for other
 * classes in that package that do not provide their own definition
 * of it.
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ResourcePrefices.class)
public @interface ResourcePrefix {
	/**
	 * The prefix used queries.
	 */
	String abbreviated();

	/**
	 * The full URI prefix.
	 */
	String full();
}