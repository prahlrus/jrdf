package com.stinja.jrdf;

public class AppointmentService {
	private PersistenceManager manager;

	public AppointmentService(PersistenceManager manager) {
		this.manager = manager;
		manager.manageClass(Appointment.class);
	}

	/**
	 * Finds any appointment information associated with the 
	 * person and organization that have the given ID numbers.
	 */
	public Appointment findAppointment(int personId, int orgId) 
		throws JRDFAnnotationException {
		return (Appointment) manager.retrieve(
			Appointment.fromIds(
				ContactInfo.fromId(personId), 
				Organization.fromId(orgId)));
	}

	/**
	 * Stores the appointment in the database.
	 */
	public void persist(Appointment a) 
		throws JRDFAnnotationException {
		manager.record(a);
	}

	/**
	 * Removes the appointment from the database.
	 */
	public void delete(Appointment a)
		throws JRDFAnnotationException {
		manager.removeData(a);
	}

	/**
	 * Deletes any appointment information associated with the 
	 * person and organization that have the given ID numbers.
	 */
	public void deleteAppointment(int personId, int orgId)
		throws JRDFAnnotationException {
		manager.removeData(
			Appointment.fromIds(
				ContactInfo.fromId(personId), 
				Organization.fromId(orgId)));
	}

}