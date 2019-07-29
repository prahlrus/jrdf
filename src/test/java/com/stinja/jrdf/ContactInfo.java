package com.stinja.jrdf;

import java.util.Collection;
import java.util.ArrayList;

@IdentifiedByField(idField = "personId", uriLabel = "Person")
@ResourcePrefix(abbreviated = "schema:", full = "http://www.stinja.com/jrdf/schema#")
@ResourcePrefix(abbreviated = "foaf:", full = "http://xmlns.com/foaf/0.1/")
public class ContactInfo {
	
	public static ContactInfo fromId(int personId) {
		ContactInfo result = new ContactInfo();
		result.personId = personId;
		return result;
	}

	public static void fillData(
		ContactInfo ci,
		Collection<String> givenNames,
		String familyName,
		Collection<Appointment> appointments) {
		ci.givenNames = 
			(givenNames == null) ? 
				new ArrayList<String>() : 
				givenNames;
		ci.familyName = familyName;
		ci.appointments = 
			(appointments == null) ? 
				new ArrayList<Appointment>() :
				appointments;
	}

	@PropertyField(
		rdfProperty = "personId", 
		valueClazz = Integer.class, 
		policy = Policy.IDENTIFIER)
	private int personId;

	@PropertyField(
		rdfProperty = "givenName",
		propertyPrefix = "foaf:",
		valueClazz = String.class,
		policy = Policy.SOME)
	private Collection<String> givenNames;

	@PropertyField(
		rdfProperty = "familyName",
		propertyPrefix = "foaf:",
		valueClazz = String.class)
	private String familyName;

	@PropertyField(
		rdfProperty = "holdsAppointment",
		valueClazz = Appointment.class,
		policy = Policy.MANY)
	private Collection<Appointment> appointments;
}