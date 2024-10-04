package gr.ntua.ivml.mint.util;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.concurrent.GroovyTransform;
import gr.ntua.ivml.mint.concurrent.Queues;
import gr.ntua.ivml.mint.f.Interceptor;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Enrichment;
import gr.ntua.ivml.mint.persistent.Item;
import gr.ntua.ivml.mint.persistent.Transformation;
import gr.ntua.ivml.mint.persistent.XmlSchema;
import gr.ntua.ivml.mint.xml.util.XmlValueUtils;
import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;

// accept already filtered Annotations and apply them to an EDM record
public class EdmAnnotation {
	private static final Logger log = Logger.getLogger( EdmAnnotation.class );

	// some setup
	public boolean dontInsertDuplications = true;
	public boolean preRemoveDuplications = true;
	
	public Function<Document, String> idExtract;
	
	private Set<String> uriSet = new HashSet<String>();
	
	static List<String> providedChoIdx = Arrays.asList(
			// basetype in ProvidedCHO is unordered
			// the dc and dcterms elements can come in any order
			"{http://purl.org/dc/elements/1.1/}:description",
			"{http://purl.org/dc/elements/1.1/}:title",
			"{http://purl.org/dc/elements/1.1/}:creator",
			"{http://purl.org/dc/elements/1.1/}:contributor",
			"{http://purl.org/dc/elements/1.1/}:publisher",
			"{http://purl.org/dc/elements/1.1/}:subject",
			"{http://purl.org/dc/elements/1.1/}:type",
			"{http://purl.org/dc/elements/1.1/}:coverage",
			"{http://purl.org/dc/elements/1.1/}:date",
			"{http://purl.org/dc/elements/1.1/}:format",
			"{http://purl.org/dc/elements/1.1/}:source",
			"{http://purl.org/dc/elements/1.1/}:rights",
			"{http://purl.org/dc/elements/1.1/}:relation",
            

			"{http://purl.org/dc/terms/}:hasPart",
			"{http://purl.org/dc/terms/}:provenance",			
			"{http://purl.org/dc/terms/}:spatial",
			"{http://purl.org/dc/terms/}:isPartOf",
			"{http://purl.org/dc/terms/}:medium",

			// extension is ordered but has to come after base type anyway
			"{http://www.europeana.eu/schemas/edm/}:currentLocation",
			"{http://www.europeana.eu/schemas/edm/}:hasMet" ,
			"{http://www.europeana.eu/schemas/edm/}:hasType" ,
			"{http://www.europeana.eu/schemas/edm/}:incorporates",
			"{http://www.europeana.eu/schemas/edm/}:isDerivativeOf",
			"{http://www.europeana.eu/schemas/edm/}:isNextInSequence" ,
			"{http://www.europeana.eu/schemas/edm/}:isRelatedTo",
			"{http://www.europeana.eu/schemas/edm/}:isRepresentationOf",
			"{http://www.europeana.eu/schemas/edm/}:isSimilarTo",
			"{http://www.europeana.eu/schemas/edm/}:isSuccessorOf",
			"{http://www.europeana.eu/schemas/edm/}:realizes",
			
			"{http://www.europeana.eu/schemas/edm/}:type"			
		);

	// scope: nothing / records / total / removed 
	public HashMap<String, Integer> counters = new HashMap<>();
	
	private static HashMap<String, Integer> elemenValueByName = new HashMap<String,Integer>() {{
	    put("isRelatedTo", 1);
	    put("subject", 2);
	}};

	// the minimal info  needed for insert into EDM is kept here
	public static class SimpleAnnotation {
		public String id, confidence, field, literal, language, uri, agent;
	}
	
	public static class SimpleResult {
		public String fieldname;
		public int added;
		public int duplicates;
	}
	
	HashMap<String, List<SimpleAnnotation>> data  = new HashMap<>();
	
	public void addFlatJson( Stream<ObjectNode> annotations ) {
		annotations
			.map( node -> simplify( (ObjectNode) node ))
			// lists, keyed on the id we want to enrich
			.forEach( sa -> data.computeIfAbsent( sa.id, (k)->new ArrayList<>()).add( sa ));
	}
	
	
	public void setDefaultIdExtract() {
		idExtract = (doc) -> 
			XmlValueUtils.getUniqueValue(doc, "//*[local-name()='ProvidedCHO']/@*[local-name()='about']" )
			.orElse(null);
	}

	private void notEmptyAction( ObjectNode node, String field, Consumer<String> action ) {
		JsonNode n = node.path( field );
		if( n.isMissingNode()) return;
		if( n.isNull()) return;
		
		String val = n.asText();
		if( StringUtils.empty(val)) return;
		action.accept(val);
	}
	
