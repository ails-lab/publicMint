package gr.ntua.ivml.mint.translation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.api.handlers.TranslationHandlers.ApplyFilter;
import gr.ntua.ivml.mint.api.handlers.TranslationHandlers.ApplyFilterLang;
import gr.ntua.ivml.mint.api.handlers.TranslationHandlers.ApplyParam;
import gr.ntua.ivml.mint.concurrent.GroovyTransform;
import gr.ntua.ivml.mint.concurrent.Queues;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.f.Interceptor;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Item;
import gr.ntua.ivml.mint.persistent.Translation;
import gr.ntua.ivml.mint.persistent.TranslationLiteral;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.persistent.XmlSchema;
import gr.ntua.ivml.mint.persistent.XpathHolder;
import gr.ntua.ivml.mint.util.ApplyI;
import gr.ntua.ivml.mint.util.Config;
import gr.ntua.ivml.mint.util.EdmSchemaLiteralProcessor;
import gr.ntua.ivml.mint.util.Interceptors;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.StringUtils;
import nu.xom.Document;

/**
 * 
 * This class takes the dataset and all the configurations of the translations runs as input
 * It queues a translation process that
 *  iterates over all the items
 *  collect literals and stores them in db. 
 *  when having collected all the literals, sends them off to the translation engine
 *  stores the translation results in the db.
 *  
 *  THIS DOES NOT apply translations to the XML file.
 *  
 * @author arne
 *
 */
public class Translator implements DB.SessionRunnable {
	
	public static final Logger log = Logger.getLogger( Translator.class);
	public Long translationId = null;
	public Translation translation = null;
	public SchemaLiteralProcessor schemaLiteralProcessor;
	
	public Status status = new Status();
	public HashMap<String, Integer> counters= new HashMap<>();
	
	// update status in db every minute
	private long last = System.currentTimeMillis();
	
	public static class Status {
		public int totalRecords, readRecords, extractedLiterals,
		translatedLiterals, languageDetectedLiterals, charCount;
		
		// here goes an error message
		public String error = null;
	}
	
	public static class FieldLiteral {
		public String literal, language, fieldName;
		
		// last pathelement of fieldname, without array indices
	}

	public static class TranslatedLiteral {
		public String originalLiteral, originalLanguage, fieldName,
		translatedLiteral, translatedLanguage;
		public int score; //0-1000 0.0-1.0
	}

	public interface SchemaLiteralProcessor {
		public List<FieldLiteral> extractLiterals( Document doc, JsonNode config );
		public void applyTranslation( Document doc, Collection<TranslatedLiteral> translations );
	}
	
	
	public Translator( long translationId ) {
		this.translationId = translationId;
	}
	
	// Create the db entry for thing running
	// iterate over records, extracting literals and their language tags
	// when all literals extracted, translate them

	// there should be a queue, so that not many translations run in parallel, in fact only one should be running,
	// the rest be queued
	
	public void wrappedRun() {
		try {
			translation = DB.getTranslationDAO().getById(translationId, false);
			translation.setStatus(Translation.RUNNING);
			DB.commit();
			Dataset ds = translation.getDataset();
			status.totalRecords = ds.getItemCount();
			LiteralTable literalTable = new LiteralTable();
			
			//this item processor writes all literals as per config into the table
			// and writes the table to db when needed
			ApplyI<Item> itemProcessor = createLiteralCollector( literalTable, createConfigCheck());
			
			// collect literals and write them to db
			ds.processAllItems(itemProcessor, false);
			ds.logEvent("Literals collected for translation",  Jackson.om().writeValueAsString(status));
			// anything left to do.
			literalTable.updateDbCounts( translation );
			ds.logEvent("Duplication counts updated");

			// translate the db entries			
			translateDb();
			ds.logEvent( "Translation finished");
			translation.setEndDate( new Date());
			translation.setStatus(Translation.OK);
			writeStatus(true);
			DB.commit();
		} catch( Exception e) {
			log.error( "", e );
			status.error = "Exception during translation running: \n " + e.getMessage();
			translation.setStatus(Translation.FAILED);
			translation.setEndDate( new Date());
			writeStatus(true);
			
		}
	}
	
