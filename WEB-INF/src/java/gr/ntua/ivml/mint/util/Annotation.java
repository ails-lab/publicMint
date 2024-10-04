package gr.ntua.ivml.mint.util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import gr.ntua.ivml.mint.concurrent.Queues;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.persistent.Enrichment;

public class Annotation {
	private static final Logger log = Logger.getLogger( Annotation.class );
			
	
	public static class LiteralOrUrl {
		
	}
	
	public static class Literal extends LiteralOrUrl {
		public String type ="Literal";
		public String value, language;  
		public Literal() {}
		public Literal( String value, String language ) {
			this.value = value;
			this.language = language;
		}
	}
	
	public static class Url extends LiteralOrUrl {
		public String type = "Url";
		public String value;  
		public Url() {}
		public Url( String value ) {
			this.value = value;
		}
	}
	
	public static class TextPosition {
		public String type = "TextPositionSelector";
		public Integer start, end;
	}
	
	public static class RefinedBy {
		public String type="TextPositionSelector";
		public int start, end;
	}
	
	public static class Selector {		
		public String type;
		public String property, rdfPath; // one of those
		
		@JsonDeserialize(using = LiteralDeserializer.class) 
		public LiteralOrUrl destination;
		
		public RefinedBy refinedBy;
	}
		
	public static class Target {
		public String source;
		public Selector selector;
	}
	
	
	// single uri serialize as TextNode
	// multiple as ArrayNode of TextNode
	// literal as ObjectNode with type, value, language
	// and vice versa
	
	public static class Body {
		
		public List<String> uris;
		public String value, language;

		public Body() {
			
		}
		
		// convenience constructors , deserialize
		public Body( ArrayNode manyUris ) {
			uris = new ArrayList<>();
			for( JsonNode uri:manyUris ) {
				if( uri instanceof TextNode ) {
					uris.add( uri.textValue());
				}
			}
		}

		public Body( TextNode text ) {
			uris = Arrays.asList(text.textValue());
		}
		
		public Body( ObjectNode obj ) {
			value = obj.get( "value").textValue();
			language = obj.get( "language").textValue();
		}
		
		// serialize into tree
		public JsonNode toJson() {
			if( value != null ) {
				ObjectNode res = new ObjectMapper().createObjectNode();
				res.put( "value", value );
				if( language != null ) 
					res.put( "language", language);
				res.put( "type", "TextualBody");
				return res;
			}
			
			if( uris != null ) {
				if( uris.size() == 1 ) {
					return new TextNode( uris.get(0));
				} else {
					ArrayNode res = new ObjectMapper().createArrayNode();
					for( String uri: uris)
						res.add( new TextNode( uri ));
					return res;
				}
			}
			return null;
		}
		
	}
	
	public static class BodyDeserializer extends StdDeserializer<Body> { 

	    public BodyDeserializer() { 
	        this(null); 
	    } 

	    public BodyDeserializer(Class<?> vc) { 
	        super(vc); 
	    }

	    @Override
	    public Body deserialize(JsonParser jp, DeserializationContext ctxt) 
	      throws IOException, JsonProcessingException {
	        JsonNode node = jp.getCodec().readTree(jp);
	        if( node instanceof ArrayNode ) return new Body( (ArrayNode) node );
	        if( node instanceof TextNode ) return new Body( (TextNode) node );
	        if( node instanceof ObjectNode ) return new Body( (ObjectNode) node );
	        return null;
	    }
	}
	
	public static class BodySerializer extends StdSerializer<Body> {
		   public BodySerializer() { 
		      this(null); 
		   } 
		   public BodySerializer(Class<Body> t) { 
		      super(t); 
		   } 
		   @Override 
		   public void serialize(Body value, 
		      JsonGenerator generator, SerializerProvider serProv) throws IOException { 
		      generator.writeObject( value.toJson()); 
		   } 
		}
	
	public static class LiteralDeserializer extends StdDeserializer<LiteralOrUrl> { 

	    public LiteralDeserializer() { 
	        this(null); 
	    } 

	    public LiteralDeserializer(Class<?> vc) { 
	        super(vc); 
	    }

