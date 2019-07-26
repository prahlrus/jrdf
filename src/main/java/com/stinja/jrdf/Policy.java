package com.stinja.jrdf;

/**
 * An object representing the policy of a @LiteralValued or a
 * @ResourceValued field.
 * 
 * If the nullable is false, then an Exception will be thown when
 * trying to persist an object that has a "null" for of an empty
 * Collection for that field. If a non-nullable field cannot be
 * populated during retrieval, an Exception will be thrown.
 *
 * If isCollection is true, then the type of the field must be 
 * a java.util.Collection. If isCollection is false, the type must
 * not be a Collection.
 *
 * The IDENTIFIER policy is semantically identical to the 
 * EXACTLY_ONE policy, but indicates that the property should
 * be used when identifying this Object with a query. This policy
 * is ignored if class is annotated as a @Resource, since that
 * annotation specifies an idField.
 */

public enum Policy {
	/** A nullable non-Collection. */
	ONE 					(true, false),
	/** A non-null non-Collection. */
	EXACTLY_ONE 	(false, false),	
	/** A non-null non-Collection, used as an identifier. */
	IDENTIFIER 		(false, false),
	/** A (possibly empty) Collection. */
	MANY					(true, true),
	/** A nonempty Collection. */
	SOME					(false, true);		

	public final boolean nullable;
	public final boolean isCollection;
	
	Policy (boolean nullable, boolean isCollection) {
		this.nullable = nullable;
		this.isCollection = isCollection;
	}
}
