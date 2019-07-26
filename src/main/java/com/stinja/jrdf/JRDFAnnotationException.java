package com.stinja.jrdf;

import java.lang.reflect.Field;

/**
 * This is the type of Exception thrown to indicate that a the JRDF
 * annotations have been incorrectly applied and therefore the 
 * requested operation cannot be determined or executed.
 */

public class JRDFAnnotationException extends Exception {
	private JRDFAnnotationException(String message) {
		super(message);
	}

	public static JRDFAnnotationException badlyAnnotatedClass(
		Class clazz,
		String reason
		) {
		return new JRDFAnnotationException(
			String.format(
				"The class %s is badly annotated: %s",
				clazz.getCanonicalName(),
				reason
			)
		);
	}

	public static JRDFAnnotationException badlyAnnotatedProperty(
		Class clazz,
		Field field,
		String reason
		) {
		return new JRDFAnnotationException(
			String.format(
				"The field %s on class %s is badly annotated: %s",
				field.getName(),
				clazz.getCanonicalName(),
				reason
			)
		);
	}

}