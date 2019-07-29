package com.stinja.jrdf;

import java.lang.reflect.Field;

import java.math.BigInteger;
import java.math.BigDecimal;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Collection;

import java.util.Iterator;

import java.util.LinkedList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumSet;

import java.util.Iterator;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Literal;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.Dataset;

import org.apache.jena.update.UpdateAction;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;

import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;

public class PersistenceManager {
	/* A String literal that is about to be persisted MUST match the following
	 * regex, or else it might contain malicious SPARQL Update queries.
	 * Note that only \x22 is disallowed, since we generate literals enclosed
	 * in DOUBLE quotes. An escaped \x27 is still valid, however, as recommended
	 * by the SPARQL grammar. */

	private static final String rdfPlainLiteral = "(([^\\x22\\x5C\\x0A\\x0D])|\\\\[tbnrf\\\"\\\'])*";
	private static final Pattern plainLiteralPattern = Pattern
			.compile(rdfPlainLiteral);

	private Dataset ds;
	private Map<Class, String> prefixes;
	private Map<Class, String> uriStems;
	private String anonStem;
	private List<Class> literalTypes;

	public PersistenceManager(Dataset ds, String anonStem) {
		this.ds = ds;
		this.prefixes = new HashMap<Class, String>();
		this.uriStems = new HashMap<Class, String>();
		this.anonStem = anonStem;

		// These are the classes that can be persisted as Jena Literals
		literalTypes = new LinkedList<Class>();
    literalTypes.add(Integer.class);
    literalTypes.add(Double.class);
    literalTypes.add(Boolean.class);
    literalTypes.add(Long.class);
    literalTypes.add(Byte.class);
    literalTypes.add(Short.class);
    literalTypes.add(Float.class);
    literalTypes.add(BigInteger.class);
    literalTypes.add(BigDecimal.class);
	}

	private static String ORIGIN_VARNAME = "?origin";

	/* ############# INITIALIZATION ############# */

	/**
	 * Pre-process a class to find any and all @ResourcePrefix
	 * annotations on it or on its package. All SPARQL queries
	 * regarding that particular class will use those prefixes.
	 */
	public void manageClass(Class clazz) {
		if (! prefixes.containsKey(clazz)) {
			IdentifiedByField id = clazz.isAnnotationPresent(IdentifiedByField.class) 
				? (IdentifiedByField) clazz.getAnnotation(IdentifiedByField.class) : null;
			String uriPrefix = (id != null) ? id.uriPrefix() : null;

			Map <String, String> draftPrefixes = 
				new HashMap<String, String>();
			Package currentPrefixes = clazz.getPackage();
			if (currentPrefixes != null && currentPrefixes.isAnnotationPresent(ResourcePrefix.class)) {
				for (ResourcePrefix up : (ResourcePrefix[]) currentPrefixes.getAnnotationsByType(ResourcePrefix.class)) {
					draftPrefixes.put(up.abbreviated(), up.full());
					if (up.abbreviated().equals(uriPrefix))
						uriPrefix = up.full();
				}
			}
			if (clazz.isAnnotationPresent(ResourcePrefix.class)) {
				for (ResourcePrefix up : (ResourcePrefix[]) clazz.getAnnotationsByType(ResourcePrefix.class)) {
					draftPrefixes.put(up.abbreviated(), up.full());
					if (up.abbreviated().equals(uriPrefix))
						uriPrefix = up.full();
				}
			}
			StringBuilder sb = new StringBuilder();
			for (Map.Entry e : draftPrefixes.entrySet())
				sb.append(String.format(
						"PREFIX %s <%s>%n",
						e.getKey(),
						e.getValue()
					)
				);
			prefixes.put(clazz, sb.toString());
			if (id != null)
				uriStems.put(
					clazz,
					uriPrefix + (	(id.uriLabel().equals("")) ? 
												clazz.getName() : 
												id.uriLabel())
				);
		}
	}