	// feed the groovyTransform
	public static void applyTranslation(Dataset ds, Translation translation, ApplyParam params ) throws Exception {
		// find the schemaLiteralProcessor, for now hardcoded
		Translator translator = new Translator( translation.getDbID() );
		translator.translation = translation;
		translator.schemaLiteralProcessor = new EdmSchemaLiteralProcessor(null);
		
		Interceptor<Item,Item> interceptor = new Interceptors.ItemDocWrapInterceptor(
			Interceptor.modifyInterceptor( doc -> 
				translator.applyTranslationDocument(doc, tl -> checkTranslation(tl, params)))
			, true);
		// String names = Jackson.om().readTree( translation.getJsonConfig()).at
		GroovyTransform gt = GroovyTransform.defaultFromDataset(ds, interceptor, 
				Optional.of( tr -> {
					tr.setName( "Translation " + StringUtils.concatIfAll( "of ", params.getFieldNames()) + " from " + params.getLangFrom());
					findOutputSchema(ds).ifPresent( xs -> tr.setSchema( xs ));
				}));
		
		gt.postProcessor = Optional.of( tr -> {
			String details = translator.counters.entrySet().stream()
				.map( e-> e.getKey()+" applied " + e.getValue() + " times.")
				.collect( Collectors.joining( "\n"));
			tr.logEvent( "Translation apply stats", details );
		});
		
		Queues.queue(gt, "db");
	}
	
	// if we ever support more than one schema, needs to happen here
	private static Optional<XmlSchema> findOutputSchema( Dataset ds) {
		String outputSchemaName = Config.get( "translation.outputSchema");
		if( StringUtils.empty(outputSchemaName)) return Optional.empty();
		XmlSchema out = DB.getXmlSchemaDAO().getByName( outputSchemaName);
		return Optional.ofNullable( out );
	}
	
	
	public static  boolean checkTranslation( TranslationLiteral tl, ApplyParam params ) {
		ApplyFilterLang filter = params.get( tl.getUsedLanguage());
		if( filter == null ) return false;
		if( filter.threshold > tl.getTranslationScore()/1000f) return false;
		if( filter.exclude ) return false;
		
		if( filter.fields == null ) return true;
		// everything not : from the end
		String shortFieldname = tl.getFieldName().replaceAll("^.*:([^:]+)$", "$1");
		ApplyFilter af = filter.fields.get( shortFieldname);
		if( af == null ) return false;
		
		if( af.threshold > tl.getTranslationScore()/1000f ) return false;
		if( af.exclude ) return false;

		return true;
	}
	
	
	
	public void applyTranslationDocument( Document d, 
			Predicate<TranslationLiteral> applyParamCheck ) {
		// extract the literals
		List<FieldLiteral> literals = schemaLiteralProcessor.extractLiterals(d, null);
		ArrayList<FieldLiteral> acceptedLiterals = new ArrayList<>();
		ArrayList<Long> retrievalLiterals = new ArrayList<>();
		
		TranslationLiteral tl = new TranslationLiteral(); //re-used not stored, just for the hash calc
		for( FieldLiteral fl: literals ) {
			tl.setFieldName( shortFieldname(fl.fieldName));
			tl.setOriginalLiteral(fl.literal);
			acceptedLiterals.add( fl );
			retrievalLiterals.add( tl.updateHash());
		}
		
		List<TranslationLiteral> translations = DB.getTranslationLiteralDAO().listByHashList( translation,retrievalLiterals );
		// filter and load into lookup ... there might be a list with the same hash
		final HashMap<Long, ArrayList<TranslationLiteral>> lookup = new HashMap<>();
		for( TranslationLiteral tl2: translations ) {
			if( applyParamCheck.test(tl2)) {
				ArrayList<TranslationLiteral> al = lookup.computeIfAbsent( tl2.updateHash(), k-> new ArrayList<>());
				al.add( tl2 );
			}
		}

		final List<TranslatedLiteral> literalsForInsert = new ArrayList<>();
		zip( acceptedLiterals, retrievalLiterals, (lit, hash) -> {
			List<TranslationLiteral> translations2 = lookup.get( hash );
			TranslationLiteral translation = null;
			if( translations2 != null )
				for( TranslationLiteral tl2: translations2 ) {
					if( lit.language.equals( tl2.getOriginalLanguageTag()))
						translation = tl2;
				}
			
			if( translation != null ) {
				TranslatedLiteral translatdLiteral = new TranslatedLiteral();
				translatdLiteral.fieldName = lit.fieldName;
				translatdLiteral.translatedLanguage = translation.getTargetLanguage();
				translatdLiteral.translatedLiteral = translation.getTranslatedLiteral();
				translatdLiteral.score = translation.getTranslationScore();
				literalsForInsert.add( translatdLiteral );
				
				// some stats
				// very short fieldname
				String fieldname = shortFieldname(lit.fieldName).replaceAll("^.*:([^:]+)$", "$1" );
				counters.merge(fieldname+":"+translation.getUsedLanguage()+"->"+translation.getTargetLanguage(), 1, (a,b)->a+b);
			}
		});

		if( literalsForInsert.size() > 0 ) {
			schemaLiteralProcessor.applyTranslation(d, literalsForInsert);
		}
		
	}
	
