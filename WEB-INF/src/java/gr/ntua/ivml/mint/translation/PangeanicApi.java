package gr.ntua.ivml.mint.translation;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.f.ThrowingConsumer;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic.Access;
import gr.ntua.ivml.mint.persistent.Translation;
import gr.ntua.ivml.mint.persistent.TranslationLiteral;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.util.Config;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.StringUtils;
import gr.ntua.ivml.mint.util.WebToken;


public class PangeanicApi {
	  // Definitions
	
	private static final Logger log = Logger.getLogger( PangeanicApi.class);
	
    private static String translationUrl = Config.get( "translation.translate.url" );
    private static String languageDetectionUrl = Config.get( "translation.detect.url" ); 
    private static String apiKey = Config.get( "translation.apikey" );
    private static Optional<HttpHost> proxy;
    private static int TIMEOUT = 60*1000; // 1 min in millies
    
    // POJOs for parsing endpoint responses
    private static class LanguageDetectSingleResponse {
        private String src_detected;
        private double src_lang_score;

        public String getSrc_detected() {
            return src_detected;
        }

        public void setSrc_detected(String src_detected) {
            this.src_detected = src_detected;
        }

        public double getSrc_lang_score() {
            return src_lang_score;
        }

        public void setSrc_lang_score(double src_lang_score) {
            this.src_lang_score = src_lang_score;
        }

        @Override
        public String toString() {
            return "LanguageDetectSingleResponse{" +
                    "src_lang_detected='" + src_detected + '\'' +
                    ", src_lang_score='" + src_lang_score + '\'' +
                    '}';
        }
    }

    private static class LanguageDetectionResponse {
        private List<LanguageDetectSingleResponse> detected_langs;

        public List<LanguageDetectSingleResponse> getDetected_langs() {
            return detected_langs;
        }

        public void setDetected_langs(List<LanguageDetectSingleResponse> detected_langs) {
            this.detected_langs = detected_langs;
        }

        @Override
        public String toString() {
            return "LanguageDetectionResponse{" +
                    "detected_langs=" + detected_langs +
                    '}';
        }
    }

    private static class TranslationSingleResponse {
        private String src;
        private String tgt;
        private double score;

        public String getSrc() {
            return src;
        }

        public void setSrc(String src) {
            this.src = src;
        }

        public String getTgt() {
            return tgt;
        }

        public void setTgt(String tgt) {
            this.tgt = tgt;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }

    private static class TranslationResponse {
        private List<TranslationSingleResponse> translations;
        private String src_lang;
        private String tgt_lang;

        public TranslationSingleResponse getTranslationBySource (String source) {
            return translations.stream().filter(trans -> source.equals(trans.getSrc())).findFirst().orElse(null);
        }
        public List<TranslationSingleResponse> getTranslations() {
            return translations;
        }

        public void setTranslations(List<TranslationSingleResponse> translations) {
            this.translations = translations;
        }

        public String getSrc_lang() {
            return src_lang;
        }

        public void setSrc_lang(String src_lang) {
            this.src_lang = src_lang;
        }

        public String getTgt_lang() {
            return tgt_lang;
        }

        public void setTgt_lang(String tgt_lang) {
            this.tgt_lang = tgt_lang;
        }
    }


    public static JsonNode createAnnotationBody(String translation) {
        ObjectMapper om = new ObjectMapper();
        JsonNode body = om.createObjectNode();
        ((ObjectNode) body).put("type", "TextualBody");
        ((ObjectNode) body).put("value", translation);
        ((ObjectNode) body).put("language", "en");

        return body;
    }

    // should all have the same src language.
    // result is filled into the trasnlation details
    public static void translate(List<TranslationLiteral> value) throws IOException {

        ObjectMapper om = new ObjectMapper();
        JsonNode requestBody = om.createObjectNode();

        ((ObjectNode) requestBody).put("apikey", apiKey);
        ((ObjectNode) requestBody).put("mode", "EUROPEANA");
        ((ObjectNode) requestBody).putArray("src");

        for( TranslationLiteral detail: value ) {
            ((ArrayNode) ((ObjectNode) requestBody).get("src")).add(detail.getOriginalLiteral());        	
        }

        ((ObjectNode) requestBody).put("src_lang", value.get(0).getUsedLanguage());
        ((ObjectNode) requestBody).put("include_src", true);
        ((ObjectNode) requestBody).put("tgt_lang", "en");
        log.debug( "Translate Request " + requestBody.toString());
        long startTime = System.currentTimeMillis();
        String response = null;
        try {
        	Request r = Request.Post(translationUrl);
        	getProxy().ifPresent( h-> r.viaProxy(h));
	        response = r.bodyString(requestBody.toString(), ContentType.APPLICATION_JSON)
	                .socketTimeout(TIMEOUT)
                    .connectTimeout(TIMEOUT)
	                .execute()
	                .returnContent()
	                .toString();
	        
	        log.debug( "Translate Response " + response );
	        TranslationResponse res = om.readValue(response, TranslationResponse.class);
	        Iterator<TranslationSingleResponse> itTrans = res.translations.iterator();
	        Iterator<TranslationLiteral> itRes = value.iterator();
	        while( itTrans.hasNext() && itRes.hasNext() ) {
	        	TranslationSingleResponse tsr = itTrans.next();
	        	TranslationLiteral detail = itRes.next();
	        	detail.setTranslatedLiteral( tsr.getTgt());
	        	detail.setTranslationScore( (short) ( tsr.getScore() * 1000 ));
	        	
	        	if( ! tsr.getSrc().equals( detail.getOriginalLiteral())) 
	        			log.warn( "Inconsistent Translation: \n" + 
	        					" SrcOriginal: " + detail.getOriginalLiteral() + "\n" +
	        					" ApiReturn  : " + tsr.getSrc() + "\n" );
	        }
        } catch( Exception e ) {
        	log.error( "",  e );
        	log.error( "Request: \n" + requestBody.toString()+"\n\n");
        	log.error( "Response: \n" + response + "\n\n");
        	log.error( "After " + (( System.currentTimeMillis() - startTime) / 1000) + " secs");
        	throw e;
        }

    }

