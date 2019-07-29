package com.stinja.jrdf;

public class OrganizationService {
	private PersistenceManager manager;

	public OrganizationService(PersistenceManager manager) {
		this.manager = manager;
		manager.manageClass(Organization.class);
	}

	private String getOrgURI(int orgId) 
		throws JRDFAnnotationException {
		return manager.getURI(Organization.fromId(orgId));
	}

	/**
	 * Retrieves any organization information associated with that ID number.
	 */
	public Organization fineOragnization(int orgId) 
		throws JRDFAnnotationException {
		return (Organization) manager.retrieve(getOrgURI(orgId), ContactInfo.class);
	}

	/**
	 * Persists contact information to the database.
	 */
	public void persist(Organization o)
		throws JRDFAnnotationException {
		manager.record(o);
	}

	/**
	 * Removes any organizational information associated with 
	 * the ID of the given object from the database
	 */
	public void delete(Organization o)
		throws JRDFAnnotationException {
		manager.removeData(o);
	}

	/**
	 * Removes any organizational information associated with 
	 * the ID of the given object from the database.
	 */
	public void deleteById(int orgId)
		throws JRDFAnnotationException {
		manager.removeData(getOrgURI(orgId), Organization.class);
	}
}