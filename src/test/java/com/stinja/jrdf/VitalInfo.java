package com.stinja.jrdf;

@IdentifiedByField(idField = "personId", uriLabel = "Person")
@ResourcePrefix(abbreviated = "schema:", full = "http://www.stinja.com/jrdf/schema#")
public class VitalInfo {

	public static VitalInfo fromId(int personId) {
		VitalInfo result = new VitalInfo();
		result.personId = personId;
		return result;
	}

	public static void fillData(
		VitalInfo vi,
		HairColor hairColor,
		double heightInches,
		int age) {
		vi.hairColor = hairColor;
		vi.heightInches = heightInches;
		vi.age = age;
	}

	@PropertyField(
		rdfProperty = "personId", 
		valueClazz = Integer.class, 
		policy = Policy.IDENTIFIER)
	private int personId;

	@PropertyField(
		rdfProperty = "hairColor",
		valueClazz = HairColor.class
		)
	private HairColor hairColor;

	@PropertyField(
		rdfProperty = "heightInches",
		valueClazz = Double.class)
	private double heightInches;

	@PropertyField(
		rdfProperty = "age", 
		propertyPrefix = "http://xmlns.com/foaf/0.1/",
		abbreviated = false,
		valueClazz = Integer.class, 
		policy = Policy.EXACTLY_ONE)
	private int age;
}