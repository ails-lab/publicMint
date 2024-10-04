package gr.ntua.ivml.mint.api.handlers;

import static gr.ntua.ivml.mint.api.RequestHandlerUtil.check;
import static gr.ntua.ivml.mint.api.RequestHandlerUtil.grab;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.api.ParameterException;
import gr.ntua.ivml.mint.api.RequestHandler;
import gr.ntua.ivml.mint.api.RequestHandlerUtil;
import gr.ntua.ivml.mint.concurrent.Queues;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic.Access;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Organization;
import gr.ntua.ivml.mint.persistent.Translation;
import gr.ntua.ivml.mint.persistent.TranslationLiteral;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.persistent.XmlSchema;
import gr.ntua.ivml.mint.persistent.XpathHolder;
import gr.ntua.ivml.mint.translation.PangeanicApi;
import gr.ntua.ivml.mint.translation.Translator;
import gr.ntua.ivml.mint.util.Config;
import gr.ntua.ivml.mint.util.Fetch;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.JacksonBuilder;
import gr.ntua.ivml.mint.util.WebToken;
import gr.ntua.ivml.mint.util.WebToken.Content;

/*
 * All the API calls that we want to support for translation
 */

public class TranslationHandlers {
	// get the param json the user dataset and initiate the process
	// the running translation should be interruptible and have a status
	
	public static final Logger log = Logger.getLogger( TranslationHandlers.class );
	
	
	public static class ApplyFilter {
		public float threshold;
		public boolean exclude;
	}
	public static class ApplyFilterLang extends ApplyFilter {
		public HashMap<String, ApplyFilter> fields;
	}
	
	// key is src lang
	public static class ApplyParam extends HashMap<String, ApplyFilterLang>{
		public String getFieldNames() {
			// find not excluded field names and return them
			return values().stream().flatMap( (ApplyFilterLang filterForLang)-> {
				if( filterForLang.fields == null ) return null;
				return filterForLang.fields.entrySet().stream()
						.filter( e-> !e.getValue().exclude)
						.map( e-> e.getKey());
			}).distinct().collect( Collectors.joining(", "));
		}
		
		public String getLangFrom() {
			return keySet().stream().collect( Collectors.joining(", "));
		}
	}
	
	
	public static void startTranslation( HttpServletRequest request, HttpServletResponse response ) {
		// parameters from posted json
		//{
	    //      datasetId: this.dataset_details.id,
	    //      defaultLanguage: this.selectedDefaultLang,
	    //      selectedFields: this.selectedFields.map(field => field.xpathHolderId)
	    //}

		RequestHandlerUtil.errorWrap( 
			(req, resp) -> {
				User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
				ObjectNode on = (ObjectNode) RequestHandlerUtil.getJson(req, Optional.empty());
				int datasetId = on.at( "/datasetId").asInt();
				if( datasetId == 0 ) throw new ParameterException( "Missing datasetId in json");
				
				check( ()->on.at( "/selectedFields").isArray(), "Invalid selectedFields");
				RequestHandlerUtil.check(()->
					on.at("/normalizedTags").isArray()
					|| (!on.at("/defaultLanguage").isMissingNode()) 
					, "Need normalizedTags or defaultLanguage");
				
				// now checks that involve the db 
				
				Dataset ds = RequestHandlerUtil.accessDataset(datasetId, u, Access.WRITE);

				// do the selected fields belong to the dataset in question ??
				RequestHandlerUtil.check( ()-> {
					for( JsonNode xpathIdNode: (ArrayNode) on.get("selectedFields")) {
						int xpathId = xpathIdNode.asInt();
						XpathHolder xp = DB.getXpathHolderDAO().getById((long)xpathId, false);
						if( xp.getDataset().getDbID() != datasetId ) return false;
					}
					return true;
				}, "Invalid selectedFields, not in Dataset");
				
				// is the dataset translateable ?
				List<XmlSchema> supportedSchemas = XmlSchema.getSchemasFromConfig("translation.schemas");
				
				XmlSchema dsSchema = RequestHandlerUtil.grab( ()->ds.getSchema(), "Schema does not support translation");
				RequestHandlerUtil.check( ()-> {
					for( XmlSchema xs: supportedSchemas )
						if( xs.getDbID() == dsSchema.getDbID()) return true;
					return false;
				}, "Schema does not support Translation.");
				
				Translator.queueTranslation(ds, u, on);
				RequestHandler.okJson( "msg",  "Dataset queued for translation", response );
			}).handle(request, response);

	}

