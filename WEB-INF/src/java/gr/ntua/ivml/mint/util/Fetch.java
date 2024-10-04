package gr.ntua.ivml.mint.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.log4j.Logger;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;

public class Fetch {
	private static final Logger log = Logger.getLogger( Fetch.class );
	
	public byte[] data;
	public String name;
	public String contentType;
	public String errMsg;

	static {
		HttpClientConfig conf = HttpClientConfig
			.httpClientConfig()
			// 5min
			// time between two packets
			.setParam( "http.socket.timeout", 300000 )
			// time to respond to connection request
			.setParam( "http.connection.timeout", 300000 );
			RestAssured.config = RestAssuredConfig.config().httpClient(conf);
	}
	
	public static Fetch fetchUrl( String url ) {
		// TODO: timeout needs extension
		Fetch result = new Fetch();
		try {
			
			Response response = RestAssured
					.get( new URL( url ));
			if( response.statusCode() != 200 ) {
				result.errMsg = StringUtils.shortenEnd( response.asString(), 200);
				return result;
			}
			
			result.data = response.asByteArray();
			result.contentType = response.getContentType();
			String contentDisposition = response.getHeader("Content-Disposition");
			if( contentDisposition != null ) {
				// assume this is filename
                String[] parts = contentDisposition.split("=");
                if (parts.length > 1 ) {
                    result.name = parts[1].trim();
                }
			}
			return result;
		} catch( Throwable th ) {
			log.error( "fetch from url failed ", th );
			result.errMsg = th.getMessage();
			return result;
		}	
	}

	public static byte[] gunzip( byte[] input ) {
		try (InputStream is = new GzipCompressorInputStream(
				new ByteArrayInputStream( input ))) {
			return IOUtils.toByteArray( is );
		} catch( Exception e ) {
			log.error( "gunzip failed");
			return new byte[0];
		}
	}
}
