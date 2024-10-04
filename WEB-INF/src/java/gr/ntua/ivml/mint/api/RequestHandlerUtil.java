package gr.ntua.ivml.mint.api;

import static gr.ntua.ivml.mint.api.RequestHandler.errJson;
import static gr.ntua.ivml.mint.api.RequestHandler.getPathInt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gr.ntua.ivml.mint.api.RouterServlet.Handler;
import gr.ntua.ivml.mint.api.RouterServlet.ThrowingHandler;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.f.ErrorCondition;
import gr.ntua.ivml.mint.f.ThrowingConsumer;
import gr.ntua.ivml.mint.f.ThrowingSupplier;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Enrichment;
import gr.ntua.ivml.mint.persistent.Organization;
import gr.ntua.ivml.mint.persistent.Project;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.persistent.XpathHolder;
import gr.ntua.ivml.mint.persistent.XpathStatsValues.ValueStat;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.StringUtils;
 
/**
 * Static methods for use in Request processing operation
 * @author arne
 *
 */
public class RequestHandlerUtil {

	public static Logger log = Logger.getLogger(RequestHandlerUtil.class);

	// execute the supply method and throw if it returns null or throws itself
	public static <T> T grab( ThrowingSupplier<T> supply, String failureMsg ) throws ParameterException {
		try {
			T tmp = supply.get();
			if( tmp != null ) 
				return tmp;
		} catch( ParameterException pe) {
			// dont extra wrap parameterexception
			throw pe;
		} catch( Throwable t ) {
			throw new ParameterException( failureMsg, t );
		}
		throw new ParameterException( failureMsg );
	}
	
	// execute the supply method and throw if it returns null or throws itself
	public static void check( ThrowingSupplier<Boolean> tester, String failureMsg ) throws ParameterException {
		try {
			if( tester.get()) return;
		} catch( ParameterException pe) {
			throw pe;
		} catch( Throwable t ) {
			throw new ParameterException( failureMsg, t );
		}
		throw new ParameterException( failureMsg );
	}
	
	// standard json response for throwing handlers wrapped around the handler
	public static Handler errorWrap( ThrowingHandler inputHandler ) {
		return (request, response ) -> {
			try {
				inputHandler.handle(request, response);
			} catch( ParameterException pe ) {
				errJson( pe.getMessage(), response );
				if( pe.getCause() != null ) {
					log.error( "Exception in parameter processing ", pe.getCause());
				}
				return;
			} catch( Throwable th ) {
				errJson( StringUtils.filteredStackTrace(th, "gr.ntua.ivml.mint").toString(), response );
				log.error( "Exception in Handler", th );
			}
		};
	}
	
	// some HttpRequest convenience
	public static int getInt( HttpServletRequest req, String key ) throws ParameterException {
		try {
			return Integer.parseInt( getUnique( req, key ));
		} catch( NumberFormatException e ) {
			throw new ParameterException( key + " is not an int.");
		}
	}
	
	public static Optional<Integer> getOptInt(  HttpServletRequest req, String key ) throws ParameterException {
		try {
			return getOptUnique( req, key ).map( s -> Integer.parseInt(s));
		} catch( NumberFormatException e ) {
			throw new ParameterException( key + " is not an int.");
		}
	}
	
	public static String getUnique(  HttpServletRequest req, String key ) throws ParameterException {
		String[] vals = req.getParameterValues(key);
		if( vals == null) throw new ParameterException( key + " is missing");
		if( vals.length > 1 ) throw new ParameterException( "Mulitple values for " + key + " not allowed");
		return vals[0];
	}
	
	public static Optional<String> getOptUnique(  HttpServletRequest req, String key ) throws ParameterException {
		String[] vals = req.getParameterValues(key);
		if( vals == null) return Optional.empty();
		if( vals.length > 1 ) throw new ParameterException( "Mulitple values for " + key + " not allowed");
		return Optional.of( vals[0] );
	}
	
	// if key given, check that param, else check body
	public static JsonNode getJson( HttpServletRequest req, Optional<String> key ) throws ParameterException {
		ObjectMapper om = Jackson.om();
		try {
			if( ! key.isPresent() ) {
				// not a parameter,check body
				JsonNode jn = om.readTree( req.getInputStream());
				return jn;				
			}

			try {
				return om.readTree( getUnique( req, key.get()));
			} catch( Exception e ) {
				throw new ParameterException( "Error reading Json for " + key.get(), e);
			}
			
		} catch( ParameterException pe ) {
			throw pe;
		} catch( Exception e ) {
			log.error( "Invalid json" ,e );
			throw new ParameterException( "Invalid json parameter " );
		}
	}
	
	public static Optional<JsonNode> getOptJsonBody( HttpServletRequest req ) {
		ObjectMapper om = Jackson.om();
		try {
			return Optional.ofNullable( om.readTree( req.getInputStream()));
		} catch( Exception e ) {
			log.info( "No or invalid json body");
			return Optional.empty();
		}
	}
	
