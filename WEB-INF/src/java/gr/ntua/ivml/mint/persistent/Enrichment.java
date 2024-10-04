package gr.ntua.ivml.mint.persistent;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jena.util.FileUtils;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.api.handlers.AnnotationHandlers.EnrichmentType;
import gr.ntua.ivml.mint.concurrent.GroovyTransform;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.f.ThrowingConsumer;
import gr.ntua.ivml.mint.util.Annotation;
import gr.ntua.ivml.mint.util.AnnotationFilter;
import gr.ntua.ivml.mint.util.Config;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.JacksonBuilder;
import gr.ntua.ivml.mint.util.StringUtils;
import gr.ntua.ivml.mint.util.Trie;

public class Enrichment implements AccessCheckEnabled {
    private static final Logger log = Logger.getLogger(Enrichment.class);

    public static enum Format { CSV_GZ, ANNOTATION_JSONLD };
    
	private Long dbID;
    private String name;
    private String headers;
    private User creator;
    private Organization organization;
    private Long bytesize;
	// rename this to recordCount
    private Long recordCount;
    public List<Integer> projectIds = new ArrayList<>();
    private byte[] content;
    private String jsonStats;
    
	private Date creationDate;
	private Format format;

    /**
     * Convenience class to allow code to run on one  or more rows of the csv file, based on a lookup
     * key. builder = Enrichment.getEnrichBuilder( indx col )
     * builder.enrich( "some lookup value", row -> { what you want to do with any matching row in csv } )
     * reuse the builder, it is keeping all data in memory. 
     * @author arne
     *
     */
    public static class EnrichBuilder {
		public Map<String,List<String[]>> enrichmentIndex;

		public EnrichBuilder( Enrichment e, int idx ) {
			enrichmentIndex = e.asHash(idx);
		}
		
		public void enrich( String key, ThrowingConsumer< String[]> rowProcessor ) {
			try {
				List<String[]> rows = enrichmentIndex.get(key);
				if( rows == null ) return;
				for( String[] row: rows )
					rowProcessor.accept(row);
			} catch( Exception e ) {
				log.debug( "Row processor threw exception" ,e );
			}
		}
		
		// for classic loops
		// for( String[] row: enrich.getWithKey( key )) {
		//   something with row[] }
		public List<String[]> getWithKey( String key ) {
			List<String[]> rows = enrichmentIndex.get(key);
			if( rows == null ) return Collections.emptyList();
			return rows;
		}
		
		// What is better? Not sure, but the latter again is easier to read, less hidden magic.
	}
	
    public static class EnrichBuilderTrie {
 		public Trie<List<String[]>> enrichmentIndex;
 		float threshhold;
 		
 		public EnrichBuilderTrie( Enrichment e, int idx ) {
 			this( e, idx, 0.999f );
 		}
 		
 		// only accept hits with >threshhold confidence 		
 		public EnrichBuilderTrie( Enrichment e, int idx, float threshhold ) {
 			enrichmentIndex = new Trie<>();
 			e.asHash(idx).forEach( ( str, list ) -> enrichmentIndex.insertValue(str, list));
 			this.threshhold = threshhold;
 		}
 		
 		public void enrich( String key, ThrowingConsumer< String[]> rowProcessor ) {
 			try {
 				Optional<Trie.TrieLookup<List<String[]>>> optRes = enrichmentIndex.lookup(key);
 				if( !optRes.isPresent()) return;
 				if( optRes.get().confidence < threshhold ) return;
 				
 				List<String[]> rows = optRes.get().result;
 				if( rows == null ) return;
 				for( String[] row: rows )
 					rowProcessor.accept(row);
 			} catch( Exception e ) {
 				log.debug( "Row processor threw exception" ,e );
 			}
 		}
 		
