package com.stinja.jrdf;

public class PersonService {
	private PersistenceManager manager;

	public PersonService(PersistenceManager manager) {
		this.manager = manager;
		manager.manageClass(VitalInfo.class);
		manager.manageClass(ContactInfo.class);
	}

	private String getPersonURI(int personId) 
		throws JRDFAnnotationException {
		ContactInfo dummy = ContactInfo.fromId(personId);
		return manager.getURI(dummy);
	}

	/**
	 * Retrieves any contact information associated with that ID number.
	 */
	public ContactInfo findContactInfo(int personId) 
		throws JRDFAnnotationException {
		return (ContactInfo) manager.retrieve(getPersonURI(personId), ContactInfo.class);
	}

	/**
	 * Retrieves any vital information associated with that ID number.
	 */
	public VitalInfo findVitalInfo(int personId) 
		throws JRDFAnnotationException {
		return (VitalInfo) manager.retrieve(getPersonURI(personId), VitalInfo.class);
	}

	/**
	 * Persists contact information to the database.
	 */
	public void persist(ContactInfo ci)
		throws JRDFAnnotationException {
		manager.record(ci);
	}

	/**
	 * Persists vital information to the database.
	 */
	public void persist(VitalInfo vi)
		throws JRDFAnnotationException {
		manager.record(vi);
	}

	/**
	 * Removes any contact information associated with the ID
	 * of the given object from the database, leaving vital
	 * information unaffacted.
	 */
	public void delete(ContactInfo ci)
		throws JRDFAnnotationException {
		manager.removeData(ci);
	}

	/**
	 * Removes any vital information associated with the ID
	 * of the given object from the database, leaving contact
	 * information unaffacted.
	 */
	public void delete(VitalInfo vi)
		throws JRDFAnnotationException {
		manager.removeData(vi);
	}

	/**
	 * Removes any contact information associated with that ID
	 * from the database, leaving vital information unaffacted.
	 */
	public void deleteContactById(int personId)
		throws JRDFAnnotationException {
		manager.removeData(getPersonURI(personId), ContactInfo.class);
	}

	/**
	 * Removes any vital information associated with that ID
	 * from the database, leaving contact information unaffacted.
	 */
	public void deleteVitalById(int personId)
		throws JRDFAnnotationException {
		manager.removeData(getPersonURI(personId), VitalInfo.class);
	}

	/**
	 * Removes all information regarding a person with the
	 * given ID number from the database..
	 */
	public void deletePersonById(int personId)
		throws JRDFAnnotationException {
		manager.removeData(getPersonURI(personId), VitalInfo.class);
		manager.removeData(getPersonURI(personId), ContactInfo.class);
	}
}