	    @Override
	    public LiteralOrUrl deserialize(JsonParser jp, DeserializationContext ctxt) 
	      throws IOException, JsonProcessingException {
	        JsonNode node = jp.getCodec().readTree(jp);
	        if( "Url".equals( node.path("type").asText())) {
	        	return new Url( node.path( "value").asText());
	        } else {
	        	return new Literal( node.path( "value").asText(), node.path( "language").asText());
	        }
	    }
	}
	
	public String id;
	public String type="Annotation";
	public String created;
	public String confidence;
	public String scope;
	
	public static class Creator {
		public String id, name, type;
	}
	
	public Creator creator;
	public Target target;

	public static class Review {
		public String type, recommendation;
		public Float score;
		
		// type is Rating or Validation
		// recommendation is accept
	}
	
	public Review review;
	
	@JsonDeserialize(using = BodyDeserializer.class) 
	@JsonSerialize( using = BodySerializer.class )
	public Body body;
	
	
	public static List<Annotation> fromJsonLd( ObjectNode json ) {
		// ignore json["@context"}
		try {
			ArrayNode jsonGraph = (ArrayNode) json.get( "@graph");
			List<Annotation> res = new ArrayList<>();
			
			for( JsonNode n: jsonGraph ) {
				Annotation a = Jackson.om().treeToValue(n, Annotation.class );
				res.add( a );
			}
			return res;
		} catch(Exception e ) {
			log.error( "Json not annotation json ld");
			return Collections.emptyList();
		}
	}
	
	public static List<Annotation> fromText( String jsonText ) {
		try {
			ObjectNode node = (ObjectNode) Jackson.om().readTree(jsonText);
			return fromJsonLd( node );
		} catch(Exception e ) {
			log.error( "Text not json");
			return Collections.emptyList();
		}
	}
	
	public static ArrayNode toJson( List<Annotation> l ) {
		ArrayNode annos = Jackson.om().createArrayNode();
		for( Annotation a: l ) {
			annos.add( Jackson.om().valueToTree(a));
		}
		return annos;
	}
	
	
	public Annotation textBody( String literal, String language ) {
		this.body = new Body();
		this.body.language = language;
		this.body.value = literal;
		
		return this;
	}
	
	public Annotation uriBody( String url ) {
		this.body = new Body();
		this.body.uris = Arrays.asList(url);
		return this;
	}
	
	public Annotation targetId( String url ) {
		if( this.target == null ) 
			this.target = new Target();
		this.target.source = url;
		return this;
	}
	
	// use a fieldname with naemspace prefix from context
	public Annotation textField( String fieldname, String literal, String language, Optional<String> confidence ) {
		textBody( literal, language );
		if( target == null)
			target = new Target();
		
		if( target.selector == null ) 
			target.selector = new Selector();
		
		target.selector.type = "RDFPropertySelector";
		target.selector.property = fieldname;
		
		scope = fieldname;
		
		confidence.ifPresent( c-> this.confidence = c );
		
		if( this.creator == null )
			this.creator = new Creator();
			
		this.creator.type = "Software";
		
		return this;
	}
	
	public Annotation humanCreator() {
		if( this.creator == null )
			this.creator = new Creator();
			
		this.creator.type = "Person";
		return this;
	}
	
	public int size() {
		if( body == null ) return 0;
		if( body.value != null ) return 1;
		if( body.uris == null ) return 0;
		return body.uris.size();
	}
	
	public Annotation softwareCreator() {
		if( this.creator == null )
			this.creator = new Creator();
			
		this.creator.type = "Software";
		return this;		
	}
	
	
	public Annotation optConfidence( Optional<String> confidence ) { 
		confidence.ifPresent( c-> this.confidence = c );
		return this;
	}
	
	public Annotation scope( String fieldname ) {
		this.scope = fieldname;
		return this;
	}
	
 	public Annotation uriField( String fieldname, String url, Optional<String> confidence ) {
 		uriBody( url );
 		optConfidence(confidence);
 		scope( fieldname );
 		

		return this;
	}
	