	public static <A,B> void zip( Iterable<A> a, Iterable<B> b, BiConsumer<A,B> mergeFunction ) {
		Iterator<A> aIter = a.iterator();
		Iterator<B> bIter = b.iterator();
		while( aIter.hasNext() && bIter.hasNext())
			mergeFunction.accept(aIter.next(), bIter.next());
	}
	
	public boolean isOneMinutePassed( ) {
		if(( System.currentTimeMillis() - last ) > 1000*60 ) {
			last = System.currentTimeMillis();
			return true;
		}
		return false;
	}
	
	
	private void translateDb() throws Exception  {
		
		final TranslationHelper th = new TranslationHelper();
		ApplyI<TranslationLiteral> process = literal -> {
			th.translate( literal );
			status.translatedLiterals+=1;
			writeStatus( false );
		};
				
		try {
			DB.getTranslationLiteralDAO().onAll(process, "translation="+ translation.getDbID(), false);  
			// process the rest.
			th.close();
		} catch( Exception e ) {
			log.error(e);
			translation.getDataset().logEvent("Translation failed", e.getMessage());
			throw e;
		}
	}
	
	public ApplyI<Item> createLiteralCollector(LiteralTable literalTable,  BiFunction<String,Optional<String>,Optional<TranslationLiteral>> checkLiteralAgainstConfig ) {
		ApplyI<Item> res = (Item i) -> {
			Document doc = i.getDocument();
			status.readRecords += 1;
			List<FieldLiteral> literals = schemaLiteralProcessor.extractLiterals(doc, null );
			for( FieldLiteral fl: literals ) {
				Optional<TranslationLiteral> tl = checkLiteralAgainstConfig.apply( fl.fieldName, Optional.ofNullable( fl.language ));
				if( tl.isPresent() ) {
					tl.get().setItemId(i.getDbID());
					tl.get().setOriginalLiteral(fl.literal);
					if( literalTable.addTranslation(translation, tl.get())) {
						status.extractedLiterals++;
						status.charCount += tl.get().getOriginalLiteral().length();
						writeStatus( false );
					}
				}
				
			}
		};
		return res;
	}
	
	
	private BiFunction<String, Optional<String>, Optional<TranslationLiteral>> createConfigCheck( ) throws Exception {
		ObjectNode config = (ObjectNode) Jackson.om().readTree( translation.getJsonConfig());
		HashSet<String> validPaths = new HashSet<>();
		HashMap<String,String> langLookup = new HashMap<>();
		HashSet<String> detect = new HashSet<>();
		
		Optional<String> defaultLang=  config.has( "defaultLanguage") ?
			Optional.of( config.get( "defaultLanguage").asText())
			:
			Optional.empty();
		
		
		for( JsonNode val: (ArrayNode) config.get( "selectedFields")) {
			long pathHolderId = val.asLong();
			XpathHolder holder = DB.getXpathHolderDAO().getById(pathHolderId, false);
			String normalizedPath = holder.getFullPath();
			validPaths.add(normalizedPath);
		}
			
		if( config.has( "normalizedTags" )) {
			for( JsonNode tagsNode: (ArrayNode) config.get( "normalizedTags")) {
				ObjectNode tags = (ObjectNode) tagsNode;
				String label = tags.get( "label").asText().toLowerCase();
				if( "n/a".equals( label )) label = "--";
				langLookup.put( label, tags.get( "normalizedLabel").asText());
				if( tags.get("applyDetection").asBoolean()) 
					detect.add( label );
			}
		} 
		
		if(!(config.has(  "normalizedTags" ) || 
				config.has( "defaultLanguage"))) {
			log.error( "Could not process the translation definition");
			throw new Exception( "Missing either normalizedTags or defaultLanguage in the json Config");
		}
		
		// selectedFields array with xpathholder ids -> validPaths hashset
		// either defaultLanguage or normalizedTags array with label,normalizedLabel, applyDetection
		BiFunction<String, Optional<String>, Optional<TranslationLiteral>> res = (String fieldName, Optional<String> languageTag ) -> {
			if( ! validPaths.contains(fieldName)) return Optional.empty();
			
			TranslationLiteral tl = new TranslationLiteral();
			tl.setOriginalLanguageTag( languageTag.orElse("--"));
			tl.setUsedLanguage( defaultLang.orElse( langLookup.get( tl.getOriginalLanguageTag() )));

			if( tl.getUsedLanguage() == null ) return Optional.empty();
			
			// detection score is 0 by default, set it to -1 if no detection wanted
			if(! detect.contains(languageTag.orElse("--")))
				tl.setDetectionScore((short) -1 );
			
			tl.setFieldName( shortFieldname(fieldName));
			tl.setTargetLanguage("en");
			return Optional.of(tl);
		};
		
		return res;
	}
	
	
	// create the translation object, set the status
	public static synchronized Translation queueTranslation( Dataset ds, User u, ObjectNode config ) {
		Translation tl = new Translation();
		tl.setCreator(u);
		tl.setDataset(ds);
		tl.setOrganization(ds.getOrganization());
		tl.setOriginalDataset(ds.getOrigin());
		tl.setStartDate(new Date());
		tl.setJsonConfig(config.toString());
		tl.setStatus(Translation.QUEUED );

		tl = DB.getTranslationDAO().makePersistent(tl);
		DB.commit();
		
		Translator translator = new Translator( tl.getDbID());
		// here we should find the right SchemaLiteralProcessor, for now
		translator.schemaLiteralProcessor = new EdmSchemaLiteralProcessor( config );
		Queues.queue(
			translator, "translation");
		
		return tl;
	}
	
	
	// maybe something that should be done when there is a problem with the API server
	public synchronized void cancelAllTranslations() {
		
	}
		
	public synchronized void cancelRunningTranslation() {
		
	}
	
	private void writeStatus( boolean force ) {
		if(! force )
			if( !isOneMinutePassed() ) return;
		if( translation != null ) {
			try {
				translation.setJsonStats( Jackson.om().writeValueAsString(status));
				DB.getTranslationDAO().makePersistent(translation);
				DB.commit();
			} catch( JsonProcessingException e ) {
				translation.setJsonStats( "{ \"error\":\"Json Processing Problem \n" + e.getMessage() + "\"}");
			}
		}
	}
	
	
	
	public static <T> Optional<T> optionalOr( Optional<T>... optionals ) {
		for( Optional<T> o: optionals )
			if( o.isPresent()) return o;
		return Optional.empty();
	}
	
	public static String shortFieldname( String longFieldname ) {
		// remove array markers
		longFieldname = longFieldname.replaceAll("\\[\\d+\\](?=/)", "");
		longFieldname = longFieldname.replaceAll("\\[\\d+\\]$", "");
		int last = longFieldname.lastIndexOf("/@{");
		if( last < 0 )
			last = longFieldname.lastIndexOf("/{");
		
		return longFieldname.substring(last+1);
}

}
