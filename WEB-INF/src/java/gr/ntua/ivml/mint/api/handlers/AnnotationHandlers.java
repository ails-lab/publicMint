package gr.ntua.ivml.mint.api.handlers;

import static gr.ntua.ivml.mint.api.RequestHandlerUtil.check;
import static gr.ntua.ivml.mint.api.RequestHandlerUtil.grab;

import java.io.File;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.api.RequestHandler;
import gr.ntua.ivml.mint.api.RequestHandler.TmpFile;
import gr.ntua.ivml.mint.api.RequestHandlerUtil;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.f.ThrowingConsumer;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic.Access;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Enrichment;
import gr.ntua.ivml.mint.persistent.Enrichment.Format;
import gr.ntua.ivml.mint.persistent.Organization;
import gr.ntua.ivml.mint.persistent.Project;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.services.ImageAnalysis;
import gr.ntua.ivml.mint.services.ImageAnalysis.AnalysisType;
import gr.ntua.ivml.mint.util.Annotation;
import gr.ntua.ivml.mint.util.AnnotationFilter;
import gr.ntua.ivml.mint.util.AnnotationFilter.FilterModifier;
import gr.ntua.ivml.mint.util.AnnotationStats;
import gr.ntua.ivml.mint.util.Config;
import gr.ntua.ivml.mint.util.EdmAnnotation;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.JacksonBuilder;
import gr.ntua.ivml.mint.util.StringUtils;
import gr.ntua.ivml.mint.util.WebToken;

/*
 * All the API calls relevant for enrichment operations
 */
public class AnnotationHandlers {
    
    public static final Logger log = Logger.getLogger( AnnotationHandlers.class );

    public enum EnrichmentType {
		CSV,
		JSON
	}

    /**
	 * /api/enrichment/upload
	 * @param request
	 * @param response
	 *
	 * Upload an enrichment
	 * url params should be name and type
	 * body is the uploaded file
	 * orgId is optional
	 * Request body should have three parts: file, name and enrichmentType: CSV or JSON
	 */
	public static void uploadAnnotation(HttpServletRequest request, HttpServletResponse response)  {

        RequestHandlerUtil.errorWrap(
            (req, res) -> {
                User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");

                Optional<Integer> orgIdOpt = RequestHandlerUtil.getOptInt(request, "orgId");
                int orgId = orgIdOpt.orElse((int) u.getOrganization().getDbID());
                Organization org = RequestHandlerUtil.accessOrganization(orgId, u, Access.WRITE);

                EnrichmentType enrichmentType = grab(()-> EnrichmentType.valueOf( RequestHandlerUtil.getUnique(req, "enrichmentType" )), "Invalid enrichment type, need CSV or JSON");
                String name = RequestHandlerUtil.getUnique(req, "name" );
        
                Enrichment enrichment = new Enrichment();

                try (TmpFile tmp = 	new TmpFile( File.createTempFile("enrich", "."+enrichmentType.name() ))) {
                    FileUtils.copyInputStreamToFile(req.getInputStream(),tmp.file );

                    enrichment.initialize(tmp.file, name, enrichmentType, u);
                }

                enrichment.setOrganization(org);
                enrichment.setProjectIds( Project.sharedIds(u.getProjectIds(), org.getProjectIds()));
                new AnnotationStats().buildStats(enrichment);

                DB.getEnrichmentDAO().makePersistent(enrichment);
                DB.commit();

                // prepare success response
                ObjectMapper om = new ObjectMapper();

                ObjectNode node = om.createObjectNode();

                node.put("enrichmentId", enrichment.getDbID());
                node.put("enrichmentName", enrichment.getName());
                node.put("recordCount", enrichment.getRecordCount());
                node.put("creator", enrichment.getCreator().getDbID());
                node.put("organization", enrichment.getOrganization().getDbID());
                node.set("projectIds", om.valueToTree( enrichment.getProjectIds()));
                node.put("format", enrichment.getFormat().toString());
                node.put( "success", true);

                RequestHandler.okJson(node, response);
        
        }).handle(request, response);
	}