 		// for classic loops
 		// for( String[] row: enrich.getWithKey( key )) {
 		//   something with row[] }
 		public List<String[]> getWithKey( String key ) {
			Optional<Trie.TrieLookup<List<String[]>>> optRes = enrichmentIndex.lookup(key);
			if( !optRes.isPresent()) return Collections.emptyList();
			if( optRes.get().confidence < threshhold ) return Collections.emptyList();
			
			List<String[]> rows = optRes.get().result;
			if( rows == null ) return Collections.emptyList();
 			return rows;
 		}
 		
 		// What is better? Not sure, but the latter again is easier to read, less hidden magic.
 	}
 	
    public Enrichment() {
    	this.creationDate = new Date();
    	this.setStats(Jackson.om().createObjectNode());
    }
    
    public ArrayNode getValuesAsFlatJson( int start, int count ) {
    	ArrayNode res = Jackson.om().createArrayNode();

    	streamFlatJson().skip(start).limit(count).forEach( node -> res.add(node ));

    	return res;
    }
    
    public ArrayNode getValuesAsFlatJson( int start, int count, AnnotationFilter.FilterModifier filter ) {
    	ArrayNode res = Jackson.om().createArrayNode();

    	streamFlatJson()
    	.filter( on -> filter.apply(on) != null )
    	.skip(start).limit(count).forEach( node -> res.add(filter.apply(node) ));
    	return res;
    }
    
    
    
    public boolean canApply( Dataset ds ) {
    	// dataset needs to be configured schema
    	// enrichment needs to be json-ld
    	// for now
    	if( format != Format.ANNOTATION_JSONLD) return false;
    	
    	List<XmlSchema> eligibleSchemas = XmlSchema.getSchemasFromConfig("enrichment.schemas");
    	if( ds.isAnySchemaAndHasValidItems(eligibleSchemas)) return true;
    	
    	return false;
    	
    }
    
    // filterModifier takes an ObjectNode of a flattened Annotation and either makes it null, 
    // modifies it or returns it unmodified.
    
    
    public Stream<ObjectNode> streamFlatJson() {
    	switch( format ) {
    	case ANNOTATION_JSONLD:
    		return jsonLdStream();
    	case CSV_GZ:
    		return csvJsonStream();
    	default: return Stream.empty();	
    	}
    }
    
    private Stream<ObjectNode> csvJsonStream() {
    	List<ObjectNode> res = Collections.emptyList();
    	// could 
    	try (
        		ByteArrayInputStream bis = new ByteArrayInputStream( getContent());
        		GzipCompressorInputStream gzin = new GzipCompressorInputStream(bis)
        	) {
    			Iterable<CSVRecord> headerRecords = CSVFormat.DEFAULT.parse( new StringReader(headers ));
    			CSVRecord headerRecord = headerRecords.iterator().next();

    			Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(new InputStreamReader(gzin, "UTF8"));
    			res = StreamSupport.stream( records.spliterator(), false)
    				.map( record -> {
            			ObjectNode flatJson = Jackson.om().createObjectNode();
        				for( int i=0; (i<record.size()) && (i<headerRecord.size()); i++ ) 
        					flatJson.put( headerRecord.get( i ), record.get( i ));  
        				return flatJson;
    				})
    				// this stream would hold on to two inputStreams when returned and may not close them
    				// all is in memory anyway, so materialize the list and stream from there.
    				.collect(Collectors.toList());
    			
        	} catch( Exception e ) {
        		log.error( "Csv from db read failed", e );
        		return Stream.empty();
        	}
    	return res.stream();
    }
    	
    
    private Stream<ObjectNode> jsonLdStream() {
    	ObjectNode annJson = getAnnotationJson();
		List<Annotation> annotations = Annotation.fromJsonLd(annJson);
		return annotations.stream()
			 .flatMap( ann -> StreamSupport.stream(ann.flatJson().spliterator(), false))
			 .map( node -> (ObjectNode) node );    	
    }
    
        
    public boolean initialize (File tmp, String enrichmentName, EnrichmentType type, User u) throws IOException {
        //extract headers
        this.setCreator(u);
        this.setOrganization(u.getOrganization());
        this.setCreationDate(new Date());
        this.setName(enrichmentName);

        if (type.equals(EnrichmentType.CSV)) {
            Reader read = FileUtils.asBufferedUTF8( new FileInputStream( tmp ));
            
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(read);
            CSVRecord header = records.iterator().next();
            this.setHeaders(header.toString());

            Files.deleteIfExists(tmp.toPath());
            tmp = File.createTempFile("tmpResult", ".csv");

            long recordCount = 0;
            try (CSVPrinter printer = new CSVPrinter(FileUtils.asUTF8( new FileOutputStream(tmp)), CSVFormat.DEFAULT)) {
                for (CSVRecord record : records) {
                    recordCount++;
                    printer.printRecord(record);
                }
            } catch (IOException ex) {
                log.error( "Reading supplied CSV failed", ex);
                return false;
            }
            this.setRecordCount(recordCount - 1);

            byte[] gzip = makeGzippedContent(tmp);
            // make gzipped blob
            this.setContent(gzip);
            this.setBytesize((long)gzip.length);
            this.setFormat(Format.CSV_GZ);

            Files.deleteIfExists(tmp.toPath());
            return true;
        }
        else if (type.equals(EnrichmentType.JSON)) {
            ObjectMapper om = new ObjectMapper();

            String jsonFileString = IOUtils.toString(new FileInputStream(tmp), "UTF-8");
            JsonNode json = om.readTree(jsonFileString);
            setAnnotationJson( (ObjectNode) json);
            Files.deleteIfExists(tmp.toPath());
            return true;
        }

        return false;
    }
    
