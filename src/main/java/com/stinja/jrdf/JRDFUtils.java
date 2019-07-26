package com.stinja.jrdf;

import java.lang.reflect.Field;

import java.util.Collection;
import java.util.Iterator;

import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;

public class JRDFUtils {

	/* A String literal that is about to be persisted MUST match the following
	 * regex, or else it might contain malicious SPARQL Update queries.
	 * Note that only \x22 is disallowed, since we generate literals enclosed
	 * in DOUBLE quotes. An escaped \x27 is still valid, however, as recommended
	 * by the SPARQL grammar. */

	private static final String rdfPlainLiteral = "(([^\\x22\\x5C\\x0A\\x0D])|\\\\[tbnrf\\\"\\\'])*";
	private static final Pattern plainLiteralPattern = Pattern
			.compile(rdfPlainLiteral);

	/**
	 * Method to generat a URI, for use in a SPARQL Query. Only works
	 * on a @Resource class. If @Resource.abbreviated() is true, then
	 * the URI will not have enclosing angle braces, since those are not
	 * required if a prefix is used. Otherwise, it will have enclosing 
	 * angle braces.
	 *
	 * The URI pattern is: (<){prefix}{unqualified class name}.{hashCode}(>)
	 * Where the hashCode is the hashCode of the value stored in the idField.
	 *
	 * @see Resource
	 */
	public static String getUri(Object o) 
	throws JRDFAnnotationException {
		Class resourceClazz = o.getClass();
		if (resourceClazz.isAnnotationPresent(Resource.class)) {
			Resource r =  (Resource) resourceClazz.getAnnotation(Resource.class);
			Field idField;
			Object idValue;
			try {
				idField = resourceClazz.getField(r.idField());
				if (idField.getType() != String.class &&
					!idField.getType().isPrimitive()) {
					throw JRDFAnnotationException.badlyAnnotatedClass(
						resourceClazz,
						String.format("The identifier field %s was neither a String or a primitive type!"));
				}
				idField.setAccessible(true);
				idValue = idField.get(o);
				if (r.abbreviated())
					return String.format(
						"%s%s.%x",
						r.uriPrefix(),
						resourceClazz.getSimpleName(),
						idValue.hashCode()
					);
				else 
					return String.format(
						"<%s%s.%x>",
						r.uriPrefix(),
						resourceClazz.getSimpleName(),
						idValue.hashCode()
					);
			} catch (NoSuchFieldException e) {
				throw JRDFAnnotationException.badlyAnnotatedClass(
				resourceClazz,
				String.format("The class has no field '%s'.",
					r.idField())
				);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(
					String.format(
						"Encountered an IllegalArgumentException when trying to access the field %s on the class %s.",
						r.idField(),
						resourceClazz.getCanonicalName()
					)
				);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(
					String.format(
						"Encountered an IllegalAccessException when trying to access the field %s on the class %s.",
						r.idField(),
						resourceClazz.getCanonicalName()
					)
				);
			} catch (NullPointerException e) {
				throw IllegalValueException.nullIdField(
					resourceClazz,
					r.idField()
				);
			}
		} else {
			throw JRDFAnnotationException.badlyAnnotatedClass(
				resourceClazz,
				"Is not annotated with @Resource and is persisted as blank nodes."
			);
		}
	}

	/**
	 * Tests whether, for JRDF's purposes, two runtime objects are 
	 * equivalent. Returns true if both are null or both are non-null
	 * instances of the same class and either their URIs or their
	 * identifying patterns match.
	 */
	public static boolean equivalent(Object o0, Object o1) 
		throws JRDFAnnotationException {
		if (o0 == null && o1 == null)
			return true;
		if (o0 == null || o1 == null)
			return false;

		if (o0.getClass() != o1.getClass())
			return false;
		Class resourceClazz = o0.getClass();
		if (resourceClazz.isAnnotationPresent(Resource.class))
			return getUri(o0).equals(getUri(o1));
		else
			return getIdentifyingPattern("?o", o0)
				.equals(getIdentifyingPattern("?o", o1));
	}

	/**
	 * Method to get the SPARQL INSERT pattern to presist a given
	 * object's various properties.
	 */
	public String getLazyInsertPattern(String node, Object o) 
	throws JRDFAnnotationException {
		Class resourceClazz = o.getClass();		
		StringBuilder sb = new StringBuilder(node);			
		boolean noFields = true;

		for (Field f : resourceClazz.getDeclaredFields()) {
			if (! f.isAnnotationPresent(PropertyField.class))
				continue;
			PropertyField pf = f.getAnnotation(PropertyField.class);
			
			if (noFields) 
				noFields = false;
			else 
				sb.append(" ;\n\t\t"); // not the first identifying field

			String idValue = fieldToSparql(o, f, pf);
			if (! pf.policy().nullable && idValue == null)
				throw IllegalValueException.nullField(
					resourceClazz, 
					f,
					pf
				);

			sb.append(
				String.format(
					pf.abbreviated() ? " %s%s %s" : " <%s%s> %s",
					pf.propertyPrefix(),
					pf.rdfProperty(),
					idValue
					)
				);
		}

		if (noFields) { // no @PropertyFields to persist
			return null;
		} else {
			sb.append(".");
			return sb.toString();
		}
	}