    /**
	 * /api/enrichment/download
	 * @param request
	 * @param response
	 *
	 * Download an enrichment
	 */
	public static void downloadAnnotation(HttpServletRequest request, HttpServletResponse response)  {
        RequestHandlerUtil.errorWrap(
            (req, res) -> {
                User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
                int enrichmentId = grab(() -> RequestHandler.getPathInt(request, 3), "No enrichmentId in path");
                Enrichment enrichment = grab(() -> RequestHandlerUtil.accessEnrichment(enrichmentId, u, AccessAuthenticationLogic.Access.READ), "Not enough rights");                
                
                ThrowingConsumer<OutputStream> downloadProducer = (OutputStream os) -> {
                    try {
                        if (enrichment.getFormat().equals(Enrichment.Format.CSV_GZ)) 
                            enrichment.outputCsvToStream(os);
                        else 
                            os.write(enrichment.getContent());
                    }
                    catch (Exception e) {
                        log.error("", e);
                    }
                    finally {
                        os.close();
                    }
                };

                String fileExtension = enrichment.getFormat().equals(Format.CSV_GZ) ? ".csv" : ".json";
                boolean compressOutput =  enrichment.getFormat().equals(Format.CSV_GZ);
                RequestHandlerUtil.download("application/gzip", "Enrichment_"+enrichment.getDbID()+fileExtension+".gz", downloadProducer, compressOutput, response);
            }
        ).handle(request, response);
    }

	
	// optional orgId, url, optional name 
	public static void uploadAnnationViaLink(HttpServletRequest request, HttpServletResponse response)  {
        RequestHandlerUtil.errorWrap(
                (req, res) -> {
                    User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
                    Optional<Integer> orgIdOpt = RequestHandlerUtil.getOptInt(request, "orgId");
                    int orgId = orgIdOpt.orElse((int) u.getOrganization().getDbID());
                    Organization org = RequestHandlerUtil.accessOrganization(orgId, u, Access.WRITE);

                    Optional<String> name = RequestHandlerUtil.getOptUnique(request, "name");
                    String url = RequestHandlerUtil.getUnique(request, "url");
                    // create an enrichment with status json ... if the status is error, it will removed after some time
                    Enrichment enrichment = new Enrichment();
                    name.ifPresent( s->enrichment.setName(s));
                    enrichment.setOrganization(org);
                    enrichment.setCreator(u);
                    // will be replace with the stats when finished
                    enrichment.setStats(
                    		JacksonBuilder.obj()
                    			.put( "status", "QUEUED")
                    			.put( "date", (new Date()).toString())
                    			.put( "msg", "Importing '" + url )
                    			.getObj());
                    
                    DB.getEnrichmentDAO().makePersistent(enrichment);
                    DB.commit();
                    Annotation.loadAnnotationLdFromUrl( enrichment, url );
                    RequestHandler.ok( res );
                }
        ).handle(request, response);
	}
	
	
	
	
	
	public static void listAnnotation(HttpServletRequest request, HttpServletResponse response)  {
        RequestHandlerUtil.errorWrap(
            (req, res) -> {
                User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
                // request for an org?
                Optional<Integer> orgIdOpt = RequestHandlerUtil.getOptInt(request, "orgId");
                Organization filterOrg = u.getOrganization();
                if( orgIdOpt.isPresent()) 
                	filterOrg = RequestHandlerUtil.accessOrganization(orgIdOpt.get(), u, Access.READ);                	
                int start = RequestHandlerUtil.getOptInt(req, "start").orElse(0);
                int count = RequestHandlerUtil.getOptInt(req, "count").orElse(10);
                List<Enrichment> enrichments = DB.getEnrichmentDAO().pageByOrganization(filterOrg, start, count);
                ArrayNode result = Jackson.om().createArrayNode();
                for( Enrichment e: enrichments ) {
                	List<String> projectNames = Project.toNames(e.getProjectIds());
                	String downloadToken = downloadToken( u, e.getDbID().intValue());
                	JacksonBuilder jb = 
                	JacksonBuilder.obj()
                		.put( "dbID", e.getDbID())
                		.put( "creationDate", e.getCreationDate().toString())
                		.put( "prettyTime", StringUtils.prettyTime(e.getCreationDate()))
                		.put( "name", e.getName());

                	if( e.getStats() != null ) {
                		if( e.getStats().has( "status") ) {
                			jb.put( "status", e.getStats().get( "status" ));
                			jb.put( "msg", e.getStats().get( "msg"));
                			jb.put( "lastUpdate", e.getStats().get( "date"));
                		} else {
                    		jb
                    		.put( "recordCount", e.getRecordCount())
                    		.put( "format", e.getFormat().name())
                    		.put( "share", "api/callByToken?token=" + URLEncoder.encode( downloadToken,"UTF-8"));
                    		                			
                		}
                		if( e.getStats().has( "stats")) {
                			jb.put( "stats", e.getStats().get( "stats" ));
                		}
                	}
                		
                	
                	for( String projectName: projectNames ) 
                		jb.withArray("projects").append( projectName );
                	
                	result.add( jb.getObj());
                }
                RequestHandler.okJson(result, res);
            }
        ).handle(request, response);
	}
	