    // call only for status updates, will commit to db
    public void updateStatus( String status, String msg ) {
    	setStats(
        		JacksonBuilder.obj()
    			.put( "status", status)
    			.put( "date", (new Date()).toString())
    			.put( "msg", msg)
    			.getObj());
    	DB.getEnrichmentDAO().makePersistent(this);
    	DB.commit();
    }
    
    public void setStats( ObjectNode obj ) {
    	try {
    		setJsonStats( Jackson.om().writeValueAsString(obj));
    	} catch( Exception e ) {
    		log.error( "Unexpected json trouble", e );
    	}
    }
    
    public ObjectNode getStats() {
    	String val = getJsonStats();
    	if( val == null ) return Jackson.om().createObjectNode();
    	try {
    		return (ObjectNode)  Jackson.om().readTree(val);
    	} catch( Exception e ) {
    		log.error( "Unexpected json trouble", e );
    	}
    	return null;
    }
    
    
    public void setAnnotationJson( ObjectNode json ) {
    	format = Format.ANNOTATION_JSONLD;
    	ObjectMapper om = new ObjectMapper();
    	om.enable(SerializationFeature.INDENT_OUTPUT);
    	om.setPropertyInclusion(
    	   JsonInclude.Value.construct(Include.NON_NULL, Include.NON_NULL));
    	try {
    		byte[] jsonBytes = om.writeValueAsBytes(json);
    		
    		// was the record count set? If not, this is an estimate
    		if(( recordCount == null ) || ( recordCount == 0 )) {
    			ArrayNode graph = (ArrayNode) json.get("@graph");
    			this.setRecordCount((long) graph.size());
    		}

    		setUncompressedContent(jsonBytes);
    	} catch( Exception e ) {
    		log.error( "Unexpected json serialize error", e );
    	}
    }
    
    public void setUncompressedContent( byte[] uncompressed ) {
    	content = compress( uncompressed );
    	bytesize = (long) uncompressed.length;
    }
    
    public static byte[] compress( byte[] in ) {
        ByteArrayOutputStream baos = null;
        GzipCompressorOutputStream gz = null;

        try {
            baos = new ByteArrayOutputStream();
            gz = new GzipCompressorOutputStream(baos);
            gz.write(in);
            gz.flush();
            gz.close();
        }
        catch ( Exception e ) {
            log.error( "Unexpected Error on gzipping content", e );
        }
        finally {
            try {if( gz!= null ) gz.close();} catch( Exception e ){}
            try {if( baos!= null ) baos.close();} catch( Exception e ){}
        }
        return baos.toByteArray();    	
    }
    
    
    public byte[] makeGzippedContent (File tmp) {
    	
    	try {
    		return compress( Files.readAllBytes(tmp.toPath()));
        }
        catch ( Exception e ) {
            log.error( "Unexpected Error on gzipping content", e );
            return null;
        }
    }

