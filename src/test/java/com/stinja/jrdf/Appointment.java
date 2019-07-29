package com.stinja.jrdf;

@ResourcePrefix(abbreviated = "schema:", full = "http://www.stinja.com/jrdf/schema#")
public class Appointment {

	public static Appointment fromIds(
		ContactInfo appointee,
		Organization organization) {
		Appointment result = new Appointment();
		result.appointee = appointee;
		result.organization = organization;
		return result;
	}

	public static void fillData(
		Appointment appointment,
		String officialTitle) {
		appointment.officialTitle = officialTitle;
	}

	@PropertyField(
		rdfProperty = "appointee", 
		valueClazz = ContactInfo.class, 
		policy = Policy.IDENTIFIER)
	private ContactInfo appointee;

	@PropertyField(
		rdfProperty = "organization", 
		valueClazz = String.class, 
		policy = Policy.IDENTIFIER)
	private Organization organization;

	@PropertyField(
		rdfProperty = "officialTitle",
		valueClazz = ContactInfo.class,
		policy = Policy.EXACTLY_ONE)
	private String officialTitle;
}