	public static void listTranslations( HttpServletRequest request, HttpServletResponse response )  {
		RequestHandlerUtil.errorWrap( 
			(req, resp) -> {
				// check for params and rights
				User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
				// need param for orgId or datasetId
				// user needs read for org
				Optional<Integer> orgId = RequestHandlerUtil.getOptInt(request, "orgId");
				Optional<Integer> datasetId = RequestHandlerUtil.getOptInt(request, "datasetId");
				check( ()-> orgId.isPresent()||datasetId.isPresent(), "Need orgId or datasetId");
				
				// get the Translations
				List<Translation> res = Collections.emptyList();
				if( datasetId.isPresent()) {
					Dataset ds = RequestHandlerUtil.accessDataset(datasetId.get(), u, Access.READ);
					res = DB.getTranslationDAO().findByDataset( ds );
				}
				if( orgId.isPresent()) {
					Organization org = RequestHandlerUtil.accessOrganization(orgId.get(), u, Access.READ );
					res = DB.getTranslationDAO().findByOrganization( org );
				}
				
				Duration expiresIn = Duration.ofDays( 2 );
				
				String baseLink = request.getRequestURL().toString()
						.replaceAll("/api/translation/list.*", 
								    "/api/callByToken?token=" );
				String baseDownloadLiteralsUrl = "/translation/downloadLiterals?translationId=";
						
						
	
				// build useful json output
				JacksonBuilder jb = JacksonBuilder.arr();
				for( Translation t:res ) {
					ObjectNode simpleJsonTranslation = (ObjectNode) t.listView();
	
					// add the download link for literals, valid 2 days
					WebToken.Content c = Content.create().expires(expiresIn).url( baseDownloadLiteralsUrl + t.getDbID() );
					c.user( u.getLogin());
					simpleJsonTranslation.put( "share", baseLink + URLEncoder.encode( 
								    		c.encrypt( Config.get( "secret")), "UTF-8"));
					jb.append( simpleJsonTranslation );
				}
				RequestHandler.okJson(jb.getArray(), response);
			}).handle(request, response);
	}

	public static void statusTranslation( HttpServletRequest request, HttpServletResponse response )  {
		// Dataset that is supposed to be translated. User making the request
		// find the worker object and ask about status
	}

	public static void applyTranslation( HttpServletRequest request, HttpServletResponse response ) {
		// dataset write access, config, finished translation run there
		// read all literals into mem? too big?
		// create query literal table size X
		// full traverse review literal table and add translations.
		// reiterate over the records and apply translation table		

		RequestHandlerUtil.errorWrap( 
				(req, resp) -> {
					User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
					
					int datasetId = RequestHandlerUtil.getInt(request, "datasetId");
					int translationId = RequestHandlerUtil.getInt(request, "translationId");
					
					Dataset d = RequestHandlerUtil.accessDataset(datasetId, u, Access.WRITE );
					Translation t = grab( ()-> DB.getTranslationDAO().getById((long)translationId, false), "Unknown Translation");
					
					ApplyParam json = RequestHandlerUtil.getJson( req, ApplyParam.class );
					
					try {
						Translator.applyTranslation(d, t, json);
					} catch( Exception e ) {
						log.error( "Failed to apply",e );
						RequestHandler.errJson("Failed to apply Translation", response);
						return;
					}					
					
					RequestHandler.okJson( "msg", "Translation application queued", resp );
				}).handle(request, response);					
	}

	public static void deleteTranslation( HttpServletRequest request, HttpServletResponse response ) {
		// dataset write access, config, finished translation run there
		// read all literals into mem? too big?
		// create query literal table size X
		// full traverse review literal table and add translations.
		// reiterate over the records and apply translation table		

		RequestHandlerUtil.errorWrap( 
			(req, resp) -> {
				User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
				
				int translationId = RequestHandlerUtil.getPathInt(request, 2);
				
				Translation t = grab( ()-> DB.getTranslationDAO().getById((long)translationId, false), "Unknown Translation");
				AccessAuthenticationLogic.checkAccess(t.getOrganization(), u, Access.WRITE);
				
				try {
					DB.getTranslationDAO().makeTransient(t);
					DB.commit();
				} catch( Exception e ) {
					log.error( "Failed to delete Translation",e );
					RequestHandler.errJson("Failed to delete Translation", response);
					return;
				}										
				RequestHandler.okJson( "msg", "Translation deleted", resp );
			}).handle(request, response);					
	}