	/**
	 * Method to get a pattern to use in a SPARQL query to identify
	 * the blank node representing an Object.
	 */
	public static String getIdentifyingPattern(String varName, Object o)
	throws JRDFAnnotationException {
		Class resourceClazz = o.getClass();		
		StringBuilder sb = new StringBuilder(varName);		

		if (!resourceClazz.isAnnotationPresent(Resource.class)) {
			boolean hasIdentifier = false;
			for (Field f : resourceClazz.getDeclaredFields()) {
				if (! f.isAnnotationPresent(PropertyField.class))
					continue;
				PropertyField pf = f.getAnnotation(PropertyField.class);
				if (pf.policy() != Policy.IDENTIFIER)
					continue;
				
				if (! hasIdentifier) 
					hasIdentifier = true; // first identifying field
				else 
					sb.append(" ;\n\t\t"); // not the first identifying field

				String idValue = fieldToSparql(o, f, pf);
				if (idValue == null)
					throw IllegalValueException.nullField(
						resourceClazz, 
						f,
						pf
					);

				sb.append(
					String.format(
						pf.abbreviated() ? " %s%s %s" : " <%s%s> %s",
						pf.propertyPrefix(),
						pf.rdfProperty(),
						idValue
						)
					);
			}
		} else {
			throw JRDFAnnotationException.badlyAnnotatedClass(
				resourceClazz,
				String.format("An instance of a @Resource class must be fetched by URI!")
			);
		}

		return sb.toString();
	}

	/**
	 * Creates the SQARQL query matching a field f on object o.
	 */
	private static String fieldToSparql(Object o, Field f, PropertyField pf) 
		throws JRDFAnnotationException {
		Class resourceClazz = o.getClass();
		f.setAccessible(true);
		if (pf.policy().isCollection) {
			StringBuilder sb = new StringBuilder();
			Collection<Object> coll;

			try {
				coll = (Collection<Object>) f.get(o);
				if (coll == null) return null;
				if (coll.size() == 0) return null;

				Iterator<Object> iter = coll.iterator();
				while (iter.hasNext()) {
					Object val = iter.next();
					if (pf.valueClazz().isAnnotationPresent(Resource.class))
						sb.append(getUri(val));
					else {
						sb.append(valToSparql(val));
					}

					if (iter.hasNext()) 
						sb.append(" , "); // more objects to go
				}

				return sb.toString();
			} catch (ClassCastException e) {
				throw JRDFAnnotationException.badlyAnnotatedProperty(
					resourceClazz,
					f,
					"Marked as a collection field, but value could not be cast to java.lang.Collection."
				);
			}
				catch (IllegalArgumentException e) {
				throw new RuntimeException(
					String.format(
						"Encountered an IllegalArgumentException when trying to access the field %s on the class %s.",
						f.getName(),
						resourceClazz.getCanonicalName()
					)
				);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(
					String.format(
						"Encountered an IllegalAccessException when trying to access the field %s on the class %s.",
						f.getName(),
						resourceClazz.getCanonicalName()
					)
				);
			}
		} else {
			try {
				Object val =  f.get(o);
				if (val == null) return null;
				if (val instanceof java.util.Collection)
					throw JRDFAnnotationException.badlyAnnotatedProperty(
						resourceClazz,
						f,
						"Cardinality did not indicate collection, but value was a collection.");
				if (pf.valueClazz().isAnnotationPresent(Resource.class))
					return getUri(val);
				else {
					return valToSparql(val);
				}
			} 
			// catch (ClassCastException e) {
			// 	throw JRDFAnnotationException.badlyAnnotatedProperty(
			// 		resourceClazz,
			// 		f,
			// 		"Marked as a collection field, but value could not be cast to java.lang.Collection."
			// 	);
			// }
				catch (IllegalArgumentException e) {
				throw new RuntimeException(
					String.format(
						"Encountered an IllegalArgumentException when trying to access the field %s on the class %s.",
						f.getName(),
						resourceClazz.getCanonicalName()
					)
				);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(
					String.format(
						"Encountered an IllegalAccessException when trying to access the field %s on the class %s.",
						f.getName(),
						resourceClazz.getCanonicalName()
					)
				);
			}
		}
	}

	/**
	 * Creates a SPARQL literal representing o.
	 */
	private static String valToSparql(Object o) {
		if (o instanceof java.lang.Enum)
			o = o.toString();

		Literal lit;
		if (o instanceof java.lang.String)
			lit = ResourceFactory.createPlainLiteral((String) o);
		else
			lit = ResourceFactory.createTypedLiteral(o);

		String escaped = StringEscapeUtils
			.escapeJava(lit.getLexicalForm());
		if (!plainLiteralPattern.matcher(escaped).matches())
			throw IllegalValueException.literalMismatch(escaped);

		if (lit.getDatatype() != null)
			return String.format("\"%s\"^^<%s>",
				escaped, lit.getDatatypeURI());
		return String.format("\"%s\"",
				escaped);
	}

}