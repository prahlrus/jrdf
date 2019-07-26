package com.stinja.jrdf;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;

import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;

import java.util.List;
import java.util.LinkedList;

import java.util.Iterator;

import org.apache.jena.rdf.model.Literal;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.Dataset;

import org.apache.jena.update.UpdateAction;

public class PersistenceManager {
	private Dataset ds;
	private Map<Class, String> prefixes;

	public PersistenceManager(Dataset ds) {
		this.ds = ds;
		this.prefixes = new HashMap<Class, String>();
	}

	private static String ORIGIN_VARNAME = "?origin";

	/* ############# INITIALIZATION ############# */

	/**
	 * Pre-process a class to find any and all @UriPrefix
	 * annotations on it or on its package. All SPARQL queries
	 * regarding that particular class will use those prefixes.
	 */
	public void manageClass(Class clazz) {
		if (! prefixes.containsKey(clazz)) {
			Map <String, String> draftPrefixes = 
				new HashMap<String, String>();
			Package currentPrefixes = clazz.getPackage();
			if (currentPrefixes != null && currentPrefixes.isAnnotationPresent(UriPrefix.class))
				for (UriPrefix up : (UriPrefix[]) currentPrefixes.getAnnotationsByType(UriPrefix.class))
					draftPrefixes.put(up.prefix(), up.full());
			if (clazz.isAnnotationPresent(UriPrefix.class))
				for (UriPrefix up : (UriPrefix[]) clazz.getAnnotationsByType(UriPrefix.class))
					draftPrefixes.put(up.prefix(), up.full());
			StringBuilder sb = new StringBuilder();
			for (Map.Entry e : draftPrefixes.entrySet())
				sb.append(String.format(
						"PREFIX %s <%s>%n",
						e.getKey(),
						e.getValue()
					)
				);
			prefixes.put(clazz, sb.toString());
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

	/* ########## RETRIEVAL OPERATIONS ########## */

	/**
	 * Performs a retrieval operation against the dataset and attempts
	 * to create an instance of resourceClazz that represents the 
	 * information in the graph associated with uri. Returns null if
	 * no such URI exists in the graph.
	 */
	public Object retrieveResource(String uri, Class resourceClazz)
	throws JRDFAnnotationException {
		List<String> keyQueue = new LinkedList<String>();
		List<Class> clazzQueue = new LinkedList<Class>();

		keyQueue.add(uri);
		clazzQueue.add(resourceClazz);

		Map<String,Object> objectMapping = 
			retrieveBatch(keyQueue, clazzQueue);
		if (objectMapping.containsKey(uri))
			return objectMapping.get(uri);
		return null;
	}


	/**
	 * Performs a retrieval operation to fill in all of the non-
	 * identifier fields of o, using the identifier fields (or a
	 * URI, in the case of a @Resource class) to find the appropriate
	 * node in the graph. Returns null if no such node exists in the
	 * graph.
	 */
	public Object retrieve(Object o) 
	throws JRDFAnnotationException {
		if (o == null) return null;
		List<String> keyQueue = new LinkedList<String>();
		List<Class> clazzQueue = new LinkedList<Class>();

		Class resourceClazz = o.getClass();
		String key;
		if (resourceClazz.isAnnotationPresent(Resource.class)) {
			key = JRDFUtils.getUri(o);
			keyQueue.add(key);

		}
		else
			key = JRDFUtils.getIdentifyingPattern(ORIGIN_VARNAME, o);
			keyQueue.add(key);
		clazzQueue.add(resourceClazz);

		Map<String,Object> objectMapping = 
			retrieveBatch(keyQueue, clazzQueue);
		if (objectMapping.containsKey(key))
			return objectMapping.get(key);
		return null;
	}


	/**
	 * The heavy-lifting method behind retrievals.
	 */
	private Map<String,Object> retrieveBatch(
		List<String> keyQueue, 
		List<Class> clazzQueue)
	throws JRDFAnnotationException {
		Map<String,Object> objectMapping = new HashMap<String,Object>();

		String currentKey;
		Class currentClazz;

		while (keyQueue.size() > 0 && clazzQueue.size() > 0) {
			// Move on to the next object.
			currentKey = keyQueue.remove(0);
			currentClazz = clazzQueue.remove(0);

			if (!objectMapping.containsKey(currentKey))
				objectMapping.put(currentKey, create(currentClazz));
			Object current = objectMapping.get(currentKey);

			String currentPrefixes = "";
			if (prefixes.containsKey(currentClazz))
				currentPrefixes = prefixes.get(currentClazz);

			String origin = 
				currentClazz.isAnnotationPresent(Resource.class) ?
				currentKey : 			// for a @Resource
				String.format(
					"%s ;\n\t\t",		// otherwise, use the identifying pattern
					currentKey
				);

			// for each @PropertyField on the object, fill it out
			for (Field f : currentClazz.getDeclaredFields()) {
				if (f.isAnnotationPresent(PropertyField.class)) {
					PropertyField pf = f.getAnnotation(PropertyField.class);

					String queryString = String.format(
							pf.policy().isCollection ? 
								"%sSELECT DISTINCT ?val\nWHERE {\n\t%s %s ?val .\n}" :
								"%sSELECT DISTINCT ?val\nWHERE {\n\t%s %s ?val .\n}\nLIMIT 1",
							currentPrefixes,
							origin,
							String.format(
								pf.abbreviated() ? "%s%s" : "<%s%s>",
								pf.propertyPrefix(),
								pf.rdfProperty()
							)
						);

					Iterator<QuerySolution> results = 
						QueryExecutionFactory.create(
							QueryFactory.create(queryString)
						).execSelect();


					Object val;
					Class valueClazz = pf.valueClazz();

					if (pf.policy().isCollection) {
						Collection<Object> allVals;

						if (valueClazz.isEnum()) 	// use an EnumSet
							allVals = EnumSet.noneOf(valueClazz);
						else 										// use a HashSet
							allVals = new HashSet<Object>();

						while (results.hasNext()) {
							QuerySolution soln = results.next();

							if (valueClazz.isAnnotationPresent(Resource.class)) {
								String valKey = 
									String.format(
										"<%s>", 
										soln.getResource("val").getURI()
									);

								if (!objectMapping.containsKey(valKey)) { 
									// enqueue a newly-created object
									objectMapping.put(valKey, create(valueClazz));
									keyQueue.add(valKey);
									clazzQueue.add(valueClazz);
								}

								allVals.add(objectMapping.get(valKey));
							} else {
								Literal lit = soln.getLiteral("val");
								allVals.add(lit2Object(lit, valueClazz));
							}
						}

						val = allVals;
					} else {
						if (results.hasNext()) {
							QuerySolution soln = results.next();

							if (valueClazz.isAnnotationPresent(Resource.class)) {
								String valKey = 
									String.format(
										"<%s>", 
										soln.getResource("val").getURI()
									);

								if (objectMapping.containsKey(valKey)) {
									val = objectMapping.get(valKey);
								} else { // enqueue a newly-created object
									val = create(valueClazz);
									objectMapping.put(valKey, val);
									keyQueue.add(valKey);
									clazzQueue.add(valueClazz);
								}
							} else {
								Literal lit = soln.getLiteral("val");
								val = lit2Object(lit, valueClazz);
							}
						} else {
							if (!pf.policy().nullable)
								throw IllegalValueException.nullField(currentClazz, f, pf);
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

		return objectMapping;
	}

	private static Object create(Class resourceClazz) {
		try {
			return resourceClazz.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new RuntimeException(
				String.format(
					"Class %s has no public no-argument constructor!",
					resourceClazz.getCanonicalName()
				)
			);
		}
	}

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

	/* ########## PERSISTENCE OPERATIONS ########## */

	/**
	 * Removes all triples concerning the given URI from the graph.
	 * The resourceClazz parameter is used to determine whether the
	 * deletion needs to cascade to any referenced nodes or not.
	 * Returns true if any triples were actually removed.
	 */
	public boolean removeResource(String uri, Class resourceClazz) 
	throws JRDFAnnotationException {
		String currentPrefixes = null;
		if (prefixes.containsKey(resourceClazz))
			currentPrefixes = prefixes.get(resourceClazz);

		// TODO

		return false;
	}

	/**
	 * Removes all triples concerning the node that represents
	 * the given object from the graph. Returns true if the given
	 * Object corresponded to a node in the graph (and therefore
	 * triples were removed).
	 */
	public boolean remove(Object o) 
	throws JRDFAnnotationException {
		if (o == null) return false;
		List<String> keyQueue = new LinkedList<String>();
		List<Class> clazzQueue = new LinkedList<Class>();

		Class resourceClazz = o.getClass();
		if (resourceClazz.isAnnotationPresent(Resource.class))
			keyQueue.add(JRDFUtils.getUri(o));
		else
			keyQueue.add(JRDFUtils.getIdentifyingPattern(ORIGIN_VARNAME, o));
		clazzQueue.add(resourceClazz);

		Set<String> removedKeys = 
			removeBatch(keyQueue, clazzQueue);
		if (! removedKeys.isEmpty())
			return true;
		return false;
	}

	/**
	 * Heavy-lifting method to remove many nodes from the graph.
	 */
	private Set<String> removeBatch(
		List<String> keyQueue, 
		List<Class> clazzQueue)
	throws JRDFAnnotationException {
		Set<String> removedKeys = new HashSet<String>();

		String currentKey;
		Class currentClazz;

		while (keyQueue.size() > 0 && clazzQueue.size() > 0) {
			// Move on to the next object.
			currentKey = keyQueue.remove(0);
			currentClazz = clazzQueue.remove(0);

			if (removedKeys.contains(currentKey))
				continue;

			String currentPrefixes = "";
			if (prefixes.containsKey(currentClazz))
				currentPrefixes = prefixes.get(currentClazz);

			String lazyDeletion;
			if (currentClazz.isAnnotationPresent(Resource.class))
				lazyDeletion = String.format(
					"%sDELETE WHERE {\n\t%s ?property1 ?val .\n} ;\nDELETE WHERE {\n ?node ?property2 %s .\n}",
					currentPrefixes,
					currentKey,
					currentKey
				);
			else
				lazyDeletion = String.format(
					"%sDELETE WHERE {\n\t%s ;\n\t\t?property1 ?val .\n} ;\nDELETE WHERE {\n %s .\n\t?node ?property2 %s .\n}",
					currentPrefixes,
					currentKey,
					currentKey,
					ORIGIN_VARNAME
				);

			UpdateAction.parseExecute(lazyDeletion, ds);
			removedKeys.add(currentKey);

			String origin = 
				currentClazz.isAnnotationPresent(Resource.class) ?
				currentKey : 			// for a @Resource
				String.format(
					"%s ;\n\t\t",		// otherwise, use the identifying pattern
					currentKey
				);

			// for each @PropertyField on the object, fill it out
			for (Field f : currentClazz.getDeclaredFields()) {
				if (f.isAnnotationPresent(PropertyField.class)) {
					PropertyField pf = f.getAnnotation(PropertyField.class);

					String queryString = String.format(
							"%sSELECT DISTINCT ?val\nWHERE {\n\t%s %s ?val .\n}",
							currentPrefixes,
							origin,
							String.format(
								pf.abbreviated() ? "%s%s" : "<%s%s>",
								pf.propertyPrefix(),
								pf.rdfProperty()
							)
						);

					Iterator<QuerySolution> results = 
						QueryExecutionFactory.create(
							QueryFactory.create(queryString)
						).execSelect();

					Class valueClazz = pf.valueClazz();

					while (results.hasNext()) {
						String valKey = String.format(
							"<%s>",
							results.next().getResource("val").getURI()
						);
						if (! removedKeys.contains(valKey)) {
							keyQueue.add(valKey);
							clazzQueue.add(valueClazz);
						}
					}
				}
			}
		}

		return removedKeys;
	}

	/**
	 * Persists a representation of the given object in the graph.
	 */
	public void record(Object o) 
	throws JRDFAnnotationException {
		Class resourceClazz = o.getClass();
		String currentPrefixes = null;
		if (prefixes.containsKey(resourceClazz))
			currentPrefixes = prefixes.get(resourceClazz);

		// TODO
	}
}