	public static void main( String[] args ) {
		ObjectMapper om = new ObjectMapper();
		om.enable(SerializationFeature.INDENT_OUTPUT);

		try {
			JsonNode node = om.readTree( Annotation.class.getResourceAsStream("annotationExample.json"));
			System.out.println( om.writeValueAsString( node.get("@graph")));

			for( JsonNode n: (ArrayNode) (node.get("@graph"))) {
				Annotation a = (Annotation) om.treeToValue(n, Annotation.class );
				JsonNode node2 = om.valueToTree(a);
				System.out.println( "Roundtrip:\n" + om.writeValueAsString( node2 ));
				
			}
			// om.writerWithDefaultPrettyPrinter().writeValue(System.out, node.get("@graph").get(0));
		} catch( Exception e ) {
			e.printStackTrace();
		}
	}

	public ArrayNode flatJson() {
		ObjectNode template = Jackson.om().createObjectNode();
		if( target != null ) {
			template.put( "recordId", target.source );
			if( target.selector != null ) {
				// either property or rdfpath
				template.put( "analyzedField",StringUtils.join("", target.selector.property, target.selector.rdfPath));
			}
		}
		if( !StringUtils.empty(confidence)) template.put( "confidence", confidence );
		if( review != null ) {
			template.put( "review", review.recommendation );
			if( review.score != null ) template.put( "reviewScore",  review.score );
		}
		if( creator != null ) {
			if( !StringUtils.empty(creator.type)) template.put( "annotatorType", creator.type);
			String creatorIdName = StringUtils.join( " ", creator.id, creator.name );
			if( !StringUtils.empty(creatorIdName))
				template.put( "annotatorName", creatorIdName);
		}
		if( !StringUtils.empty(scope))
			template.put("targetField", scope );
		
		// now it depends on the body
		if( body != null ) {
			if( body.uris != null ) {
				if( body.uris.size() > 1 ) {
					ArrayNode res = Jackson.om().createArrayNode();
					for(  String uri: body.uris ) {
						ObjectNode elem = template.deepCopy();
						elem.put( "uri", uri );
						res.add( elem );
					}
					return res;
				} else {
					template.put( "uri", body.uris.get( 0 ) );
				}
			} else if( body.value != null ) {
			    if( !StringUtils.empty( body.language ))
			    	template.put( "textLanguage", body.language );  
			    template.put( "textValue", StringUtils.join( body.value, "@", body.language ));
			}
		}
		ArrayNode res = template.arrayNode();
		res.add( template );
		return res;
	}
		
	public static ObjectNode testParseText( byte[] data ) {
		try {
			return (ObjectNode) Jackson.om().readTree(data);
		} catch(Exception e ) {
			log.warn( "Not a valid json Object", e );
		}
		return null;
	}
	
	public static ObjectNode testParseGz( byte[] data ) {
		try ( ByteArrayInputStream bis = new ByteArrayInputStream( data ) ;
				GzipCompressorInputStream gis = new GzipCompressorInputStream(bis) ) {
			byte[] uncompressed = IOUtils.toByteArray(gis);	
			return testParseText( uncompressed );
		} catch( Exception e ) {
			log.warn( "Not a valid gz", e );
		}
		return null;
	}
	
	public static ObjectNode testParseTgz( byte[] data ) {
		
		ObjectNode res = Jackson.om().createObjectNode();
		try ( ByteArrayInputStream bis = new ByteArrayInputStream( data ) ;
			GzipCompressorInputStream gis = new GzipCompressorInputStream(bis) ;
			TarArchiveInputStream tis = new TarArchiveInputStream(gis) ) {
		
			TarArchiveEntry tae = tis.getNextTarEntry();
			while( tae != null ) {
				if( tae.isDirectory()) continue;
				byte[] entry = new byte[(int) tae.getSize()];
				tis.read( entry, 0, entry.length );
				// merge all jsonld files
				if( tae.getName().endsWith("jsonld")) {
					ObjectNode json = testParseText(entry);
					if( json != null ) {
						if( res.has( "@graph")) {
							((ArrayNode)res.get("@graph")).addAll(
									(ArrayNode) json.get("@graph")
									);
						} else {
							res = json;
						}
					}
				}
				tae = tis.getNextTarEntry();
			}
			return res;
		} catch( Exception e ) {
			log.warn( "Not a tgz or invalid tgz", e);
		}
		return null;
	}
	
	public static Object getDefault( Object... args) {
		for( Object e: args ) 
			if( e!= null ) return e;
		return null;
	}
	
