package com.stinja.jrdf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is born by a class whose isntances should receive
 * URIs when persisted. Instances that lack this annotation will be
 * persisted as blank nodes.
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Resource {
	/**
	 * When this annotation is present, only the field matching the 
	 * name of the idField is used as an identifier for the instance. 
	 * That field must cannot be null; if it is, a RuntimeException 
	 * should be thrown.
	 *
	 * Any other @EnumeratedField, @LiteralProperty or 
	 * @ResourceField fields have the Cardinality.IDENTIFIER will 
	 * be ignored, since it is not necessary to match against those 
	 * fields when a URI is available.
	 */
	String idField();

	/**
	 * If there is no @UriPrefix annotation on the class or its 
	 * containing packages, this must be the fully qualified prefix
	 * that the URIs of instances are to bear. Thus, if a Foo object
	 * is to receive a URI like:
	 *
	 * <http://www.stinja.com/jrdf/test_data#FooXXXX>
	 * 
	 * Either the annotation 

	 * @UriPrefix(
	 * 	prefix = "data:", 
	 *	full = "http://www.stinja.com/jrdf/test_data#"
	 * )
	 *
	 * Must be present on the class or a containing package or the
	 * uriPrefix field must be  "http://www.stinja.com/jrdf/test_data#"
	 * rather than "data:"
	 */
	String uriPrefix() default "data:";

	/**
	 * Whether the uriPrefix() is abbreviated (that is, if the prefix is
	 * NOT the fully qualified URI prefix).
	 * 
	 * @see uriPrefix()
	 */
	boolean abbreviated() default true;
}
