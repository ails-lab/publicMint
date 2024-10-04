package gr.ntua.ivml.mint.services;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.f.Interceptor;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Enrichment;
import gr.ntua.ivml.mint.persistent.Enrichment.Format;
import gr.ntua.ivml.mint.persistent.Item;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.persistent.XmlSchema;
import gr.ntua.ivml.mint.util.Annotation;
import gr.ntua.ivml.mint.util.Annotation.AnnotationStream;
import gr.ntua.ivml.mint.util.AnnotationStats;
import gr.ntua.ivml.mint.util.CompressedObjectBuffer;
import gr.ntua.ivml.mint.util.Config;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.JobScheduler;
import gr.ntua.ivml.mint.util.StringUtils;

public class ImageAnalysis {
	private static final Logger log = Logger.getLogger( ImageAnalysis.class );
	private static final long PROGRESS_REPORT_INTERVAL = 120;
	
	private static JobScheduler jobScheduler = JobScheduler.create( 1 );
	
	public static String serverEndpointUrl =  Config.get( "imageAnalysis.server" );
	
	private static class ObjectRequest {
		public String min_confidence="0.8"; //0.0 - 1.0
		public String max_objects="1"; // need more ??
		public String service = "blip-vqa-base"; // internal or GoogleVision
		public String service_key ="";
		
		// the url for the image that needs analysis
		public String source = "";
		public String annotation_type = "internal"; // should be fixed
	}
	
	private static class Selector {
		public String conformsTo = "http://www.w3.org/TR/media-frags/";
		public String type = "FragmentSelector";
		public String value = "xywh=percent:0,0,100,100";
	}
	
	private static class ColorRequest {
		public String max_colors = "3";
		public String min_area = "0.15";
		public boolean foreground_detection = true;
		public String service = "blip-vqa-base"; //internal
		public Selector selector = new Selector();
		public String annotation_type = "internal"; //fixed
		
		public String ld_source = "Wikidata"; // probably ignored
		// input url
		public String source ="";
	}
	
	private static class ObjectResult implements Serializable {
		public String recordId;
		public String imageUrl;
		public String confidence;
		public String wikidataUri;
	}
	
	private static class ColorResult implements Serializable {
		public String recordId;
		public String imageUrl;
		public String wikidataUri;
		public String fashionUri;
	}
	
	
	public static enum AnalysisType {
		COLOR, OBJECT
	};
	
	public static class RecordUrl {
		public String recordId, url;
		public RecordUrl( String recordId, String url ) {
			this.recordId = recordId;
			this.url = url;
		}
	}
	

	public static class DatasetJob implements JobScheduler.Job {

		
		Dataset dataset;
		ObjectNode requestTemplate;
		AnalysisType analysisType;
		Interceptor<Item, RecordUrl> urlInterceptor;
		
		int currentItemStart = 0;
		Deque<RecordUrl> batch = new ConcurrentLinkedDeque<RecordUrl>();
		boolean noMoreUrls = false;
		private CompressedObjectBuffer<?> resultBuffer;
		
		Object mutex = new Object();
		private long lastReportTime;
		private long startTime;
		
		// reporting counters
		AtomicInteger urlCount = new AtomicInteger(0);
		AtomicInteger resultCount = new AtomicInteger(0);
		Enrichment enrichment;
		
		public DatasetJob( Enrichment enrichment, Dataset ds, ObjectNode requestTemplate, 
				AnalysisType analysisType, Interceptor<Item, RecordUrl> urlInterceptor ) {
			this.urlInterceptor = urlInterceptor;
			this.analysisType = analysisType;
			this.requestTemplate = requestTemplate;
			this.enrichment = enrichment;
			this.dataset = ds;
			
			resultBuffer = new CompressedObjectBuffer<>();
			lastReportTime = System.currentTimeMillis();
			startTime = lastReportTime;
		}
		
		
		
		
		private boolean getMoreUrls() {
			List<Item> items = DB.getItemDAO().getItemsByDataset(dataset, currentItemStart, 100);
			currentItemStart += 100;
			
			if( items.isEmpty()) return false;

			try {
				urlInterceptor.iterate(items, ru-> batch.add( ru ));
				return true;
			} catch( Exception e ) {
				log.error( "", e );
				return false;
			}			
		}
		
