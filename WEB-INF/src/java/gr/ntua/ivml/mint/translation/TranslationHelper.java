package gr.ntua.ivml.mint.translation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;

import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.persistent.TranslationLiteral;

/**
 * Collect TranslationLiterals and when there are enough, send them for translation,
 * store results in db.
 * @author arne
 *
 */
public class TranslationHelper {

	private HashMap<String, List<TranslationLiteral>> bufferTranslate = new HashMap<>();
	private HashMap<String, List<TranslationLiteral>> bufferDetect = new HashMap<>();
	
	public static final Logger log = Logger.getLogger( TranslationHelper.class );
	public static final int MAXCHARCOUNT = 2000;
	
	public void translate( TranslationLiteral tl ) {
		if( tl.getOriginalLiteral().length() > MAXCHARCOUNT) {
			tl.setTranslatedLiteral("TEXT TO LONG FOR TRANSLATION ENGINE (>"+MAXCHARCOUNT+" characters).");
			DB.getSession().merge(tl);
			DB.commit();
			return;
		}
		
		if( tl.getDetectionScore() == 0 ) {
			// detect here
			List<TranslationLiteral> l = bufferDetect.computeIfAbsent(tl.getUsedLanguage()+"->"+tl.getTargetLanguage(), key->new ArrayList<>());
			if(( charCount( l ) + tl.getOriginalLiteral().length()) > MAXCHARCOUNT ) {
				l.clear();
			}
			l.add( tl );
		} else {
			// not translate if nothing to do
			if( tl.getUsedLanguage().equals(tl.getTargetLanguage())) return;
			// translate here 
			List<TranslationLiteral> l = bufferTranslate.computeIfAbsent(tl.getUsedLanguage()+"->"+tl.getTargetLanguage(), key->new ArrayList<>());
			if(( charCount( l ) + tl.getOriginalLiteral().length()) > MAXCHARCOUNT ) {
				// translate
				translateList( tl.getUsedLanguage(), tl.getTargetLanguage(), l );
				l.clear();
				
			}
			l.add( tl );
		}
		
	}
	
	public int charCount( List<TranslationLiteral> l ) {
		return l.stream().mapToInt( tl-> tl.getOriginalLiteral().length()).sum();
	}
	
	// at the end run all leftover detections, queue their results to translations and run all 
	// leftover translations
	public void close() {
		for( List<TranslationLiteral> l:bufferDetect.values()) {
			if( ! l.isEmpty()) {
				String defaultLang = l.get(0).getUsedLanguage();
				detectList( defaultLang, l  );
			}
		}
		for( List<TranslationLiteral> l:bufferTranslate.values()) {
			if( ! l.isEmpty()) {
				String sourceLang = l.get(0).getUsedLanguage();
				String targetLang = l.get(0).getTargetLanguage();
				translateList( sourceLang, targetLang, l  );
			}
		}
	}

	private void translateList(String sourceLang, String targetLang, List<TranslationLiteral> l)  {

		try {
			PangeanicApi.translate(l);
			persist( l );
		} catch( IOException e ) {
			log.error( "Pangeanic API failed to translate",e);
			return;
		} catch( Exception e ) {
			log.error("", e);
		}
	}

	private void persist( List<TranslationLiteral> l ) {
		try {
			Session s = DB.freshSession();
			Transaction tr = s.beginTransaction();
			for( TranslationLiteral tl: l ) s.merge( tl );
			s.flush();
			tr.commit();
			s.close();
		} catch( Exception e ) {
			log.error( "",e) ;
		}
	}
	
	private void detectList(String defaultLang, List<TranslationLiteral> l) {
		try {
			PangeanicApi.languageDetect(l);	
			persist( l );
		} catch( Exception e ) {
			log.error( "Pangeanic API failed to detect languages",e);
			return;
		}
		
		for( TranslationLiteral tl: l ) {
			// avoid detect cycle if the score is 0.
			// score 0 is signal to run detection
			if( tl.getDetectionScore() == 0 ) tl.setDetectionScore((short) -1 );
			translate( tl );
		}
	}
}
