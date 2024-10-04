package gr.ntua.ivml.mint.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;
/**
 * Write your objects one by one into this, they get stored serialized onto disc
 * When finished read them out one by one.
 * 
 * This class is not for interleaved read and write.
 * 
 * @param <T>
 */
public class CompressedObjectBuffer<T> {
	private File tmpFile;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	
	private boolean isWriting = true;
	
	
	private static final Logger log = Logger.getLogger(CompressedObjectBuffer.class );
	
	public CompressedObjectBuffer() {
		try {
			tmpFile = File.createTempFile("Annotations", ".gzip");
			out = new ObjectOutputStream(
				new GZIPOutputStream(
						new FileOutputStream( tmpFile )));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.error("",e  );
		}
	}
	
	public synchronized void write( T obj ) throws Exception {
		if( !isWriting ) throw new Exception( "Not writing any more" );
		if( out == null ) throw  new Exception( "Open failed, not usable");
		out.writeObject(obj);
	}
	
	public synchronized void flush() throws Exception  {
		out.flush();
	}
	
	public T read() throws Exception {
		if( isWriting ) {
			switchToReadMode();
		}
		
		if( in == null ) return null;
		
		T res = (T) in.readObject();
		if( res == null ) {
			in.close();
			in = null;
		}
		return res;
	}
	
	private void switchToReadMode() throws Exception {
		if( out != null ) {
			out.writeObject(null);
			out.flush();
			out.close();
			out = null;
		}
		
		in = new ObjectInputStream( 
			new GZIPInputStream ( 
				new FileInputStream( tmpFile )));
		
		isWriting = false;		
	}
	
	public void close() throws Exception {
		if( out !=null ) out.close();
		if( in != null ) in.close();
		tmpFile.delete();
	}
}
