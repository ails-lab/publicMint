package gr.ntua.ivml.mint.util;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

public class WebToken {

	private static final Logger log = Logger.getLogger( WebToken.class );
	
	public static class Content {
		public String url, user;
		public Date expires;
		public boolean keepParameters;
		
		
		// try a fluent create api
		public static Content create() {
			return new Content();
		}
		
		public String url() {
			return url;
		}
		
		public Content url( String url ) {
			this.url = url;
			return this;
		}
		
		public String user() {
			return user;
		}
		
		public Content user( String user ) {
			this.user = user;
			return this;
		}
		
		public Date expires( ) {
			return expires;
		}

		public Content expires( Date expires ) {
			this.expires = expires;
			return this;
		}

		public Content expires( Duration d ) {
			this.expires = new Date( d.toMillis() + System.currentTimeMillis());
			return this;
		}

		@JsonIgnore
		public boolean isValid( ) {
			return System.currentTimeMillis() < expires.getTime();
		}
		
		
		public boolean keepParameters() {
			return keepParameters;
		}
		
		public Content keepParameters( boolean keepParamters ) {
			this.keepParameters = keepParamters;
			return this;
		}
		
		public String encrypt( String secret ) {
			return encryptContent( this, secret );
		}
	}
	
	public static SecretKeySpec makeKey( String secret ) throws Exception {
		byte[] key = secret.getBytes( "UTF-8");
		MessageDigest sha = MessageDigest.getInstance("SHA-1");
		key = sha.digest(key);
		key = Arrays.copyOf(key, 16);
		return new SecretKeySpec(key, "AES");
	}
	
	
	
	public static String encrypt(final String strToEncrypt, final String secret) {
		try {
		      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		      cipher.init(Cipher.ENCRYPT_MODE, makeKey( secret ));
		      return Base64.getEncoder()
		        .encodeToString(cipher.doFinal(strToEncrypt.getBytes("UTF-8")));
		} catch( Exception e ) {
			log.error( "Encryption failed", e );
			return "";
		}
	}
	
	public static String decrypt( final String strToDecrypt, final String secret ) {
		try {
		      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		      cipher.init(Cipher.DECRYPT_MODE, makeKey( secret ));

		      return new String( cipher.doFinal( Base64.getDecoder().decode(strToDecrypt )),"UTF-8") ;
		} catch( Exception e ) {
			log.error( "Failed to decrypt", e );
			return "";
		}
	}
	 
	public static JsonNode decryptJson( final String strToDecrypt, final String secret ) {
		try {
			return Jackson.om().readTree( decrypt( strToDecrypt, secret ));
		} catch( Exception e ) {
			log.error( "Invalid json");
			return null;
		}
			
	}
	
	public static String encrypt( final JsonNode json, final String secret ) {
		try {
			String jsonString = Jackson.om().writerWithDefaultPrettyPrinter().writeValueAsString(json);
			return encrypt( jsonString, secret );
		} catch( Exception e ) {
			// cant see how this can happen
			log.error( "Invalid json", e );
			return null;
		}
	}

	public static Content decryptContent(  final String strToDecrypt, final String secret ) {
		try {
			return Jackson.om().treeToValue( decryptJson( strToDecrypt, secret), Content.class );
		} catch( Exception e ) {
			log.error( "Invalid Content in token", e );
			return null;
		}
	}
	
	public static String encryptContent( final Content content, final String secret ) {
		try {
			return encrypt( Jackson.om().writeValueAsString(content), secret );
		} catch (Exception e ) {
			log.error( "Cant serialize token ... ", e );
			return "";
		}
	}
}

