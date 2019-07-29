package com.stinja.jrdf;

import java.util.Collection;
import java.util.ArrayList;

@IdentifiedByField(idField = "orgId")
@ResourcePrefix(abbreviated = "schema:", full = "http://www.stinja.com/jrdf/schema#")
@ResourcePrefix(abbreviated = "foaf:", full = "http://xmlns.com/foaf/0.1/")
public class Organization {

	public static Organization fromId(int orgId) {
		Organization result = new Organization();
		result.orgId = orgId;
		return result;
	}

	public static void fillData(
		Organization o,
		String officialName,
		Collection<ContactInfo> members) {
		o.officialName = officialName;
		o.members = 
			(members == null) ?
				new ArrayList<ContactInfo>() :
				members; 
	}

	@PropertyField(
		rdfProperty = "orgId", 
		valueClazz = Integer.class, 
		policy = Policy.IDENTIFIER)
	private int orgId;

	@PropertyField(
		rdfProperty = "name", 
		propertyPrefix = "foaf:",
		valueClazz = String.class, 
		policy = Policy.EXACTLY_ONE)
	private String officialName;

	@PropertyField(
		rdfProperty = "member",
		propertyPrefix = "foaf:",
		valueClazz = ContactInfo.class,
		policy = Policy.MANY)
	private Collection<ContactInfo> members;
}