package com.stinja.jrdf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
  * Any field of that is persisted using an RDF Property must bear
  * this annotation. If the property cannot be converted to a 
  * Literal value by Apache Jena, the field must also bear either
  * the @EnumeratedField or the @ResourceField annotations, for 
  * enumerate or reference types, respectively.
  */

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyField {
	/**
	 * The name (not including the prefix) of the RDF property that
	 * is used to persist this field.
	 */
	String rdfProperty();

	/**
	 * If there is no @UriPrefix annotation on the class or its 
	 * containing packages, this must be the fully qualified prefix
	 * that the URIs of instances are to bear. Thus, if the RDF
	 * property to be used has the URI:
	 *
	 * <http://www.stinja.com/jrdf/schema#fooProperty>
	 * 
	 * Either the annotation 

	 * @UriPrefix(
	 * 	prefix = "schema:", 
	 *	full = "http://www.stinja.com/jrdf/schema#"
	 * )
	 *
	 * Must be present on the class (or a containing package) or the
	 * propertyPrefix field must be "http://www.stinja.com/jrdf/schema#"
	 * rather than "schema:"
	 */
	String propertyPrefix() default "schema:";

	/**
	 * Whether the propertyPrefix() is abbreviated (that is, if the 
	 * prefix is NOT the fully qualified URI prefix). This is needed 
	 * to decide whether to enclose the uri in angle brackets.
	 * 
	 * @see propertyPrefix()
	 */
	boolean abbreviated() default true;

	/**
	 * Needed to construct runtime objects of the appropriate class. If 
	 * the field is a Collection, this member should reflect the class
	 * of the elements of the Collection.
	 */
	Class valueClazz();

	/**
	 * Represents the policy for the runtime behavior of this Field. Note
	 * that a Collection field must have the MANY or SOME policy.
	 */
	Policy policy() default Policy.ONE;
}