	// if the body contains a valid filter json, apply this before returning values
	public static void listAnnotationValues(HttpServletRequest request, HttpServletResponse response)  {
        RequestHandlerUtil.errorWrap(
            (req, res) -> {

            	User u = grab(()-> (User) req.getAttribute("user"), "No User logged in");
                int start = RequestHandlerUtil.getOptInt( req, "start").orElse( 0 );
                int count = RequestHandlerUtil.getOptInt( req, "count").orElse( 10 );
                int enrichmentId = RequestHandlerUtil.getInt( req, "enrichmentId");
                Optional<FilterModifier> optFilter = RequestHandlerUtil.getOptJsonBody(req)
                		.filter( node -> node != null )
                		.filter( node -> node instanceof ObjectNode )
                		.flatMap( node -> Optional.ofNullable( AnnotationFilter.buildFromJson( (ObjectNode) node )));
                
                Enrichment e = RequestHandlerUtil.accessEnrichment(enrichmentId, u, Access.READ);
                if( optFilter.isPresent())
                    RequestHandler.okJson( e.getValuesAsFlatJson(start, count, optFilter.get()), res );
                else 	
                	RequestHandler.okJson( e.getValuesAsFlatJson(start, count), res );

            } ).handle(request, response);
	}

	
	// a week valid, shared link
	private static String downloadToken( User u, int enrichmentId ) {
		return WebToken.Content.create()
		.expires( Duration.ofDays(7))
		.user( u.getLogin())
		.url( "/api/annotation/download/"+enrichmentId )
		.keepParameters( true )
		.encrypt(Config.get( "secret"));
	}
	
	public static void deleteAnnotation(HttpServletRequest request, HttpServletResponse response)  {
        RequestHandlerUtil.errorWrap(
            (req, res) -> {
                User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
                // DELETE /api/annotation/23
                int enrichmentId = RequestHandlerUtil.getPathInt(req, 2);
                
                Enrichment e = RequestHandlerUtil.accessEnrichment(enrichmentId, u, Access.PROJECT);
                if( !DB.getEnrichmentDAO().makeTransient(e)) {
                	log.info( "Delete of enrichment #"+enrichmentId+ " failed");
                	RequestHandler.err( res);
                } else {
                	RequestHandler.ok( res );
                };
                
            }
        ).handle(request, response);
    }
	