	/**
	 * Calls manageClass() on the given classes.
	 * @see manageClass()
	 */
	public void manageClasses(Class[] clazzes) {
		for (Class clazz : clazzes)
			manageClass(clazz);
	}

	/**
	 * Calls manageClass() on the given classes.
	 * @see manageClass()
	 */
	public void manageClasses(Collection<Class> clazzes) {
		for (Class clazz : clazzes)
			manageClass(clazz);
	}

	/**
	 * Method to generate a URI, for use in a SPARQL Query. Only works
	 * on a @IdentifiedByField class. If @IdentifiedByField.abbreviated() is true, then
	 * the URI will not have enclosing angle braces, since those are not
	 * required if a prefix is used. Otherwise, it will have enclosing 
	 * angle braces.
	 *
	 * The URI pattern is: (<){prefix}{unqualified class name}.{hashCode}(>)
	 * Where the hashCode is the hashCode of the value stored in the idField.
	 *
	 * @see IdentifiedByField
	 */
	public String getURI(Object o) 
	throws JRDFAnnotationException {
		Class clazz = o.getClass();
		if (clazz.isAnnotationPresent(IdentifiedByField.class)) {
			IdentifiedByField r =  (IdentifiedByField) clazz.getAnnotation(IdentifiedByField.class);
			Field idField;
			Object idValue;
			try {
				idField = clazz.getField(r.idField());
				idField.setAccessible(true);
				idValue = idField.get(o);
				if (!uriStems.containsKey(clazz))
					throw new RuntimeException(
						String.format("%s is not a class managed by this PersistenceManager.", 
							clazz.getCanonicalName()));

				return String.format("<%s.%08x>", uriStems.get(clazz), (int) idValue);
			} catch (NoSuchFieldException e) {
				throw JRDFAnnotationException.badlyAnnotatedClass(
				clazz,
				String.format("The class has no field '%s'.",
					r.idField())
				);
			} catch (ClassCastException e) {
				throw JRDFAnnotationException.badlyAnnotatedClass(
						clazz,
						String.format("The identifier field %s could not be cast to an integer.", r.idField()));
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(
					String.format(
						"Encountered an IllegalArgumentException when trying to access the field %s on the class %s.",
						r.idField(),
						clazz.getCanonicalName()
					)
				);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(
					String.format(
						"Encountered an IllegalAccessException when trying to access the field %s on the class %s.",
						r.idField(),
						clazz.getCanonicalName()
					)
				);
			} catch (NullPointerException e) {
				throw IllegalValueException.nullIdField(
					clazz,
					r.idField()
				);
			}
		} else {
			throw JRDFAnnotationException.badlyAnnotatedClass(
				clazz,
				"Is not annotated with @IdentifiedByField."
			);
		}
	}

	/**
	 * Method to get a pattern to delete all the infomration associated with
	 * the given class from the resource with the given URI. Note that only
	 * the statements associated with that class are deleted; if another class
	 * has data associated with the same URI, it will not be affected.
	 */
	public static String getDeletionPattern(String uri, Class clazz) {
		StringBuilder sb = new StringBuilder(uri + " ");
		boolean hasFields = false;

		for (Field f : clazz.getDeclaredFields()) {
			if (! f.isAnnotationPresent(PropertyField.class))
				continue;
			PropertyField pf = f.getAnnotation(PropertyField.class);

			if (! hasFields)
				hasFields = true; // first field
			else
				sb.append(" ;\n\t\t"); // not the first field

			sb.append(
					String.format(
						pf.abbreviated() ? " %s%s ?%s" : " <%s%s> ?%s",
						pf.propertyPrefix(),
						pf.rdfProperty(),
						f.getName()
						)
					);
		}

		if (! hasFields) return null;
		sb.append(" .");
		return sb.toString();
	}

