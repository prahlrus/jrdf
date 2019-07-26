package com.stinja.jrdf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Any field that is a reference to a class annotated as a @Resource 
 * must bear this annotation. The referenced field must be annotated as
 * a @Resource, since the RDF generated to represent the field will
 * use the URI of that representation.
 */

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LiteralValued {
	/**
	 * Needed to construct runtime objects of the appropriate class. If 
	 * the field is a Collection, this member should reflect the class
	 * of the elements of the Collection.
	 */
	Class valueClazz();

	/**
	 * Represents the policy for the runtime behavior of this Field.
	 */
	Policy policy() default Policy.ONE;
}