	public static void editAnnotation(HttpServletRequest request, HttpServletResponse response)  {
        RequestHandlerUtil.errorWrap(
            (req, res) -> {
                User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
                // json body is for the update
                ObjectNode payload = (ObjectNode) grab(()-> RequestHandlerUtil.getJson(req, Optional.empty() ), "No json body");
                // expect enrichmentId and name
                int enrichmentId =  grab( ()-> payload.get("enrichmentId"), "No enrichmentId in json" ).asInt();
                Enrichment e = RequestHandlerUtil.accessEnrichment(enrichmentId, u, Access.WRITE);

                Optional<String> newName = Optional.ofNullable( payload.get( "name")).map( n->n.asText());
                
                // project users cannot modify project access
                if( AccessAuthenticationLogic.getAccess(e, u)!= Access.PROJECT) {
	                Optional<ArrayNode> projectIds =  
	                		Optional.ofNullable( (ArrayNode) payload.get( "projects"))
	                		.map( names -> {
	                			final ArrayNode ids = Jackson.om().createArrayNode();
	                			for( JsonNode nameNode: names ) {
	                				Project.toId( nameNode.asText()).ifPresent(
	                						i -> ids.add( i )
	                				);
	                			}
	                			return ids;
	                		});
	                if( !projectIds.isPresent() ) {
	                	projectIds = Optional.ofNullable( (ArrayNode) payload.get( "projectIds"));
	                }
	                
	                if( projectIds.isPresent()) {
	                	// only org projects can be set
	                	List<Integer> allowedProjectIds = e.getOrganization().getProjectIds();
	                	List<Integer> newProjectIds = new ArrayList<>();
	                	for( JsonNode node: projectIds.get() ) {
	                		if( allowedProjectIds.contains( node.asInt()))
	                			newProjectIds.add( node.asInt());
	                	}
	                	e.setProjectIds(newProjectIds);
	                }
                }
                 
                newName.ifPresent( n->e.setName(n));
                DB.getEnrichmentDAO().makePersistent(e);
                RequestHandler.ok( res );
            }
        ).handle(request, response);
    }	
	
	
	// with datasetId will submit the enrich job, without its just counting the passed annotations
	public static void applyAnnotation(HttpServletRequest request, HttpServletResponse response)  {
        RequestHandlerUtil.errorWrap(
            (req, res) -> {
            	// check the parameters section
            	
                User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
                // json body is filters
                ObjectNode payload = (ObjectNode) grab(()-> RequestHandlerUtil.getJson(req, Optional.empty() ), "No json body");
                // expect enrichmentId and name
                // enrichment and dataset should be url params
                int enrichmentId = RequestHandlerUtil.getInt( req, "enrichmentId");
                Enrichment e = RequestHandlerUtil.accessEnrichment(enrichmentId, u, Access.READ);

                AnnotationFilter.FilterModifier fm  = AnnotationFilter.buildFromJson(payload);
                check( ()->fm!=null, "Invalid filters");
                
                Optional<Integer> datasetIdOpt = RequestHandlerUtil.getOptInt(request, "datasetId");
                Optional<Dataset> datasetOpt = datasetIdOpt.map( id->RequestHandlerUtil.accessDataset(id, u, Access.WRITE));
                
                datasetOpt.ifPresent( ds -> check(()->e.canApply(ds), "Cannot enrich Dataset" ));
                
                // if we are here without ParameterException, execution will probably work
                
                if( datasetOpt.isPresent()) {

                	// if it throws, the outside catches it, but gives funny messages
            		EdmAnnotation.enrich(datasetOpt.get(), e, payload);
                		
                	RequestHandler.ok( res );
                	
                } else {
                	// testing
                	HashMap<String,Integer> counters = new HashMap<>();
                	e.streamFlatJson().map( node -> fm.apply(node)).forEach( node -> {
                		if( node == null )
                			counters.merge( "rejected", 1, Integer::sum);
                		else 
                			counters.merge( "accepted", 1, Integer::sum);
                	});
                    RequestHandler.okJson(Jackson.om().valueToTree(counters), res);
                }
            }
        ).handle(request, response);
	}

	public static void queueImageAnalysis(HttpServletRequest request, HttpServletResponse response)  {
        RequestHandlerUtil.errorWrap(
            (req, res) -> {
            	// check the parameters section
            	grab( ()->Config.get( "imageAnalysis.server" ), "Image Analysis server not configured" );
            	
                User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
                // json body is filters
                JsonNode payload = grab(()-> RequestHandlerUtil.getJson(req, Optional.empty() ), "No json body");

                // dataset in URL
                int datasetId = RequestHandlerUtil.getInt( req, "datasetId");
                Dataset ds = RequestHandlerUtil.accessDataset( datasetId, u, Access.READ);
                
                ObjectNode params = grab( ()->(ObjectNode) payload, "Not a json object body");
                ImageAnalysis.AnalysisType analysisType = grab( ()-> AnalysisType.valueOf( params.path("analysisType").asText()), "No analysisType");
                // everything else is optional
                ImageAnalysis.queueJob( u, ds, analysisType, params );
            }
        ).handle(request, response);
	}

     
}