    public static List<TranslationLiteral> languageDetect(List<TranslationLiteral> values) throws IOException {

        ObjectMapper om = new ObjectMapper();
        JsonNode requestBody = om.createObjectNode();
        ((ObjectNode) requestBody).put("apikey", apiKey);
        ((ObjectNode) requestBody).put("mode", "EUROPEANA");
        ((ObjectNode) requestBody).putArray("src");

        for (TranslationLiteral value : values) {
            ((ArrayNode) ((ObjectNode) requestBody).get("src")).add(value.getOriginalLiteral());
        }
        log.info( "Detect Request " + requestBody.toString());
        Request r = Request.Post(languageDetectionUrl);
        getProxy().ifPresent( h-> r.viaProxy(h));
        
        String response = r.bodyString(requestBody.toString(), ContentType.APPLICATION_JSON)
                            .socketTimeout(TIMEOUT)
                            .connectTimeout(TIMEOUT)
                            .execute()
                            .returnContent()
                            .toString();

        log.info( "Detect Response " + response );
        
        LanguageDetectionResponse res = om.readValue(response, LanguageDetectionResponse.class);
        List<LanguageDetectSingleResponse> responseList = res.getDetected_langs();
        for (int i=0; i < responseList.size(); i++) {
            values.get(i).setOriginalLanguageTag(responseList.get(i).getSrc_detected());
            values.get(i).setDetectionScore(
            	(short)(responseList.get(i).getSrc_lang_score()*1000)
            );
        }

        return values;
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
    
	private static String downloadToken( int itemId ) {
		return WebToken.Content.create()
		.expires( Duration.ofDays(90))
		.user( "admin" )
		.url( "/api/item/download?itemId="+itemId )
		.keepParameters( false )
		.encrypt(Config.get( "secret"));
	}

    
    public static ThrowingConsumer<OutputStream> pecatJsonOutput( Translation translation, String baseUrl )  {
    	return (output) -> {
    		
			ObjectMapper om = Jackson.om();
			String urlTemplate = baseUrl + "callByToken?token=";
			try (SequenceWriter sw = om.writer().writeValuesAsArray(output)){
    			    			
    			int pageSize = 1000;
    			int currentStart = 0;
    			
    			List<TranslationLiteral> page; 
    			
    			do  {
    				page = DB.getTranslationLiteralDAO().pageByTranslation( translation, currentStart, pageSize );
    				for( TranslationLiteral tl: page ) {
    					ObjectNode on = tl.listJson();
    					// remove the itemId and create a 3 months valid item XML download URL
    					if( on.has( "itemId")) {
    						int itemId= on.get( "itemId").intValue();
    						String token = URLEncoder.encode( downloadToken( itemId ), "UTF-8");
    						on.put( "exampleXml", urlTemplate+token );
    						on.remove("itemId");
    					}
    					sw.write( on );
    				}
    				currentStart += pageSize;
    				sw.flush();
    			} while( !page.isEmpty());
    			
    		} catch ( Exception e ) {
    			log.error( "", e );
    		}
    	};
    }
    
    // pecat reintegration
    public static void mergePecatReview( JsonNode review, User u ) {
    	Translation translation = null;
    	try {
	    	ArrayNode allReviews = (ArrayNode) review ; // or wherever they are
	    	for( JsonNode node: allReviews ) {
	    		ObjectNode singleReview = (ObjectNode) node;
	    		boolean first = true;
	    		String reviewState = singleReview.path( "Status").asText();
	    		if( !"NOT_REVIEWED".equals( reviewState )) {
	    			long tlId = singleReview.path("translationLiteralId").asLong();
	    			
	    			TranslationLiteral tl = DB.getTranslationLiteralDAO().getById(tlId, false);
	    			if( first ) {
	    				translation = tl.getTranslation();
	    				if( AccessAuthenticationLogic.getAccess(translation, u).isWorseThan(Access.WRITE)) {
	    					log.error( "User has no access to this translation #" + translation.getDbID() );
	    					return;
	    				}
	    				translation.setStatus(Translation.RUNNING);
	    				first = false;
	    			} else {
	    				if( tl.getTranslation().getDbID() != translation.getDbID()) {
	    					log.error( "Multiple different Translation objects in Pecat review json");
	    					continue;
	    				}
	    			}
	

	    			// merge in 
	    			tl.setHumanReview( !reviewState.equals( "REJECTED"));
	    			if( reviewState.equals( "EDITED")) {
		    			tl.setTranslatedLiteral( singleReview.path( "reviewLiteral" ).asText() );	
		    			tl.setTranslationScore((short)1000);
	    			}
	    			DB.commit();
	    		}
	    	}
    	} catch( Exception e ) {
    		log.error( "Pecat Review merge problem, ignored", e  );
    	}
    	
    	if( translation != null ) {
    		translation.setStatus(Translation.OK);
	    	DB.commit();
    	}
    }
}