	public static void getReviewTranslation( HttpServletRequest request, HttpServletResponse response )   {
		// start=?, count=?, fieldName=?opt, translationId=?, srcLang=??
		// returns json array of id:,srcLang:,targetLang:,srcLiteral:, targetLiteral:,humanReview, detectScore, translateScore
		RequestHandlerUtil.errorWrap( 
				(req, resp) -> {
					User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");

					int start = RequestHandlerUtil.getOptInt(request, "start").orElse(0);
					int count = RequestHandlerUtil.getOptInt(request, "count").orElse(10);
					
					int translationId = RequestHandlerUtil.getInt(request, "translationId");
					Translation translation = grab( ()-> DB.getTranslationDAO().getById((long)translationId, false), "Unknown Translation");
					check( () ->
						AccessAuthenticationLogic.getAccess(translation.getOrganization(), u).compareTo(
								Access.READ ) >= 0, 
						"No read access");
					Optional<String> fieldname = RequestHandlerUtil.getOptUnique(request, "fieldName");
					String srcLang = RequestHandlerUtil.getUnique(request, "srcLang");
					
					int totalCount = DB.getTranslationLiteralDAO().countBySrcField(translation, srcLang, fieldname);
					List<TranslationLiteral> tlList = DB.getTranslationLiteralDAO().pageBySrcField( start, count, translation, srcLang, fieldname );
					
					JacksonBuilder result = JacksonBuilder.obj()
						.put( "totalCount", totalCount )
						.put( "start", start )
						.put( "max", count )
						.put( "actual", tlList.size());
					
					JacksonBuilder literals = result.put( "literals");
					
					for( TranslationLiteral tl:tlList ) 
						literals.append( tl.listJson());
					
					RequestHandler.okJson( result.getObj(), resp);	

				}).handle(request, response);
	
	}

	public static void postReviewTranslation( HttpServletRequest request, HttpServletResponse response )  {
		// expect a json array with objects, containing translationLiteralIds
		
		RequestHandlerUtil.errorWrap( 
				
				(req, resp) -> {
					User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
					JsonNode bodyJson = RequestHandlerUtil.getJson(request, Optional.empty());
					check( () -> bodyJson.isArray(), "Expected a json array");
					ArrayNode arr = (ArrayNode) bodyJson;
					
					// check if we can write to the orgs of all the literals
					try {
						for( JsonNode tlJson: arr ) {
							check( ()-> tlJson.has( "translationLiteralId"), "translationLiteralId missing in array element");
							TranslationLiteral tl = grab(()->DB.getTranslationLiteralDAO()
									.getById( tlJson.get( "translationLiteralId").asLong(), false), "Invalid translationLiteralId");
							RequestHandlerUtil.accessOrganization( tl.getTranslation().getOrganization(), u, Access.WRITE);
	
							// add whatever in the tlJson to the tl
							if( tlJson.has( "rejected")) tl.setHumanReview(false);
							if( tlJson.has( "accepted")) tl.setHumanReview(true);
							if( tlJson.has( "translatedLiteral")) {
								tl.setTranslatedLiteral( tlJson.get( "translatedLiteral").asText());
								tl.setHumanReview(true);
							}
						}
					} catch( Exception  e ) {
						log.error( "Error in POST review, no data is stored");
						DB.getSession().clear();
						throw e;
					}

					DB.commit();
					RequestHandler.okJson( "msg", "All changes stored", resp );
				}).handle(request, response);
	
	}

	public static void downloadTranslationLiterals( HttpServletRequest request, HttpServletResponse response )  {
		// expect a json array with objects, containing translationLiteralIds
		
		RequestHandlerUtil.errorWrap( 
				
				(req, resp) -> {
					User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
					int translationId = RequestHandlerUtil.getInt(request, "translationId");
					Translation translation = grab( ()-> DB.getTranslationDAO().getById((long)translationId, false), "Unknown Translation");
					AccessAuthenticationLogic.checkAccess(translation, u, Access.READ );

					check(() -> translation.getStatus().equals(Translation.OK), "Translations cannot be downloaded" );
					// we need the base url for this request to create liunks for items to download
					String baseUrl = req.getRequestURL().toString().replaceAll("^(.*/api/).*","$1");
					// TODO allow for alternative formats
					RequestHandlerUtil.streamDownload("application/json", "Translation_"+translation.getDbID()+".json.gz", PangeanicApi.pecatJsonOutput(translation, baseUrl), true, resp);
					
				}
				
		).handle(request, response);
	
	}

	public static void pecatReviewMerge( HttpServletRequest request, HttpServletResponse response )  {
		//a url with gz json array of pecat modified translation literals
		
		RequestHandlerUtil.errorWrap( 
				
				(req, resp) -> {
					User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
					String url = RequestHandlerUtil.getUnique(req, "url" );
				
					Fetch fetch = Fetch.fetchUrl(url);
					if( fetch.errMsg != null ) throw new ParameterException( fetch.errMsg );

					// test gunzip
					byte[] gunzipped = Fetch.gunzip(fetch.data);
					if( gunzipped.length == 0 ) gunzipped = fetch.data;
					
					// if it throws it throws
					JsonNode node = Jackson.om().readTree(gunzipped);
					DB.SessionRunnable r = ()-> {
						User localUser = DB.getUserDAO().getById(u.getDbID(), false);
						PangeanicApi.mergePecatReview(node, localUser);
					};
					Queues.queue(r,  "db");
					RequestHandler.okJson( "msg", "'" + url +"' harvested and queued for processing as review.", resp );
				}
		).handle(request, response);
	
	}


}
