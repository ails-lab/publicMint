package gr.ntua.ivml.mint.api.handlers;
import static gr.ntua.ivml.mint.api.RequestHandlerUtil.grab;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.log4j.Logger;

import gr.ntua.ivml.mint.api.ParameterException;
import gr.ntua.ivml.mint.api.RequestHandlerUtil;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.f.ThrowingConsumer;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic.Access;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Item;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.projects.with.model.With;
import gr.ntua.ivml.mint.util.Jackson;

/*
 * All the API calls that we want to support for translation
 */

public class DatasetHandlers {
	// get the param json the user dataset and initiate the process
	// the running translation should be interruptible and have a status
	
	public static final Logger log = Logger.getLogger( DatasetHandlers.class );
	
	public static List<String> supportedDatasetFormats = Arrays.asList( "with", "xml");
	
	public static void downloadDatasetTarball( HttpServletRequest request, HttpServletResponse response ) {

		RequestHandlerUtil.errorWrap( 
			(req, resp) -> {
				Dataset ds = RequestHandlerUtil.accessDataset(req, 3, Access.READ);
				
				Optional<String> formatOpt = RequestHandlerUtil.getOptUnique(req, "format");
				RequestHandlerUtil.check( ()->supportedDatasetFormats.contains( formatOpt.orElse("with")), "Format not supported");
				
				String format = formatOpt.orElse("with");
				if( format.equals( "with"))
					withExport( ds, resp );
				else if( format.equals( "xml"))
					xmlExport( ds, resp);
				else 
					// should not happen
					log.error("Wrong format not caught");
				
			}).handle(request, response);

	}

	private static void xmlExport( Dataset ds, HttpServletResponse resp ) {
		
		Iterator<Item> it = ds.getItemIterator(false);
		ThrowingConsumer<OutputStream> dataProducer = os-> {
			Item.exportItemsTarball(os, it);
			os.close();
		};
		
		RequestHandlerUtil.streamDownload("x-tar", "Dataset_#"+ds.getDbID()+".tar.gz", dataProducer, false, resp);
	}
	
	private static void withExport( Dataset ds, HttpServletResponse resp ) {
		ThrowingConsumer<OutputStream> downloadProducer = (OutputStream os ) -> {
			
			String path = "Dataset #" + ds.getDbID() + "/";

			final TarArchiveOutputStream tos = new TarArchiveOutputStream(os);

			//currently only one format supported, nothing to choose from
			Consumer<Item> dumpItem = (item) -> {
				archiveItemToWith(tos, path, item);
			};
			
			try {
				tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
				tos.putArchiveEntry(new TarArchiveEntry( path ));
				tos.closeArchiveEntry();

				int itemCount = ds.getItemCount();
				int itemTotal = 0;
				while( itemTotal < itemCount ) {
					List<Item> batch =  ds.getItems(itemTotal, 100);
					for (Item item: batch ) {
						dumpItem.accept(item);
					}
					itemTotal += 100;
					tos.flush();
				}
				tos.finish();
			} catch( Exception e ) {
				log.error( "", e );
				
			} finally {
				try {
					if( tos != null ) tos.close();
					else os.close();
				} catch( Exception e ) {
					log.error( "", e );
				}
			}
		};
		
		RequestHandlerUtil.streamDownload("application/x-tar", "Dataset_"+ds.getDbID()+".tgz", downloadProducer, true, resp );
	}
	
	public static void downloadItemXml( HttpServletRequest request, HttpServletResponse response ) {

		RequestHandlerUtil.errorWrap( 
			(req, resp) -> {
				// /api/item/download? itemId = xxx
				User u = grab(()-> (User) request.getAttribute("user"), "No User logged in");
				int itemId = RequestHandlerUtil.getInt(req, "itemId");
				Item item = grab( ()-> DB.getItemDAO().getById( (long) itemId, false), "Item not found" );
				RequestHandlerUtil.accessDataset(item.getDatasetId().intValue(), u, Access.READ);
				
				String format = RequestHandlerUtil.getOptUnique(req, "format").orElse("xml");
				if( "xml".equals(format)) {
					RequestHandlerUtil.download("text/xml", "Item_"+itemId+".xml"
							, outStream-> {
								outStream.write(item.getXmlBytes());
								outStream.close();
							}, false, resp );					
				} else {
					ParameterException.error( "Format '" + format + "' not supported");
				}
			}).handle(request, response);

	}


	
	private static void write( TarArchiveOutputStream tos, byte[] data, String name) throws IOException  {
		TarArchiveEntry entry = new TarArchiveEntry(name);
		entry.setSize((long) data.length );
		tos.putArchiveEntry(entry);
		tos.write(data, 0, data.length);
		tos.closeArchiveEntry();
	}	

	private static void archiveItemToWith( TarArchiveOutputStream tos, String path, Item item) {
		try{
			With  w = new With( item.getDocument());
			byte[] data = Jackson.om().writeValueAsBytes(w);
			write(tos, data,path + "Item_" + item.getDbID() + ".json");
		} catch( Exception e ) {
			log.error( "Failed to archive item ", e );
		}
	}
	
}