	public static <T> T getJson( HttpServletRequest req, Class<T> clazz ) throws ParameterException {
		try {
			JsonNode jsonTree = getJson( req, Optional.empty());
			return Jackson.om().treeToValue(jsonTree, clazz);
		} catch( ParameterException p ) {
			throw p;
		} catch( Throwable t ) {
			throw new ParameterException( "Invalid Json Body", t);
		}
	}
	
	// get the dataset with id in path
	// Problems will be in ParameterException (Login, exists, rights, malformed url )
	public static Dataset accessDataset( HttpServletRequest request, HttpServletResponse response, String project, int pathPositionOfDatasetNumber ) throws ParameterException {
		
		// err does nothing once it has an error condition. grabs return null
		Integer datasetId = grab(()->getPathInt( request, pathPositionOfDatasetNumber ), "Invalid dataset id."); 
		Dataset ds = grab( ()->DB.getDatasetDAO().getById((long) datasetId, false ), "Unknown Dataset" ); 
		User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");

		Project p = grab(()-> DB.getProjectDAO().findByName( project ), "'" + project + "' project not present in db" );
		if( ! p.hasProject(ds.getOrigin()))
			throw new ParameterException( "Dataset not in " + project );
		
		if( !(u.can( "change data", ds.getOrganization() ) || p.hasProject(u)))
			throw new ParameterException( "User has no access");

		return ds;
	}
	
	public static Dataset accessDataset( int datasetId, User u, AccessAuthenticationLogic.Access accessNeeded ) throws ParameterException {
		
		Dataset ds = grab( ()->DB.getDatasetDAO().getById((long) datasetId, false ), "Unknown Dataset" ); 
		if( AccessAuthenticationLogic.getAccess( ds, u ).ordinal() < accessNeeded.ordinal() ) 
			throw new ParameterException( "Not enough rights");
		return ds;
	}
	
	public static Organization accessOrganization( int organizationId, User u, AccessAuthenticationLogic.Access accessNeeded ) throws ParameterException {
		Organization org = grab( ()->DB.getOrganizationDAO().getById((long) organizationId, false ), "Unknown Organization" ); 
		return accessOrganization(org, u, accessNeeded);
	}
	
	public static Organization accessOrganization( Organization org, User u, AccessAuthenticationLogic.Access accessNeeded ) throws ParameterException {
		if( AccessAuthenticationLogic.getAccess( org, u ).ordinal() < accessNeeded.ordinal() ) 
			throw new ParameterException( "Not enough rights");
		return org;
	}
	
	public static Enrichment accessEnrichment( int enrichmentId, User u, AccessAuthenticationLogic.Access accessNeeded ) throws ParameterException {
		Enrichment enrichment = grab( ()->DB.getEnrichmentDAO().getById((long) enrichmentId, false ), "Unknown Enrichment" ); 
		return accessEnrichment(enrichment, u, accessNeeded);
	}

	public static Enrichment accessEnrichment( Enrichment enrichment, User u, AccessAuthenticationLogic.Access accessNeeded ) throws ParameterException {
		if( AccessAuthenticationLogic.getAccess( enrichment, u ).ordinal() < accessNeeded.ordinal() ) 
			throw new ParameterException( "Not enough rights");
		return enrichment;
	}
	
	
	public static Dataset accessDataset( HttpServletRequest request, int pathPositionOfDatasetNumber, AccessAuthenticationLogic.Access accessNeeded ) 
			throws ParameterException {
		User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
		Integer datasetId = grab(()->getPathInt( request, pathPositionOfDatasetNumber ), "Invalid dataset id."); 
		return accessDataset( datasetId, u, accessNeeded );
	}


	// get the dataset in position 3 of request path,
	// if dataset is null, response already contains errcode and mesg
	public static Dataset accessDataset( HttpServletRequest request, HttpServletResponse response, String project,  ErrorCondition err ) throws Exception {
		
		// err does nothing once it has an error condition. grabs return null
		Integer datasetId = err.grab(()->getPathInt( request, 3 ), "Invalid dataset id."); 
		Dataset ds = err.grab( ()->DB.getDatasetDAO().getById((long) datasetId, false ), "Unknown Dataset" ); 
		User u = err.grab(()-> (User) request.getAttribute("user"), "No User logged in");

		Project p = err.grab(()-> DB.getProjectDAO().findByName( project ), "'" + project + "' project not present in db" );
		err.check(() -> p.hasProject(ds.getOrigin()), "Dataset not in " + project );
		
		err.check( ()-> u.can( "change data", ds.getOrganization() ) || p.hasProject(u), "User has no access");

		return err.grab(()->ds,"");
	}
		
	
	
