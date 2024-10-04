package gr.ntua.ivml.mint.api.handlers;

import static gr.ntua.ivml.mint.api.RequestHandlerUtil.check;
import static gr.ntua.ivml.mint.api.RequestHandlerUtil.grab;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.api.ParameterException;
import gr.ntua.ivml.mint.api.RequestHandler;
import gr.ntua.ivml.mint.api.RequestHandlerUtil;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic.Access;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Lock;
import gr.ntua.ivml.mint.persistent.Organization;
import gr.ntua.ivml.mint.persistent.Translation;
import gr.ntua.ivml.mint.persistent.TranslationLiteral;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.persistent.XmlSchema;
import gr.ntua.ivml.mint.persistent.XpathHolder;
import gr.ntua.ivml.mint.translation.Translator;
import gr.ntua.ivml.mint.util.JacksonBuilder;
import gr.ntua.ivml.mint.util.StringUtils;

/*
 * All the API calls that we want to support for translation
 */

public class LockHandlers {
	// get the param json the user dataset and initiate the process
	// the running translation should be interruptible and have a status
	
	public static final Logger log = Logger.getLogger( LockHandlers.class );
	
	// either all locks if you are super or just the user locks
	public static void getLocks( HttpServletRequest request, HttpServletResponse response ) {

		RequestHandlerUtil.errorWrap( 
			(req, resp) -> {
				User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
				
				List<Lock> l = Collections.emptyList();
				if( u.hasRight( User.SUPER_USER)) {
					l = DB.getLockManager().findAll();
				} else {
					l = DB.getLockManager().findByUser(u);
				}
				
				JacksonBuilder result = JacksonBuilder.arr();
				for( Lock lock: l ) {
					result.appendObj()
						.put( "id",lock.getDbID())
						.put( "login", lock.getUserLogin())
						.put( "timestamp", lock.getAquired().toString())
						.put( "prettyTime", StringUtils.prettyTime(lock.getAquired()))
						.put( "name", lock.getName());
				}
				RequestHandler.okJson(result.get(), response);

			}).handle(request, response);
	}

	public static void deleteLock( HttpServletRequest request, HttpServletResponse response )  {
		RequestHandlerUtil.errorWrap( 
				(req, resp) -> {
					// check for params and rights
					User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
					
					int lockId = RequestHandlerUtil.getInt(request, "lockId");

					Lock l =grab( ()-> DB.getLockManager().getByDbID( (long)lockId ), "No such lock");
					check( ()-> u.hasRight( User.SUPER_USER) || u.getLogin().equals(l.getUserLogin()), "No right to delete Lock");
					
					DB.getLockManager().releaseLock(l);
					RequestHandler.ok( resp);
		}).handle(request, response);
	}

}
