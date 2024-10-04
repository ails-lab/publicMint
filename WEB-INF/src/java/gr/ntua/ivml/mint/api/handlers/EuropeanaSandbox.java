package gr.ntua.ivml.mint.api.handlers;

import static gr.ntua.ivml.mint.api.RequestHandlerUtil.accessDataset;
import static gr.ntua.ivml.mint.api.RequestHandlerUtil.errorWrap;
import static gr.ntua.ivml.mint.api.RequestHandlerUtil.getInt;
import static gr.ntua.ivml.mint.api.RequestHandlerUtil.grab;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import gr.ntua.ivml.mint.api.DatasetOptions.Option;
import gr.ntua.ivml.mint.api.RequestHandler;
import gr.ntua.ivml.mint.api.RequestHandlerUtil;
import gr.ntua.ivml.mint.concurrent.Queues;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.db.Meta;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic.Access;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Item;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.persistent.XmlSchema;
import gr.ntua.ivml.mint.util.ApplyI;
import gr.ntua.ivml.mint.util.Config;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.StringUtils;

public class EuropeanaSandbox {
	
	// set this to the dataset id while the upload is running to stop double submits
	private static Set<Integer> uploadRunning = ConcurrentHashMap.newKeySet();
	private static Optional<HttpHost> proxy = null;
	
	public static final Logger log = Logger.getLogger( EuropeanaSandbox.class);
	
	
    public static void addSandboxOptions( ArrayNode result, Dataset ds, Access access ) {
		if( access.isWorseThan(Access.READ)) return;
		
    	List<XmlSchema> supportedSchemas = XmlSchema.getSchemasFromConfig("sandbox.schemas");
    	if( supportedSchemas.isEmpty()) return;
    	// is this publishable on sandbox
    	if( !ds.isAnySchemaAndHasValidItems(supportedSchemas)) return;
    	
    	// check if its running
    	Date startDate = Meta.getTimestamp( ds, "sandbox.running");
    	String sandboxId = Meta.get( ds, "sandbox.id" );
    	
    	Option o = null;
    	//expired?
    	if( startDate != null )
    		if( System.currentTimeMillis() - startDate.getTime() > 1000*86400*14 ) {
    			sandboxId= null;
    			startDate = null;
    			Meta.delete(ds, "sandbox.id");
    			Meta.delete( ds, "sandbox.running");
    		}

    	if(( sandboxId == null ) && ( startDate == null )) 
    		o = new Option("Upload to Sandbox","html/sandbox_options.html?datasetId="+ds.getDbID(), "Sandbox", "panel", "core");
    	else if( sandboxId == null ) {
    		o = new Option("Sandbox loading ..",null, "Sandbox", "json", "core");
    		o.inProgress = true;
    	}
    	else {       	
        	// show the external link
    		String sandboxUi = Config.get( "sandbox.ui");
    		o = new Option( "View in Sandbox", sandboxUi+"/dataset/"+sandboxId, "Sandbox", "external", "core");
    	}
    	
    	if( o != null ) 
    		result.add( Jackson.om().valueToTree( o ));
		return;    	
    }
    
    
    public static void createSandboxEntry( HttpServletRequest request, HttpServletResponse response ) {

    	errorWrap( 
			(req, resp) -> {
				// take the first 1k records if its edm and create a tar.gz temp file. Upload to sandbox
				final int datasetId = getInt( request, "datasetId");
				User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
				
				Dataset ds = accessDataset( datasetId, u, Access.READ );

				String pickMethod = RequestHandlerUtil.getOptUnique(request, "pickMethod").orElse("default");
				int size = RequestHandlerUtil.getOptInt(request, "size")
						.filter( i -> (i>0)&&(i<1000))
						.orElse( 1000 );
				int skip = RequestHandlerUtil.getOptInt(request, "skip").orElse(0);
				if( pickMethod.equals( "default")) skip = 0;
				
				boolean random = pickMethod.equals( "random");
				boolean spread = pickMethod.equals( "spread");
				
		    	Date startDate = Meta.getTimestamp( ds, "sandbox.running");
		    	String sandboxId = Meta.get( ds, "sandbox.id" );

		    	if( startDate != null )
		    		if( System.currentTimeMillis() - startDate.getTime() > 1000*86400*14 ) {
		    			sandboxId= null;
		    			startDate = null;
		    			Meta.delete(ds, "sandbox.id");
		    			Meta.delete( ds, "sandbox.running");
		    		}

		    	// if we have an id here, we dont need to create an entry
		    	if( !StringUtils.empty(sandboxId)) {
					RequestHandler.ok(resp);
					return;
		    	}
		    	
		    	if( ! uploadRunning.add( datasetId )) {
		    		log.info( "Repeated request, upload already in progress.");
		    		return;
		    	}

				// we might get other params later, for now
				uploadDatasetToSandbox( ds, size, skip, random, spread );
				
				RequestHandler.ok(resp);
			} ).handle(request, response);
    }
    
    
    public static void uploadDatasetToSandbox( Dataset ds, int size, int skip, boolean random, boolean spread ) {
    	DB.SessionRunnable r = ()->{
        	try {
        		Meta.putTimestamp( ds, "sandbox.running");
        		List<Item> l  = Collections.emptyList();
        		if( spread ) l = getSkipped( ds, size, -1 );
        		else if( random) l= getRandomItems( ds,size );
        		else l =getSkipped( ds, size, skip );
        		
        		if( l.isEmpty()) return;
        		File f = File.createTempFile("sandboxUpload", ".tar.gz" );
        		try ( RequestHandler.TmpFile tf = new RequestHandler.TmpFile(f)) {
        			FileOutputStream fos = new FileOutputStream( f );
        			Item.exportItemsTarball( fos, l.iterator() );
        			fos.close();
        			
        			MultipartEntityBuilder builder = MultipartEntityBuilder.create();


        	        // Add a binary part
        	        builder.addBinaryBody("dataset", f, ContentType.create("application/x-gzip"), f.getName());

        	        // Build the HttpEntity
        	        HttpEntity multipart = builder.build();

        	        // Send the multipart POST request
        	        Request req =  Request.Post(
    	        			new URIBuilder( Config.get( "sandbox.api"))
    	        			.setPathSegments("dataset","Mint_"+ds.getDbID(),"harvestByFile")
    	        			.addParameter("country", "Europe")
    	        			.addParameter( "language", "English")
    	        			.toString())        	        		        	        		
    	        		.body(multipart);
        	        getProxy().ifPresent( h-> req.viaProxy(h));

        	        HttpResponse rsp = req
        	        		.execute()
        	        		.returnResponse();
        	        
        	        if( ! (""+rsp.getStatusLine(). getStatusCode()).startsWith("2")  ) {
        	        	// something went badly
        	        	Meta.delete( ds, "sandbox.running");
        	        	log.error( "Error response from sandbox " + rsp.getStatusLine().getReasonPhrase());
        	        	return;
        	        }
        	        JsonNode node = Jackson.om().readTree(rsp.getEntity().getContent());
        	        
        	        String sandboxId = node.get( "dataset-id").asText();
        	        if( StringUtils.empty(sandboxId)) {
        	        	log.error( "Unexpected returned json\n"+node.toString());
        	        	Meta.delete( ds, "sandbox.running");
        	        	return;
        	        }
        	        Meta.put( ds, "sandbox.id",sandboxId );
        		} catch( Exception e ) {
        			log.error( "Upload Sandbox problem", e );
        			throw e;
        		}
        		
        	} catch( Exception e ) {
        		log.error( "Sandbox Upload failed for ds #"+ds.getDbID(), e );
        		Meta.delete( ds, "sandbox.running");
        	} finally {
        		uploadRunning.remove(ds.getDbID().intValue() );
        	}
    	};
    	
    	Queues.queue(r, "single");
    	
    }
    
