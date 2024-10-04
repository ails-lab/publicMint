package gr.ntua.ivml.mint.persistent;

import java.nio.ByteBuffer;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.util.JacksonBuilder;

public class TranslationLiteral {
	public final Logger log = Logger.getLogger(getClass());

	private long dbID;
	private Translation translation;
	
	// this is a short field name, not including the whole path, so that dc format can be unified over
	private String fieldName;
	private String originalLiteral, translatedLiteral;
	private String originalLanguageTag, usedLanguage;
	// is going to be english, but lets be flexible here
	private String targetLanguage;
	
	private long hash;
	
	// technically irrelevant, but the bigger, the more important the correct translation is.
	private int count;
	
	// approved, rejected, null==not seen
	private Boolean humanReview;
	
	// 0-1000 for 0.0-1.0 -1 for not available
	private short translationScore, detectionScore;

	// one example item id where the literal occured
	private long itemId;
	
	private static ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES); 
	
	public long updateHash( ) {
		// this might be slow .. might put murmur3 at some point
		// needed 64bit for low collision count
		byte[] md5 = DigestUtils.md5( originalLiteral );
		byteBuffer.clear();
		byteBuffer.put( md5, 0, Long.BYTES);
		hash = byteBuffer.getLong(0);
		return hash;
	}
	
	public ObjectNode listJson() {
		String shortFieldname = fieldName.replaceAll("^.*:([^:]+)$", "$1");
		return JacksonBuilder.obj()
			.put( "translationLiteralId", dbID )
			.put( "translationId", translation.getDbID())
			.put( "count", count )
			.put( "fieldName", fieldName )
			.put( "shortFieldname", shortFieldname)
			.put( "humanReview", b-> {
				if( humanReview == null ) return false;
				b.set( humanReview );
				return true;
			})
			.put( "srcLiteral", originalLiteral )
			.put( "translatedLiteral", translatedLiteral )
			.put( "sourceLanguage", usedLanguage )
			.put( "targetLanguage", targetLanguage )
			.put( "translationScorePercent", b -> {
				if( translationScore < 0 ) return false;	
				b.set((float)translationScore/10.0 );
				return true;
			} )
			.put( "detectionScorePercent", b -> {
				if( detectionScore < 0 ) return false;	
				b.set((float)detectionScore/10.0 );
				return true;
			} )
			.put( "itemId", b-> {
				if( itemId <= 0 ) return false;
				b.set( itemId);
				return true;
			})
			.getObj();
	}
	
//
//	Getters Setters
//
	
	
	public long getDbID() {
		return dbID;
	}

	public void setDbID(long dbID) {
		this.dbID = dbID;
	}

	public Translation getTranslation() {
		return translation;
	}

	public void setTranslation(Translation translation) {
		this.translation = translation;
	}

	public String getOriginalLiteral() {
		return originalLiteral;
	}

	public void setOriginalLiteral(String originalLiteral) {
		this.originalLiteral = originalLiteral;
	}

	public String getTranslatedLiteral() {
		return translatedLiteral;
	}

	public void setTranslatedLiteral(String translatedLiteral) {
		this.translatedLiteral = translatedLiteral;
	}

	public String getOriginalLanguageTag() {
		return originalLanguageTag;
	}

	public void setOriginalLanguageTag(String originalLanguageTag) {
		this.originalLanguageTag = originalLanguageTag;
	}

	public String getUsedLanguage() {
		return usedLanguage;
	}

	public void setUsedLanguage(String usedLanguage) {
		this.usedLanguage = usedLanguage;
	}

	public String getTargetLanguage() {
		return targetLanguage;
	}

	public void setTargetLanguage(String targetLanguage) {
		this.targetLanguage = targetLanguage;
	}

	public Boolean getHumanReview() {
		return humanReview;
	}

	public void setHumanReview(Boolean humanReview) {
		this.humanReview = humanReview;
	}

	public short getTranslationScore() {
		return translationScore;
	}

	public void setTranslationScore(short translationScore) {
		this.translationScore = translationScore;
	}

	public short getDetectionScore() {
		return detectionScore;
	}

	public void setDetectionScore(short detectionScore) {
		this.detectionScore = detectionScore;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public long getHash() {
		return hash;
	}

	public void setHash(long hash) {
		this.hash = hash;
	}

	public long getItemId() {
		return itemId;
	}

	public void setItemId(long itemId) {
		this.itemId = itemId;
	}
	
	
	

}
/*

SELECT execute( $$ CREATE TABLE translation_literal (
    translation_literal_id bigint primary key NOT NULL,
    translation_id integer references translation on delete cascade,
    original_literal text,
    translated_literal text,
    -- how frequent was this literal in the original dataset, how important is correct translation
    count int,
    
    -- what language was this tagged with
    original_language_tag text,
    
    -- two letter code of the result of language detection, if run or '--' if not run
    detected_language varchar(2) default '--',
    
    -- what language was assumed on sending for translation
    used_language varchar(2),
    
    -- for the time being we will only translate to english, but who knows,
    -- its cheap to be defensive here.
    target_language varchar(2) default 'en',
    
    -- true for reviewed and correct, false for reviewed and broken, null for not reviewed
    human_review boolean,
    
    -- anumeric value of accuracy provided with this translation
    -- -1 for not available, 0...1000 for range 0-1
    translation_score smallint,
    
    -- if there was detection and if there was a score, its here, same as above
    detection_score smallint
)  $$ )
WHERE NOT table_exists( 'translation_literal');

*/