	private SimpleAnnotation simplify( ObjectNode flatJson ) {
		SimpleAnnotation res = new SimpleAnnotation();

		notEmptyAction( flatJson, "recordId", s->res.id=s );
		notEmptyAction( flatJson, "targetField", s->res.field=s );
		notEmptyAction( flatJson, "confidence", s->res.confidence = s );
		notEmptyAction( flatJson, "annotatorType", s-> {
			if( "person".equalsIgnoreCase(s))
				res.agent = "Person";
			else if( s.toLowerCase().contains("software"))
				res.agent = "SoftwareAgent";
		});
		notEmptyAction( flatJson, "textLanguage", s->res.language = s );
		
		notEmptyAction( flatJson, "textValue", s-> {
			if( res.language != null ) {
				int languagePos = s.lastIndexOf("@");
				if( languagePos >= 0 )
					res.literal = s.substring(0 , languagePos );
				else 
					res.literal = s;
			}
			else
				res.literal =s;
		});
		
		notEmptyAction( flatJson, "uri", s->res.uri = s );
		return res;
	}
	
	public static class FieldInfo {
		public String namespace, elementName;
		public FieldInfo( String namespace, String elementName ) {
			this.elementName = elementName;
			this.namespace = namespace;
		}
	}
	
	
	// find for a fieldname the correct namespace, ignore prefix, mostly not needed.
	private Optional<FieldInfo> findFieldInfoByName( String name ) {
		// seems to be the only ambiguous field in providedcho
		if( name.equals( "edm:type"))
			return Optional.of( new FieldInfo("http://www.europeana.eu/schemas/edm/", "type" ));
		if( name.contains(":")) {
			name = name.substring( name.indexOf(":")+1);
		}
		for( String field: providedChoIdx ) {
			if( field.endsWith(":"+name)) {
				int splitPos = field.lastIndexOf(':');
				String namespace = field.substring( 1, splitPos-1);
				return Optional.of( new FieldInfo( namespace, name ));
			}
		}
		return Optional.empty();
	}

	
	private void addLang( Element elem, String lang  ) {
		Attribute attLang = new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", lang );
		elem.addAttribute(attLang);
	}

	private void addConfidence( Element elem, String confidence ) {
		if( StringUtils.empty(confidence)) return;
		
		String conf = "0.0";
		try {
			float val = Float.parseFloat(confidence);
			if( val >= 0.0 && val <= 100.0 )
				conf = String.format("%.2g", val );
		} catch( Exception e ) {
			log.debug("'" + confidence + "' not a float");
			return;
		}
		Attribute att = new Attribute("edm:confidenceLevel", "http://www.europeana.eu/schemas/edm/", conf);
		elem.addAttribute( att );
	}

	private void addAgent( Element elem, String agent ) {
		Attribute att = new Attribute("edm:wasGeneratedBy", "http://www.europeana.eu/schemas/edm/", agent);
		elem.addAttribute( att );
	}
		
	private SimpleResult enrichLiteral( Document doc, SimpleAnnotation sa  ) {
		Optional<FieldInfo> fieldInfoOpt = findFieldInfoByName( sa.field );
		
		if( !fieldInfoOpt.isPresent()  ) {
			log.error( "Unknown field name "+sa.field+" for enrich");
			return null;
		}
		
		SimpleResult r = new SimpleResult();
		r.fieldname = fieldInfoOpt.get().elementName;
		r.added = 1;
		
		Element elem = Item.createElement(doc,  fieldInfoOpt.get().elementName, fieldInfoOpt.get().namespace,
				providedChoIdx, "//*[local-name()='ProvidedCHO']" );
		if( sa.language != null )
			addLang( elem, sa.language );
		if( sa.agent != null )
			addAgent( elem, sa.agent );
		if( sa.confidence != null )
			addConfidence(elem, sa.confidence );
		
		elem.appendChild(sa.literal);
		return r;
	}
	
	private SimpleResult enrichUri( Document doc, SimpleAnnotation sa ) {
		Optional<FieldInfo> fieldInfoOpt = findFieldInfoByName( sa.field );
		
		if( !fieldInfoOpt.isPresent()  ) {
			log.error( "Unknown field name "+sa.field+" for enrich");
			return null;
		}
		
		SimpleResult r = new SimpleResult();
		r.fieldname = fieldInfoOpt.get().elementName;

		if( dontInsertDuplications && uriSet.contains( sa.uri )) {
			r.duplicates += 1;
			return	r;	
		}

		uriSet.add( sa.uri );
		
		Element elem = Item.createElement(doc, fieldInfoOpt.get().elementName, fieldInfoOpt.get().namespace, 
			providedChoIdx, "//*[local-name()='ProvidedCHO']" );
		elem.addAttribute( new Attribute( "rdf:resource", "http://www.w3.org/1999/02/22-rdf-syntax-ns#", sa.uri ));

		if( sa.agent != null )
			addAgent( elem, sa.agent );
		if( sa.confidence != null )
			addConfidence(elem, sa.confidence );
		r.added += 1;

		return r;
	}
	