    public List<String[]> getCsv() {
    	List<String[]> result = new ArrayList<>();
    	try (
    		ByteArrayInputStream bis = new ByteArrayInputStream( getContent());
    		GzipCompressorInputStream gzin = new GzipCompressorInputStream(bis)
    	) {
    		if (this.format.equals(Format.CSV_GZ)) {
    		Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(new InputStreamReader(gzin, "UTF8"));
    		for( CSVRecord record: records ) {
    			String[] row = new String[ record.size() ];
    			for( int i=0; i<record.size(); i++ )
    				row[i] = record.get( i );
    			result.add( row );
    		}
        }
            else {
                throw new Exception("Unsupported format");
            }
    		
    	} catch( Exception e ) {
    		log.error( "Csv from db read failed", e );
    	}
    	return result;
    }

    public Stream<String> getField( String fieldname ) {
    	return streamFlatJson()
    			.map( node -> node.path( fieldname ))
    			.filter( node -> !node.isMissingNode())
    			.map( node -> node.asText())
    			.filter( s -> !StringUtils.empty( s ));
    }
    
    public ObjectNode getAnnotationJson(){
        ObjectNode result;
        try (
    		ByteArrayInputStream bis = new ByteArrayInputStream( getContent());
    		GzipCompressorInputStream gzin = new GzipCompressorInputStream(bis)
    	) {
            if (!format.equals(Format.ANNOTATION_JSONLD))
                throw new Exception("Unsupported format");
            ObjectMapper om = new ObjectMapper();
            result = (ObjectNode) om.readTree(gzin);
            return result;
        }
        catch( Exception e ) {
    		log.error( "JSON from db read failed", e );
            return Jackson.om().createObjectNode();
    	}
    }
    
    // write headers back, then the blob
    // not closing the stream
    public void outputCsvToStream( OutputStream out ) {
		ByteArrayInputStream bis = new ByteArrayInputStream( getContent());
    	try(GzipCompressorInputStream gzin = new GzipCompressorInputStream(bis)) {
            
            out.write( (getHeaders()+"\n").getBytes("UTF-8"));
    		int input;
    		while(( input = gzin.read()) != -1 ) {
    			out.write( input );
    		}
    		out.flush();
    	} catch( Exception e ) {
    		log.error( "", e );
    	}
    }
    
    
    public Map<String, List<String[]>> asHash( int keyIdx ) {
    	return getCsv().stream().collect(Collectors.groupingBy( (String[] row) -> row[keyIdx]));
    }
    
    public EnrichBuilder getEnrichBuilder( int idxColumn ) {
    	return new EnrichBuilder( this, idxColumn);
    }

    // getters-setters
    public Long getDbID() {
        return dbID;
    }

    public void setDbID(Long dbID) {
        this.dbID = dbID;
    }

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public Long getBytesize() {
        return bytesize;
    }

    public void setBytesize(Long bytesize) {
        this.bytesize = bytesize;
    }

    public Long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Long recordCount) {
        this.recordCount = recordCount;
    }
    public List<Integer> getProjectIds() {
        return (projectIds == null) ? new ArrayList<Integer>() : projectIds;
    }

    public void setProjectIds(List<Integer> projectIds) {
        this.projectIds = projectIds;
    }

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] content) {
		this.content = content;
	}

	public Format getFormat() {
		return format;
	}

	public void setFormat(Format format) {
		this.format = format;
	}

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }


	public String getJsonStats() {
		return jsonStats;
	}


	public void setJsonStats(String jsonStats) {
		this.jsonStats = jsonStats;
	}
    
}