	/**
	 * Method to get a pattern to use in a SPARQL query to identify
	 * the blank node representing an Object.
	 */
	private String getIdentifyingPattern(String varName, Object o)
	throws JRDFAnnotationException {
		Class clazz = o.getClass();		
		StringBuilder sb = new StringBuilder(varName);		

		if (!clazz.isAnnotationPresent(IdentifiedByField.class)) {
			boolean hasIdentifier = false;
			for (Field f : clazz.getDeclaredFields()) {
				if (! f.isAnnotationPresent(PropertyField.class))
					continue;
				PropertyField pf = f.getAnnotation(PropertyField.class);
				if (pf.policy() != Policy.IDENTIFIER)
					continue;
				
				if (! hasIdentifier) 
					hasIdentifier = true; // first identifying field
				else 
					sb.append(" ;\n\t\t"); // not the first identifying field

				String idValue;
				try {					
					Object val =  f.get(o);
					if (val == null)
						throw IllegalValueException.nullField(
							clazz, 
							f,
							pf
						);
					if (val instanceof java.util.Collection)
						throw JRDFAnnotationException.badlyAnnotatedProperty(
							clazz,
							f,
							"A @PropertyField with the IDENTIFIER policy cannot be a collection.");

					if (val instanceof java.lang.Enum)
						val = val.toString();

					Literal lit = null;

					if (val instanceof java.lang.String)
						lit = ResourceFactory.createPlainLiteral((String) val);
					else {
						for (Class literalClazz : literalTypes) {
							if (val.getClass() == literalClazz) {
								lit = ResourceFactory.createTypedLiteral(val);
								break;
							}
						}
					}

					if (lit != null) {
							String escaped = StringEscapeUtils
								.escapeJava(lit.getLexicalForm());
							if (!plainLiteralPattern.matcher(escaped).matches())
								throw IllegalValueException.literalMismatch(escaped);
					
							if (lit.getDatatype() != null)
								idValue =
									String.format(
										"\"%s\"^^<%s>",
										escaped, 
										lit.getDatatypeURI()
									);
								
							else 
								idValue = 
									String.format(
										"\"%s\"",
										escaped
									);
					} else if (val.getClass().isAnnotationPresent(IdentifiedByField.class)) {
						idValue = getURI(val);
					} else
						throw JRDFAnnotationException.badlyAnnotatedProperty(
							clazz,
							f,
							"A @PropertyField that cannot be persisted as a Literal needs to be of a @IdentifiedByField type.");
				} catch (IllegalArgumentException e) {
					throw new RuntimeException(
						String.format(
							"Encountered an IllegalArgumentException when trying to access the field %s on the class %s.",
							f.getName(),
							clazz.getCanonicalName()
						)
					);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(
						String.format(
							"Encountered an IllegalAccessException when trying to access the field %s on the class %s.",
							f.getName(),
							clazz.getCanonicalName()
						)
					);
				}

				if (idValue == null)
					throw IllegalValueException.nullField(
						clazz, 
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

			if (! hasIdentifier)
				throw JRDFAnnotationException.badlyAnnotatedClass(
					clazz,
					String.format("This class has no identifying fields!")
				);
		} else {
			throw JRDFAnnotationException.badlyAnnotatedClass(
				clazz,
				String.format("An instance of an @IdentifiedByField cannot be identified in this way!")
			);
		}


		return sb.toString();
	}

	/* ########## RETRIEVAL OPERATIONS ########## */

	/**
	 * Performs a retrieval operation against the dataset and attempts
	 * to create an instance of clazz that represents the 
	 * information in the graph associated with uri. Returns null if
	 * no such URI exists in the graph.
	 */
	public Object retrieve(String uri, Class clazz)
	throws JRDFAnnotationException {
		if (uri == null || clazz == null) return null;
		RetrievalBatch batch = new RetrievalBatch();
		batch.enqueue(uri, clazz);
		batch.run();
		return batch.getData(uri, clazz);
	}


	/**
	 * Performs a retrieval operation to fill in all of the non-
	 * identifier fields of o, using the identifier fields (or a
	 * URI, in the case of a @IdentifiedByField class) to find the appropriate
	 * node in the graph. Returns null if no such node exists in the
	 * graph.
	 */
	public Object retrieve(Object o) 
	throws JRDFAnnotationException {
		if (o == null) return null;

		Class clazz = o.getClass();
		String uri;

		if (clazz.isAnnotationPresent(IdentifiedByField.class))
			uri = getURI(o);
		else {
			String currentPrefixes = "";
			if (prefixes.containsKey(clazz))
				currentPrefixes = prefixes.get(clazz);

			String queryString = String.format(
				"%sSELECT ?origin\nWHERE {\n\t%s .\n}\nLIMIT 1",
				currentPrefixes,
				getIdentifyingPattern("?origin", o)
			);

			Iterator<QuerySolution> results = 
				QueryExecutionFactory.create(
					QueryFactory.create(queryString)
				).execSelect();

			if (results.hasNext()) {
				QuerySolution soln = results.next();
				uri = soln.getResource("origin").getURI();
			} else return null;
		}

		return retrieve(uri, clazz);
	}

	/**
	 * A private inner class representing the internal state and 
	 * results of a retrieval operation.
	 */
	private class RetrievalBatch {
		private List<String> uriQueue;
		private List<Class> clazzQueue;
		private Map<String, Map<Class, Object>> objMapping;

		public RetrievalBatch () {
			uriQueue = new LinkedList<String>();
			clazzQueue = new LinkedList<Class>();
			objMapping = new HashMap<String,Map<Class,Object>>();
		}

		public void enqueue(String uri, Class clazz) {
			if ( (!uriQueue.contains(uri))
				|| clazzQueue.get(uriQueue.indexOf(uri)) != clazz ) {
				uriQueue.add(uri);
				clazzQueue.add(clazz);
			}
		}

		public Object getData(String uri, Class clazz) {
			if (objMapping.containsKey(uri)) {
				Map<Class, Object> clazzMapping = objMapping.get(uri);
				if (clazzMapping.containsKey(clazz)) {
					return clazzMapping.get(clazz);
				}
			}
			return null;
		}

		public Set<Object> getAllData(Class clazz) {
			return new HashSet<Object>();
		}

		public void run()
		throws JRDFAnnotationException {
			String currentURI;
			Class currentClazz;
			int queuePos = 0;

			// if there is a single item  in the queue, we will attempt to 
			// retrieve the complete object graph. if any part of it cannot
			// be retrieved (because a non-nullable field cannot be populated),
			// the retrieval will end and objMapping will not be modified.
			if (uriQueue.size() <= 1 && clazzQueue.size() <= 1) {
				// create a deep clone of objMapping, so that a failed batch does not
				// affect objMapping.
				Map<String,Map<Class,Object>> results = new HashMap<String,Map<Class,Object>>(objMapping);
				for (Map.Entry<String,Map<Class,Object>> e : objMapping.entrySet())
					results.put(e.getKey(), new HashMap<Class, Object>(e.getValue()));

				while (queuePos < uriQueue.size() && queuePos < clazzQueue.size()) {
					// Move on to the next object.
					currentURI = uriQueue.get(queuePos);
					currentClazz = clazzQueue.get(queuePos);
					queuePos++;

					Object current = getOrCreate(currentURI, currentClazz, results);

					String currentPrefixes = "";
					if (prefixes.containsKey(currentClazz))
						currentPrefixes = prefixes.get(currentClazz);

					// for each @PropertyField on the object, fill it out
					for (Field f : currentClazz.getDeclaredFields()) {
						if (f.isAnnotationPresent(PropertyField.class)) {
							PropertyField pf = f.getAnnotation(PropertyField.class);

							Class valueClazz = pf.valueClazz();

							String queryString = String.format(
									pf.policy().isCollection ? 
										"%sSELECT ?val\nWHERE {\n\t%s %s ?val .\n}" :
										"%sSELECT ?val\nWHERE {\n\t%s %s ?val .\n}\nLIMIT 1",
									currentPrefixes,
									currentURI,
									String.format(
										pf.abbreviated() ? "%s%s" : "<%s%s>",
										pf.propertyPrefix(),
										pf.rdfProperty()
									)
								);

							Iterator<QuerySolution> querySolns = 
								QueryExecutionFactory.create(
									QueryFactory.create(queryString)
								).execSelect();

							Object val;

							if (pf.policy().isCollection) {
								Collection<Object> allVals;

								if (valueClazz.isEnum()) 	// use an EnumSet
									allVals = EnumSet.noneOf(valueClazz);
								else 											// use a HashSet
									allVals = new HashSet<Object>();

								while (querySolns.hasNext()) {
									QuerySolution soln = querySolns.next();
									RDFNode valNode = soln.get("val");
									if (valNode.isLiteral()) {
										Literal lit = soln.getLiteral("val");
										allVals.add(lit2Object(lit, valueClazz));
									} else {
										String valURI =
											String.format(
												"<%s>", 
												valNode.asResource().getURI()
											);

										enqueue(valURI, valueClazz);
										allVals.add(getOrCreate(valURI, valueClazz, results));
									}
								}

								// If we fail to populate the non-nullable field, abort the retrieval.
								if ((!pf.policy().nullable) && allVals.size() == 0)
									return;

								val = allVals;
							} else {
								if (querySolns.hasNext()) {
									QuerySolution soln = querySolns.next();
									RDFNode valNode = soln.get("val");

									if (valNode.isLiteral()) {
										val = lit2Object(valNode.asLiteral(), valueClazz);
									} else {
										String valURI = 
											String.format(
												"<%s>", 
												valNode.asResource().getURI()
											);

									enqueue(valURI, valueClazz);
									val = getOrCreate(valURI, valueClazz, results);
									}
								} else {
									// If we fail to populate the non-nullable field, abort the retrieval.
									if (!pf.policy().nullable)
										return;

									val = null;
								}
							}

							// We're not playing around here.
							f.setAccessible(true);
							
							try {
								f.set(current, val);
							} catch (IllegalArgumentException e) {
								throw new RuntimeException(
									String.format(
										"Encountered an IllegalArgumentException when trying to set the field %s on the class %s.",
										f.getName(),
										currentClazz.getCanonicalName()
									)
								);
							} catch (IllegalAccessException e) {
								throw new RuntimeException(
									String.format(
										"Encountered an IllegalAccessException when trying to set the field %s on the class %s.",
										f.getName(),
										currentClazz.getCanonicalName()
									)
								);
							}
						}
					}
				}
				// if a return has not been triggered at by this point,
				// retrieval was successful.
				objMapping.putAll(results);
			} else if (uriQueue.size() == clazzQueue.size()) {
				// run each item in the queue as a separate batch, so that 
				// a failure to retrieve one does not affect the others 
				// unless they are part of the same graph.
				for (int x = 0; x < uriQueue.size(); x++) {
					RetrievalBatch subBatch = new RetrievalBatch();
					
					subBatch.enqueue(
						uriQueue.get(x),
						clazzQueue.get(x)
					);

					subBatch.objMapping.putAll(objMapping);
					subBatch.run();
					objMapping.putAll(subBatch.objMapping);
				}
			}
		}

		private Object getOrCreate(
			String uri, 
			Class clazz, 
			Map<String, Map<Class, Object>> mapping) {
			if (mapping.containsKey(uri)) {
				Map<Class, Object> clazzMapping = mapping.get(uri);
				if (clazzMapping.containsKey(clazz))
					return clazzMapping.get(clazz);
				else {
					try {
						Object o = clazz.getDeclaredConstructor().newInstance();
						clazzMapping.put(clazz, o);
						mapping.put(uri, clazzMapping);
						return o;
					} catch (Exception e) {
						throw new RuntimeException(
							String.format(
								"Class %s has no public no-argument constructor!",
								clazz.getCanonicalName()
							)
						);
					}
				}
			} else {
				Map<Class, Object> clazzMapping = new HashMap<Class, Object>();
				try {
					Object o = clazz.getDeclaredConstructor().newInstance();
					clazzMapping.put(clazz, o);
					mapping.put(uri, clazzMapping);
					return o;
				} catch (Exception e) {
					throw new RuntimeException(
						String.format(
							"Class %s has no public no-argument constructor!",
							clazz.getCanonicalName()
						)
					);
				}
			}
		}
	}

	/**
	 * Utility method for converting Literal nodes into Java objects.
	 */
	private static Object lit2Object(Literal lit, Class<?> clazz) {

		// Restore enumerated type
		if (clazz.isEnum()) {

			/*
			 * WARNING: THE FOLLOWING CODE IS BLACK MAGIC. WE USE REFLECTION TO
			 * COERCE Enum.valueOf() INTO IGNORING THE CLASS GENERICS THAT ARE
			 * INFERRED AT RUNTIME. DO NOT CHANGE THIS CODE
			 */

			try {
				return Enum.class.getDeclaredMethod("valueOf",
						new Class[] { Class.class, String.class }).invoke(null,
						clazz.asSubclass(Enum.class), lit.getValue());
			} catch (Exception e) {
				throw new RuntimeException(
						"Something genuinely horrifying happened; we can't invoke Enum.valueOf().");
			}
		} else {
			return lit.getValue();
		}
	}

	/* ########## REMOVAL OPERATIONS ########## */

	/**
	 * Removes all the data FOR THE GIVEN CLASS that is associated
	 * with the given URI. References to that URI are not deleted,
	 * and neither are statements about that node that are not
	 * associated with the given class.
	 *
	 * To remove all statements about a URI, 
	 * @see removeResource
	 */
	public void removeData(String uri, Class clazz) 
	throws JRDFAnnotationException {
		String currentPrefixes = null;
		if (prefixes.containsKey(clazz))
			currentPrefixes = prefixes.get(clazz);

		String queryString = String.format(
			"%sDELETE WHERE {\n\t%s\n}",
			currentPrefixes,
			getDeletionPattern(uri, clazz)
		);

		UpdateAction.parseExecute(queryString, ds);
	}

	/**
	 * Finds the node in the graph associated with the given object
	 * (if it exists) and removes the data associated with the class
	 * of that object. References to that URI are not deleted,
	 * and neither are statements about that node that are not
	 * associated with the given class.
	 */
	public void removeData(Object o) 
	throws JRDFAnnotationException {
		if (o == null) return;

		Class clazz = o.getClass();
		String uri;

		if (clazz.isAnnotationPresent(IdentifiedByField.class))
			uri = getURI(o);
		else {
			String currentPrefixes = "";
			if (prefixes.containsKey(clazz))
				currentPrefixes = prefixes.get(clazz);

			String queryString = String.format(
				"%sSELECT ?origin\nWHERE {\n\t%s .\n}\nLIMIT 1",
				currentPrefixes,
				getIdentifyingPattern("?origin", o)
			);

			Iterator<QuerySolution> results = 
				QueryExecutionFactory.create(
					QueryFactory.create(queryString)
				).execSelect();

			if (results.hasNext()) {
				QuerySolution soln = results.next();
				uri = soln.getResource("origin").getURI();
			} else 
				return; // nothing to delete!
		}

		removeData(uri, clazz);
	}

	/**
	 * This is the method to completely remove all the statements (no
	 * matter what class it is associated with) that involve a given
	 * Resource from the dataset.
	 */
	public void removeResource(String uri)
	throws JRDFAnnotationException {
		String queryString = String.format(
				"DELETE WHERE {\n\t%s ?property1 ?val .\n} ;\nDELETE WHERE {\n ?node ?property2 %s .\n}",
				uri,
				uri
			);

		UpdateAction.parseExecute(queryString, ds);
	}

	/* ########## RECORDING OPERATIONS ########## */

	/**
	 * Persists a representation of the given object in the graph.
	 */
	public void record(Object o) 
	throws JRDFAnnotationException {
		RecordingBatch batch = new RecordingBatch();
		batch.enqueue(o);
		batch.run();
	}

	/**
	 * Persists representations of all the given objects in the graph.
	 */
	public void record(Object[] os) 
	throws JRDFAnnotationException {
		RecordingBatch batch = new RecordingBatch();
		for (Object o : os)
			batch.enqueue(o);
		batch.run();
	}

	/**
	 * Persists representations of all the given objects in the graph.
	 */
	public void record(Collection<Object> os) 
	throws JRDFAnnotationException {
		RecordingBatch batch = new RecordingBatch();
		for (Object o : os)
			batch.enqueue(o);
		batch.run();
	}

	private class RecordingBatch {
		private List<Object> objQueue;
		private Map<Object, String> nodeMapping;

		public RecordingBatch() {
			objQueue = new LinkedList<Object>();
			nodeMapping = new HashMap<Object, String>();
		}

		public void enqueue(Object o) {
			if (!objQueue.contains(o))
				objQueue.add(o);
		}

		public void run() throws JRDFAnnotationException {
			int currentObj = 0;
			while (currentObj < objQueue.size()) {
				Object current = objQueue.get(currentObj++);
				String currentUri = uriOf(current);
				Class currentClazz = current.getClass();

				String currentPrefixes = "";
				if (prefixes.containsKey(currentClazz))
					currentPrefixes = prefixes.get(currentClazz);

				String deletion = getDeletionPattern(currentUri, currentClazz);
				String insertion = getInsertionPattern(currentUri, current);

				if (deletion == null) // nothing to be done here
					continue;

				String queryString;
				if (insertion != null)
					queryString = String.format(
							"%sDELETE {\n\t%s\n}\nINSERT {\n\t%s\n}\nWHERE {\n\t%s\n}",
							currentPrefixes,
							deletion,
							insertion,
							deletion
						);
				else
					queryString = String.format(
							"%sDELETE WHERE {\n\t%s\n}",
							currentPrefixes,
							deletion
						);

				UpdateAction.parseExecute(queryString, ds);
			}
		}

		/**
		 * Utility method to find (if it already exists) the URI for the 
		 * given Object, and to create one using the default stem and the
		 * current time, if not.
		 */
		private String uriOf(Object o) throws JRDFAnnotationException {
			if (! nodeMapping.containsKey(o)){
				Class clazz = o.getClass();
				String uri;
		
				if (clazz.isAnnotationPresent(IdentifiedByField.class))
					uri = getURI(o);
				else {
					String currentPrefixes = "";
					if (prefixes.containsKey(clazz))
						currentPrefixes = prefixes.get(clazz);
		
					String queryString = String.format(
						"%sSELECT DISTINCT ?origin\nWHERE {\n\t%s .\n}\nLIMIT 1",
						currentPrefixes,
						getIdentifyingPattern("?origin", o)
					);
		
					Iterator<QuerySolution> results = 
						QueryExecutionFactory.create(
							QueryFactory.create(queryString)
						).execSelect();
		
					if (results.hasNext()) {
						QuerySolution soln = results.next();
						uri = soln.getResource("origin").getURI();
					} else {
						uri = String.format("<%s.%016x>",
							anonStem,
							System.currentTimeMillis()
							);
					}
				}
				nodeMapping.put(o, uri);
			}

			return nodeMapping.get(o);
		}

		/**
		 * Method to get the SPARQL INSERT pattern to presist a given
		 * object's various properties.
		 */
		private String getInsertionPattern(String uri, Object o) 
		throws JRDFAnnotationException {
			Class clazz = o.getClass();		
			StringBuilder sb = new StringBuilder(uri);			
			boolean noFields = true;

			for (Field f : clazz.getDeclaredFields()) {
				if (! f.isAnnotationPresent(PropertyField.class))
					continue;
				PropertyField pf = f.getAnnotation(PropertyField.class);

				String predicate = getPredicate(o, f, pf);
				if (predicate == null) {
					if (!pf.policy().nullable )
						throw IllegalValueException.nullField(
								clazz, 
								f,
								pf
							);
					continue;
				}

				if (noFields) 
					noFields = false;
				else 
					sb.append(" ;\n\t\t"); // not the first identifying field					

				sb.append(
					String.format(
						pf.abbreviated() ? " %s%s %s" : " <%s%s> %s",
						pf.propertyPrefix(),
						pf.rdfProperty(),
						predicate
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
		 * Creates the SQARQL query matching a field f on object o.
		 */
		private String getPredicate(Object o, Field f, PropertyField pf) 
			throws JRDFAnnotationException {
			Class clazz = o.getClass();
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
						if (val == null) continue;

						sb.append(valToSparql(val));
						if (iter.hasNext()) 
							sb.append(" , "); // more objects to go
					}

					return sb.toString();
				} catch (ClassCastException e) {
					throw JRDFAnnotationException.badlyAnnotatedProperty(
						clazz,
						f,
						"Marked as a collection field, but value could not be cast to java.lang.Collection."
					);
				}
					catch (IllegalArgumentException e) {
					throw new RuntimeException(
						String.format(
							"Encountered an IllegalArgumentException when trying to access the field %s on the class %s.",
							f.getName(),
							clazz.getCanonicalName()
						)
					);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(
						String.format(
							"Encountered an IllegalAccessException when trying to access the field %s on the class %s.",
							f.getName(),
							clazz.getCanonicalName()
						)
					);
				}
			} else {
				try {
					Object val =  f.get(o);
					if (val == null) return null;
					if (val instanceof java.util.Collection)
						throw JRDFAnnotationException.badlyAnnotatedProperty(
							clazz,
							f,
							"Cardinality did not indicate collection, but value was a collection.");
					return valToSparql(val);
				} catch (IllegalArgumentException e) {
					throw new RuntimeException(
						String.format(
							"Encountered an IllegalArgumentException when trying to access the field %s on the class %s.",
							f.getName(),
							clazz.getCanonicalName()
						)
					);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(
						String.format(
							"Encountered an IllegalAccessException when trying to access the field %s on the class %s.",
							f.getName(),
							clazz.getCanonicalName()
						)
					);
				}
			}
		}

		/**
		 * Gets the String that will be used for the predicate representing a link
		 * to the given Object. If the object won't be Literal and isn't in the queue, 
		 * it will be added to it.
		 */
		private String valToSparql(Object o) throws JRDFAnnotationException {
			if (o instanceof java.lang.Enum)
				o = o.toString();

			Literal lit = null;

			if (o instanceof java.lang.String)
				lit = ResourceFactory.createPlainLiteral((String) o);
			else {
				for (Class literalType : literalTypes){
					if (o.getClass() == literalType) {
						lit = ResourceFactory.createTypedLiteral(o);
						break;
					}
				}
			}

			if (lit != null) {
					String escaped = StringEscapeUtils
						.escapeJava(lit.getLexicalForm());
					if (!plainLiteralPattern.matcher(escaped).matches())
						throw IllegalValueException.literalMismatch(escaped);
			
					if (lit.getDatatype() != null)
						return String.format("\"%s\"^^<%s>",
							escaped, lit.getDatatypeURI());
					return String.format("\"%s\"",
							escaped);
			} else {
				enqueue(o);
				return uriOf(o);
			}

		}
	}
}