	private boolean isFirstElementMoreValueable( Element first, Element second ) {
		// just related to and subject are low quality resource tags, the rest is more specific
		// should use confidence in another version of this
		// first one wins randomly in a tie ... not that important
		int firstVal = elemenValueByName.getOrDefault(first.getLocalName(), 10);
		int secondVal = elemenValueByName.getOrDefault(second.getLocalName(), 10);
		return firstVal>=secondVal;
	}
	
	public void removeDuplicateRdfResource( Document doc ) {
		
		// elements with rdf:resource
		Nodes nodes = doc.query("//*[local-name()='ProvidedCHO']/*[@*[local-name()='resource']]");
		List<Element> elementsToDelete = new ArrayList<>();
		
		Map<String, Element> values = new HashMap<>();
		for (int i = 0; i < nodes.size(); i++) {
			Element elem = (Element) nodes.get(i);
			String resourceValue = elem.getAttributeValue("resource", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");

			Element oldElem = values.get(resourceValue);
			// remove 'less' valuable / specific element, keep the other
			if (oldElem == null) {
				values.put(resourceValue, elem);
				continue;
			}
			if( isFirstElementMoreValueable( oldElem, elem )) {
				elementsToDelete.add( elem );
			} else {
				elementsToDelete.add(oldElem );
				values.put( resourceValue, elem);
			}
		}
		for( Element e:elementsToDelete) { 
			e.getParent().removeChild( e );
			log.info( "Removed duplicated " + e.getLocalName());
		}	
	}
	
	public SimpleResult enrich( Document doc, SimpleAnnotation sa ) {
		if( sa.literal != null ) 
			return enrichLiteral( doc, sa );
		else
			return enrichUri( doc,sa );
	}
	
	// makes enriched Documents, keeps track of some numbers
	public Interceptor<Document, Document> getDocumentInterceptor() {
		return Interceptor.modifyInterceptor( doc -> {
			if( preRemoveDuplications) removeDuplicateRdfResource(doc);
			String id = idExtract.apply(doc);
			HashSet<String> fieldAdded = new HashSet<>();
			for( SimpleAnnotation a: data.getOrDefault( id, Collections.emptyList())) { 
				SimpleResult r = enrich( doc, a );
				if( r.added > 0 )
					fieldAdded.add( r.fieldname );
				counters.merge( r.fieldname+":added", r.added, Integer::sum );
				counters.merge( "total:added", r.added, Integer::sum );
				
				counters.merge( r.fieldname+":duplicate", r.duplicates, Integer::sum );
				counters.merge( "total:duplicate", r.duplicates, Integer::sum );
			}
			for( String fieldname: fieldAdded ) 
				counters.merge( fieldname+ " annotated records", 1, Integer::sum );
			if( !fieldAdded.isEmpty())
				counters.merge( "annotated records", 1, Integer::sum );
			else
				counters.merge( "not annotated records", 1, Integer::sum );
		});
	}
	
	// queue the enrich job
	public static void enrich( Dataset ds, Enrichment enrichment, ObjectNode filters ) 
		throws Exception {
				
			EdmAnnotation annotator = new EdmAnnotation();
			Stream<ObjectNode> annotations = enrichment.streamFlatJson();

			final AnnotationFilter.FilterModifier fm = AnnotationFilter.buildFromJson(filters);
			
			List<XmlSchema> targetSchemas = XmlSchema.getSchemasFromConfig("enrichment.targetSchema");
			final XmlSchema targetSchema = targetSchemas.size()>0?targetSchemas.get(0):ds.getSchema();
			
			if( fm != null ) {
				annotations = annotations
					.map( a-> fm.apply(a) )
					.filter( a-> a!= null );					
			}
			

			annotator.addFlatJson(annotations);
			annotator.setDefaultIdExtract();
			
			Interceptor<Item,Item> interceptor = new Interceptors.ItemDocWrapInterceptor(
					annotator.getDocumentInterceptor()
					, true);

			GroovyTransform gt = GroovyTransform.defaultFromDataset(ds, interceptor, 
					Optional.of( tr -> {
						tr.setName( "Enriched with " + enrichment.getName());
						tr.setEnrichment(enrichment);
						tr.setSchema(targetSchema);
					}));
			gt.postProcessor = Optional.of( (Transformation tr) -> {
				int unmatched = enrichment.getRecordCount().intValue() 
						- annotator.counters.getOrDefault( "total:added", 0 )
						-annotator.counters.getOrDefault( "total:duplicate", 0 );
				
				String details = annotator.counters.entrySet().stream()
					.map( entry-> entry.getKey()+" : " + entry.getValue() )
					.collect( Collectors.joining( "\n"));
				
				if( unmatched > 0 ) details = "unmatched : " + unmatched + "\n" + details;
				
				tr.setReport(details);
				if( fm != null )  {
					ObjectNode json = filters.objectNode();
					json.set( "filters", filters );
							
					tr.setJsonParameters( json.toString());
				}
	    	} );

			Queues.queue(gt, "db");
	}
}