		// better is thread safe
		@Override
		public void executeNextSubjob() {

			if( noMoreUrls ) return;

			RecordUrl url = null;
			synchronized(this) {
				while( batch.isEmpty() && getMoreUrls());
				if( batch.isEmpty()) {
					noMoreUrls = true;
					return;
				}
				url = batch.pop();
			}
			// should be impossible
			if( url == null ) return;
			urlCount.getAndIncrement();
			// Now work on the url
			log.debug( "Image analyze " + url.url );
			requestTemplate.put( "source", url.url);
			if( analysisType==AnalysisType.COLOR ) {
				ObjectNode result = request( "/v1/color", requestTemplate );
				storeColorResult(result,  url );				
			} else { //object analysis
				ObjectNode result = request( "/v1/object", requestTemplate );
				storeObjectResult( result, url );
			}
			progressReport();
			
		}
		
		private void progressReport() {
			 long currentTime = System.currentTimeMillis();
             long elapsed = currentTime - lastReportTime;
             if (elapsed >= TimeUnit.SECONDS.toMillis(PROGRESS_REPORT_INTERVAL)) {
                 synchronized (mutex) {
                     // Check again inside the synchronized block to avoid race condition
                     if (currentTime - lastReportTime >= TimeUnit.SECONDS.toMillis(PROGRESS_REPORT_INTERVAL)) {
                    	 enrichment = DB.getEnrichmentDAO().getById( enrichment.getDbID(), false );
                         enrichment.updateStatus("ANALYSING", "Processed " + urlCount.get() + " urls in " + ((currentTime - startTime)/1000) + " seconds." );
                         log.debug( "Processed " + urlCount.get() + " urls in " + ((currentTime - startTime)/1000) + " seconds." );
                         lastReportTime = currentTime;
                     }
                 }
             }
		}
		
		private void storeObjectResult( ObjectNode result, RecordUrl url ) {
			if( result == null ) return;
			try {
				ArrayNode objects = (ArrayNode) result.get( "data").get("objects");
				if( objects == null ) return;
				for( JsonNode node: objects ) {
					ObjectNode singleDetection = (ObjectNode) node;
					String confidence = singleDetection.get( "confidence" ).asText();
					String uri = singleDetection.get( "wikidata").get( "wikidata_concepturi" ).asText();
					ObjectResult or = new ObjectResult();
					or.confidence = confidence;
					or.recordId = url.recordId;
					or.wikidataUri = uri;
					or.imageUrl = url.url;
					((CompressedObjectBuffer<ObjectResult>)resultBuffer).write(or);
					resultCount.getAndIncrement();
				}
			} catch( Exception e ) {
				log.error( "", e );
			}				

		}
		
