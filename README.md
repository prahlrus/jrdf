# jrdf
A framework that uses Apache Jena to persist and retrieve Java runtime objects. To be used by the framework, the properties of classes must be annotated, `@PropertyField` which provides information about the property to the framework. 

In order to generate URIs representing objects of the class, the class must be annotated with `@IdentifiedByProperty` or one or more of its `@PropertyField`s must have `policy = Policy.IDENTIFIER`. Additionally, either the class or its containing package must be annotated with `@ResourcePrefix` to allow URIs to be shorted with RDF prefixes.
