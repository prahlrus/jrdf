package com.stinja.jrdf;

import java.lang.reflect.Field;

/**
 * This is the type of Exception thrown to indicate that a runtime
 * Object cannot be reliably identified in the RDF graph.
 */

public class IllegalValueException extends RuntimeException {
	private IllegalValueException(String message) {
		super(message);
	}

	public static IllegalValueException nullField(
		Class clazz,
		Field field,
		PropertyField pf
		) {
		return new IllegalValueException(
			String.format(
				"The field %s on the class %s has policy %s and cannot be null or empty.",
				field.getName(),
				clazz.getCanonicalName(),
				pf.policy().toString()
			)
		);
	}

	public static IllegalValueException nullIdField(
		Class clazz,
		Field field
		) {
		return new IllegalValueException(
			String.format(
				"The field %s is the identifier of the @Resource %s and cannot be null.",
				field.getName(),
				clazz.getCanonicalName()
			)
		);
	}

	public static IllegalValueException nullIdField(
		Class clazz,
		String fieldName
		) {
		return new IllegalValueException(
			String.format(
				"The field %s is the identifier of the @Resource %s and cannot be null.",
				fieldName,
				clazz.getCanonicalName()
			)
		);
	}

	public static IllegalValueException literalMismatch(
		String s
		) {
		return new IllegalValueException(
			String.format(
				"The following escaped String did not match the Literal pattern: %s.",
				s
			)
		);
	}

}