		private void storeColorResult( ObjectNode result, RecordUrl url ) {
			if( result == null ) return;			
			try {
				ObjectNode colors = (ObjectNode) result.get( "data").get("colors");
				if( colors == null ) return;
				
				for( JsonNode node: colors ) {
					ObjectNode singleDetection = (ObjectNode) node;
					String wikiUri = singleDetection.get( "wikidata_uri" ).asText();
					String fashionUri = singleDetection.get( "europeana_uri" ).asText();
					ColorResult cr = new ColorResult();
					cr.recordId = url.recordId;
					cr.imageUrl = url.url;
					cr.wikidataUri = wikiUri;
					cr.fashionUri = fashionUri;
					
					((CompressedObjectBuffer<ColorResult>)resultBuffer).write(cr);
					resultCount.getAndIncrement();
				}
			} catch( Exception e ) {
				log.error( "", e );
			}				
		}
		
		
		// Job scheduler calls this at the end
		/*
		 * Writes the collected annotation in json-ld format put into the enrichment
		 * Afterwards initiates the stats building.
		 */
		@Override
		public void finished() {
			enrichment = DB.getEnrichmentDAO().getById(enrichment.getDbID(), false );
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try {
				AnnotationStream as = new AnnotationStream( bos );
				Annotation a = new Annotation();
				a.target = new Annotation.Target();
				a.target.selector = new Annotation.Selector();
				
				if( analysisType == AnalysisType.COLOR ) {
					a.scope = "dc:format";
					a.softwareCreator()
					.creator.name = "Color tagging. Method '" + requestTemplate.get( "service" ).asText() +"'";
					boolean wiki = requestTemplate.get( "ld_source").asText().equals( "Wikidata" ); 
					
					ColorResult cr;
					while(( cr = (ColorResult) resultBuffer.read()) != null ) {
						String uri = wiki ? cr.wikidataUri : cr.fashionUri;
						if( StringUtils.empty(uri)) continue;

						a.uriBody( uri );
						a.created = (new Date()).toString();
						a.target.source = cr.recordId;
						a.target.selector.destination = new Annotation.Url( cr.imageUrl );
						as.addAnnotation(a);
					}					
				} else {
					a.scope = "dc:subject";
					a.softwareCreator()
					.creator.name = "Object detection. Method '" + requestTemplate.get( "service" ).asText() +"'";
					ObjectResult or;
					while(( or = (ObjectResult) resultBuffer.read()) != null ) {
						if( StringUtils.empty(or.wikidataUri)) continue;

						a.uriBody( or.wikidataUri );
						a.created = (new Date()).toString();
						a.target.source = or.recordId;
						a.target.selector.destination = new Annotation.Url( or.imageUrl );
						a.confidence = or.confidence;
						as.addAnnotation(a);
					}					
				}
				as.close();
			} catch( Exception e ) {
				log.error( "", e );
			}
			enrichment.setRecordCount( resultCount.longValue());
			enrichment.setUncompressedContent(bos.toByteArray());
			enrichment.setFormat( Format.ANNOTATION_JSONLD );
			
			enrichment = DB.getEnrichmentDAO().makePersistent(enrichment);
			
			enrichment.updateStatus("ANALYZE", "Building statistics");
			try {
				AnnotationStats.buildStats(enrichment);
				DB.commit();
				// DB.getEnrichmentDAO().makePersistent(enrichment);				
			} catch( Exception ex ) {
				log.error( "Stats building problem", ex );
				enrichment.updateStatus( "ERROR", "Stats buidling failed");
			} 
		}

		@Override
		public boolean hasMoreJobs() {
			// TODO Auto-generated method stub
			return !noMoreUrls;
		}		
	}

	/*
	 * Gives an Interceptor that produces pairs of record ids and urls from items.
	 */
	public static Interceptor<Item, RecordUrl> edmImages( boolean includeWebResources ) {
		return Interceptor.mapcatInterceptor((item, sink)-> {
			String id = item.getValue("//*[local-name()='ProvidedCHO']/@*[local-name()='about']");
			if( StringUtils.empty(id)) return;
			String shownBy = item.getValue("//*[local-name()='Aggregation']/*[local-name()='isShownBy']/@*[local-name()='resource']");
			log.debug( "Found '" + shownBy );
			
			if( !StringUtils.empty(shownBy)) {
				sink.accept( new RecordUrl(id, shownBy));
			}
			if( !includeWebResources) return;
			
			for( String url: item.getValues( "//*[local-name()='WebResource']/@*[local-name()='about']")) {
				if( !StringUtils.empty( url )) {
					sink.accept( new RecordUrl(id, url));
				}
			}
		});
	}
	
	
	// an exclude filter, exclude anything that matches
	public static Interceptor<RecordUrl, RecordUrl> filterByRegexp( String excludeMatchingRegexp ) {
		try {
			Pattern excludePattern = Pattern.compile( excludeMatchingRegexp );
			return Interceptor.<RecordUrl>filterInterceptor( recordUrl -> {
				if( excludePattern.matcher(recordUrl.url).find()) return false;
				return true;
			});
		} catch( Exception e ) {
			log.error( "Pattern invalid, no filtering", e );
			return Interceptor.emptyInterceptor();
		}
	}