	/**
	 * Copy output stream into tmp file and then copy to response with given content type and filename.
	 * Optionally compress the stream. Data producer needs to close the stream!
	 * @param contentType
	 * @param fileName
	 * @param dataProducer
	 * @param compressOutput
	 * @param response
	 */
	public static void download( String contentType, String fileName, 
			ThrowingConsumer<OutputStream> dataProducer, boolean compressOutput,
			HttpServletResponse response ) {
		try {
			File tmpFile = File.createTempFile( "forDownload","" );
			FileOutputStream fos = new FileOutputStream( tmpFile );

			FilterOutputStream myos = new FilterOutputStream( fos ) {
				public void close() throws IOException {
					out.flush();
					out.close();
					// copy the file to client
					if( tmpFile.length() > 0 ) {
						response.setStatus(HttpServletResponse.SC_OK);
						response.setContentType( contentType );
						response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
						
						// copy the file to the response
						if( compressOutput ) {
							OutputStream os = new org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream( response.getOutputStream());
							IOUtils.copy( new FileInputStream( tmpFile ), os);
							os.flush();
							os.close();
						} else {
							IOUtils.copy( new FileInputStream( tmpFile ), response.getOutputStream());
							response.getOutputStream().flush();
							response.getOutputStream().close();
						}
						// still no error
						tmpFile.delete();
					} else {
						errJson( "Output problem", response );
					}
				}
			};
			
			dataProducer.accept(myos);
		} catch( Exception e ) {
			log.error( "Download data problem", e );
			errJson( "Problem downloading data", response );
		}
	}
	
	// STream output stream into resonse with given fileanme and content type
	public static void streamDownload( String contentType, String fileName, 
			ThrowingConsumer<OutputStream> dataProducer, boolean compressOutput,
			HttpServletResponse response ) {
		try( OutputStream os = response.getOutputStream()) {
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType( contentType );
			response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

			if( compressOutput ) {
				OutputStream gzos = new org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream( response.getOutputStream());
				dataProducer.accept( gzos );
				gzos.flush();
				gzos.close();
			} else {
				dataProducer.accept(response.getOutputStream());
				response.getOutputStream().flush();
				response.getOutputStream().close();
			}

		} catch( Exception e ) {
			log.error( "", e );
		}
 	}

	// download given holders values in csv format
	public static void downloadValues( XpathHolder holder, HttpServletResponse response ) {
		String name = holder
				.getFullPath()
				.replaceAll("/text()", "")
				.replaceAll( "[^0-9a-zA-Z]", "_")
				// remove trailing underscores
				.replaceAll( "_*$","");
		
		if( name.length()>20) name = name.substring( name.length()-20);
		final XpathHolder holderFinal = (holder.getTextNode() != null)?holder.getTextNode():holder;

		String fileName = "dataset_#"+holder.getDataset().getDbID()+ "_"+name+".csv.gz";
		ThrowingConsumer<OutputStream> csvSupply = outputStream -> {
			int start =0;
			while( true ) {
				List<ValueStat> values = holderFinal.getValues(start, 100);
				Writer w = new OutputStreamWriter( outputStream, "UTF-8");
				if( start==0 && values.size()>0 ) {
					w.write( CSVFormat.DEFAULT.format( "Value", "Count") + "\n" );
				}
				for( ValueStat v: values ) {
					w.write( CSVFormat.DEFAULT.format(v.value, v.count ) +"\n");
				}
				log.debug( "Start = " + start + "ValueSize: " + values.size());
				start += 100;
				w.flush();
				if( values.size() == 0 ) {
					w.close();
					break;
				}
			}
		};
		download( "application/gzip", fileName, csvSupply, true, response );
	}

	// returns value if unique otherwise empty
	public static Optional<String> getUniqueParameter( HttpServletRequest request, String name) {
		String[] values = request.getParameterValues(name);
		if( values == null ) return Optional.empty();
		if( values.length > 1 ) return Optional.empty();
		return Optional.of( values[0]);
	}
	
	public static Optional<Long> getUniqueNumberParameter(  HttpServletRequest request, String name) {
		try {
			return getUniqueParameter(request, name).map( s-> (long) Integer.parseInt(s));
		} catch( NumberFormatException e ) {
			throw new ParameterException( name + " is not a number.");
		}
	}
	
	public static int getPathInt( HttpServletRequest request, int pos ) throws ParameterException {
		String[] query = request.getPathInfo().split( "/");
		try {
			return Integer.parseInt(query[pos]);
		} catch (NumberFormatException n ) {
			throw new ParameterException( "Path Position " + pos + " is not a number [" + query[pos] + "]");
		} catch( ArrayIndexOutOfBoundsException ae ) {
			throw new ParameterException( "Path Position " + pos + " doesnt exist.");
		}
	}
	
}