    public static List<Item> getRandomItems( Dataset ds, int size ) {
    	List<Item> result = new ArrayList<>();
    	Random r = new Random();
    	try {
	    	ds.processAllValidItems( new ApplyI<Item>() {
	    		int leftToPick = size;
	    		int itemCountLeft = ds.getValidItemCount();
	    		
	    		public void apply( Item item ) {
	    			if( itemCountLeft==0) return; //safety, shouldnt happen
	    			if (r.nextDouble() < ((double)leftToPick / itemCountLeft )) { 
	    					result.add(item);
	    					leftToPick -= 1;
	    			}
	    			itemCountLeft -= 1;
	    		}
	    	}
	    		
	    	, false);
    	} catch( Exception e ) {
    		log.error( "error in item picking");
    	}
    	return result;
    }
    
    // for skip <0 distribute evenly
    public static List<Item> getSkipped( Dataset ds, int size, int skip ) {
    	List<Item> result = new ArrayList<>();
    	int validCount = ds.getValidItemCount();
    	if( validCount <= size ) skip =0;
    	else if( skip < 0 ) {
    		skip = (ds.getValidItemCount() - size ) / size ;
    	}
    	
    	// shortcut
    	if (skip == 0 ) return DB.getItemDAO().getItemsByDataset(ds, 0, size );
    	
    	final int skipItems = skip;
    	
    	try {
	    	ds.processAllValidItems( new ApplyI<Item>() {
	    		int skipped = 0;
	    		
	    		public void apply( Item item ) {
	    			if( skipped == skipItems ) {
    					result.add(item);
    					skipped = 0;
	    			} else 
	    				skipped += 1;
	    		}
	    	}
	    		
	    	, false);
    	} catch( Exception e ) {
    		log.error( "error in item picking");
    	}
    	return result;    	
    }
    
    
    // get Items that represent the give fields value spread
    // this works only for fiels with at most one value per item!
    // TODO!
    public static List<Item> getSpread( Dataset ds, String field, HashMap<String,Integer> valueCounts, int size ) {
    	List<Item> result = new ArrayList<>();
    	if( valueCounts.size() >= size ) {
    		// take the most frequent ones or random
    	}
    	int allCount = valueCounts.entrySet().stream().mapToInt(e->e.getValue()).sum();
    	// put missing in null key
    	int nullKeyCount = ds.getItemCount()-allCount;
    	valueCounts.merge(null, nullKeyCount, (old, update)-> old+update);
    	
    	
    	// find target counts for each value by scaling down to "size"
    	HashMap<String, Integer> targetCounts = new HashMap<>();
    	
    	
    	Random r = new Random();
    	try {
	    	ds.processAllValidItems( new ApplyI<Item>() {
	    		int leftToPick = size;
	    		int itemCountLeft = ds.getValidItemCount();
	    		
	    		public void apply( Item item ) {
	    			if (r.nextDouble() < ((double)leftToPick / itemCountLeft )) { 
	    					result.add(item);
	    					leftToPick -= 1;
	    			}
	    			itemCountLeft -= 1;
	    		}
	    	}
	    		
	    	, false);
    	} catch( Exception e ) {
    		log.error( "error in item picking");
    	}
    	return result;
    }
    
    private static Optional<HttpHost> getProxy() {
    	if( proxy == null ) {
    		String host = System.getProperty( "http.proxyHost");
    		String port = System.getProperty( "http.proxyPort");
    		
    		if( host == null ) {
    			proxy = Optional.empty();
    		} else {
    			HttpHost hh = HttpHost.create( StringUtils.join(host, ":", port));
    			proxy = Optional.of( hh );
    		}
    	}
    	return proxy;
    }

}