	private static ObjectNode getDefaultParams( AnalysisType type ) {
		if( type == AnalysisType.COLOR ) {
			return Jackson.om().valueToTree(new ColorRequest());
		} else {
			return Jackson.om().valueToTree(new ObjectRequest());
		}
	}
	
	// object node keys in source will go into same keys in target
	// source keys with no target will be ignored
	public static void mergeJson( ObjectNode source, ObjectNode target ) {
		for( Entry<String,JsonNode> e: (Iterable<Entry<String,JsonNode>>) ()->source.fields()) {
			if( !target.has( e.getKey())) continue;

			JsonNode targetValue= target.get( e.getKey());
			// recursive merge 2 object nodes
			if( targetValue instanceof ObjectNode ) {
				if( e.getValue() instanceof ObjectNode ) 
					mergeJson( (ObjectNode) e.getValue(), (ObjectNode) targetValue );
			} else if( targetValue.isValueNode()) {
				if( e.getValue().isValueNode())
					target.set( e.getKey(), e.getValue());
			}
			// array we ignore here 
		}
	}

	
	private static ObjectNode request( String serverPath, ObjectNode parameters ) {
		try {
			Request req = Request.Post( serverEndpointUrl + serverPath )
					.bodyByteArray( Jackson.om().writeValueAsBytes(parameters))
					.addHeader("Content-type", "application/json");
			log.debug( "Request: " + req.toString());
			
			Response response = req.execute();
			HttpResponse httpResponse = response.returnResponse();
			log.debug( "Resp: " + httpResponse.toString());
			
			if( httpResponse.getStatusLine().getStatusCode() == 200 ) {
				byte[] body = IOUtils.toByteArray( httpResponse.getEntity().getContent());
			
				ObjectNode result = (ObjectNode) Jackson.om().readTree(body);
				return result;
			} else {
				// something went wrong, could be not an image url or server not available or or 

				log.error( "Analysis failed on " + parameters.get("source").asText());
				log.error( IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8"));  

			}
			
		} catch( Exception e ) {
			log.error( "", e );
		}
		
		return null;
	}
	
	public static boolean canAnalyse( Dataset ds ) {
		List<XmlSchema> schemas = XmlSchema.getSchemasFromConfig("imageAnalysis.schemas");
		return ds.isAnySchemaAndHasValidItems(schemas);
	}
	
	// params are  enrichmentName optional , excludeRegexp optional, includeWebResources,  advanced ... optional,
	public static void queueJob( User u, Dataset ds, AnalysisType analysisType, ObjectNode parameters) {
		String enrichmentName= ds.getOrigin().getName()+"_"+analysisType.name()+"_analysis";
		if( parameters.has( "enrichmentName")) {
			String newName = parameters.get("enrichmentName").asText();  
			if( !StringUtils.empty(newName)) enrichmentName = newName;
		}
		boolean includeWebResources = parameters.has( "includeWebResources") 
				&& parameters.get( "includeWebResources").asBoolean( false );
		
		Interceptor<Item, RecordUrl> interceptor = edmImages( includeWebResources );
		
		if( parameters.has( "excludeRegexp")) {
			String excludeRegexp = parameters.get( "excludeRegexp").asText();
			if( ! StringUtils.empty( excludeRegexp )) {
				interceptor = interceptor.into( filterByRegexp(excludeRegexp));
			}
		}
		
		Enrichment enrichment = new Enrichment();
		enrichment.setOrganization( ds.getOrganization());
		enrichment.setCreator(u);
		enrichment.setCreationDate( new Date());
		enrichment.setName( enrichmentName );
		enrichment = DB.getEnrichmentDAO().makePersistent(enrichment);
		DB.commit();
		enrichment.updateStatus("QUEUED", "Waiting to be processed");
		
		ObjectNode template = getDefaultParams(analysisType);
		
		if( parameters.path( "advanced").isObject()) {
			mergeJson(  (ObjectNode) parameters.get("advanced"), template );			
		}
		
		DatasetJob job = new DatasetJob( enrichment, ds, template, analysisType, interceptor );
		jobScheduler.addJob(job);
	}
}