	public static <T> T getDefault( Supplier<T>... suppliers ) {
		if( suppliers == null ) return null;
		for( Supplier<T> s: suppliers ) {
			T t = s.get();
			if( t != null ) return t; 
		}
		return null;
	}
	
	public static ObjectNode getEdmContext() {
		try {
			ObjectNode template = (ObjectNode) Jackson.om().readTree( Annotation.class.getClassLoader().getResourceAsStream("/gr/ntua/ivml/mint/util/annotation_template.json" ));
			return (ObjectNode) template.get( "@context");
		} catch( Exception e ) {
			log.error( "Standard Context not found", e );
			return null;
		}
	}
	
	// create one with an output stream and keep adding annotation objects
	// it will stream out the corresponding json
	
	public static class AnnotationStream {
		JsonGenerator jsonGenerator;
		ObjectMapper objectMapper = Jackson.om();
		
		public AnnotationStream( OutputStream outputStream ) throws IOException {
			final JsonFactory jsonFactory = new JsonFactory();
			jsonGenerator = jsonFactory.createGenerator(outputStream);
			
			jsonGenerator.writeStartObject();
			jsonGenerator.writeFieldName( "@context");
			objectMapper.writeTree( jsonGenerator, Annotation.getEdmContext());
			jsonGenerator.writeFieldName("@graph");
			jsonGenerator.writeStartArray();
		};
		
		public void addAnnotation( Annotation annotation ) {
			try {
				ObjectNode on = objectMapper.valueToTree(annotation);
				objectMapper.writeTree( jsonGenerator, on );
			} catch (Exception e) {
				log.error( "", e );
			} 
		};
		
		public void close() throws IOException {
			jsonGenerator.writeEndArray();
			jsonGenerator.writeEndObject();
			jsonGenerator.flush();
			jsonGenerator.close();
		};
		
	}
	
	public static void loadAnnotationLdFromUrl(Enrichment e, String url) {
		// create runnable and queue it
		final long enrichmentId = e.getDbID();
		DB.SessionRunnable r = ()->{
			Enrichment enrichment = DB.getEnrichmentDAO().getById(enrichmentId, false);
			enrichment.updateStatus( "FETCHING", "Fetching '" + url + "'" );
			Fetch fetch = Fetch.fetchUrl( url );
			if( fetch.errMsg != null ) {
				enrichment.updateStatus( "ERROR", "Fetching '" + url + "'\n" + fetch.errMsg );
				return;
			}
			if(( enrichment.getName() == null) && (fetch.name != null ) )
				enrichment.setName( fetch.name );
			
			ObjectNode res =  getDefault(()-> testParseTgz( fetch.data ),
					()->testParseGz( fetch.data ),
					()->testParseText( fetch.data ) ,()->null );
			if( res == null ) {
				enrichment.updateStatus( "ERROR", "parsing of retrieved Link failed");
				return;
			};
			
			// parsing ... stats
			try {
				enrichment.updateStatus( "PARSING", "Is this well formed json-ld for annotation? '" + url + "'" );
				List<Annotation> check = Annotation.fromJsonLd(res);
				if( check.size() > 0 ) {
					int size = check.stream().mapToInt(a->a.size()).sum();
					enrichment.setRecordCount((long)size);
					enrichment.setFormat( Enrichment.Format.ANNOTATION_JSONLD);
				} else {
					throw new Exception( "No @graph content in json-ld" );
				}
				
				enrichment.setUncompressedContent(Jackson.om().writerWithDefaultPrettyPrinter().writeValueAsBytes(res));
				
			} catch( Exception exception ) {
				log.warn( "", exception );
				enrichment.updateStatus( "ERROR", exception.getMessage());
				return;
			}
			enrichment.updateStatus("ANALYZE", "Building statistics");
			try {
				new AnnotationStats().buildStats(enrichment);
				DB.getEnrichmentDAO().makePersistent(enrichment);				
			} catch( Exception ex ) {
				log.error( "Stats building problem", ex );
				enrichment.updateStatus( "ERROR", "Stats buidling failed");
			} 
			DB.flush();
			DB.commit();
		};
		Queues.queue(r, "db");
	}
}
