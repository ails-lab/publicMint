package gr.ntua.ivml.mint.view;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.persistent.AnnotatedDataset;
import gr.ntua.ivml.mint.persistent.DataUpload;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Organization;
import gr.ntua.ivml.mint.persistent.Transformation;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.Label;
import gr.ntua.ivml.mint.util.StringUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class Import {
	public static final Logger log = Logger.getLogger( Import.class );
	
	private Dataset du;
	private String message="";
	private String status="UNKNOWN";
	private String formattedMessage="";
	private String statusIcon="";
	
  
    public boolean isReadOnly(){
    	return(du.isPublished());
    }
	
	public Dataset getDu(){
		return du;
	}
	
	public Organization getOrg(){
		return this.du.getOrganization();
	}
	
	public int getOrgFolderNum(){
		Organization o=getOrg();
		List f=new ArrayList(o.getFolders());
		return (getOrg().getFolders().size());
	}
	
	public static class Download {
		String title;
		String url;
		
		public Download( String title, String url ) {
			this.title = title;
			this.url = url;
		}
		public String getTitle() {
			return title;
		}
		public String getUrl() {
			return url;
		}
	}
	
	public Import( Dataset du ) {
			this.du = du;	
		   // trans=this.getTransformation();
			setStatus();
			setStatusIcon();
			setMessage();
	}
		
	public String getSchema() {
		if( du == null ) return "";
		if( du.getSchema() == null ) return "";
		return du.getSchema().getName();
	}
	
	public List<Download> getDownloads() {
		List<Download> result = new ArrayList<Download>();
		try {
			String baseUrl = "Download?datasetId=";
			if( du instanceof DataUpload && du.getData() != null) 
				result.add( new Download( "Imported data", baseUrl+du.getDbID() ));
			else if( du instanceof Transformation) {
				Transformation tr=(Transformation)du;
				if( tr.getData() != null ) 
					result.add( new Download( "Valid items from " + tr.getTargetName(), baseUrl + du.getDbID() ));
				if( tr.getInvalid() != null ) 
					result.add( new Download( "Invalid items from " + tr.getTargetName(), baseUrl + du.getDbID() +"&invalid=true" ));
			}
			else if (du instanceof AnnotatedDataset) {
				AnnotatedDataset ads = (AnnotatedDataset) du;
				String annDownUrl = "DownloadAnnotated?datasetId=";
				result.add(new Download("Annotated Transformation " + 
				ads.getName(), annDownUrl + ads.getDbID())); 
			}
		} catch( Exception e ) {
			log.error( "Getter failed", e );
		}
		return result;
	}

	public List<Label> getLabels() {
		List<String> list = new ArrayList<String>(du.getFolders());
		List<Label> labels = list.stream()
				.map(l -> new Label(l)).collect(Collectors.toList());
		return labels;
	}

	public int getFolderNum(){
		return du.getFolders().size();
	}
	
	public boolean isImported() {
		
		return ( du.isOk());
	}
	
   public boolean hasStats() {
		
		return ( du.getStatisticStatus().equals(Dataset.STATS_OK));
	}

	public boolean isTransformation() {
		if(du instanceof Transformation)
		  return true;
		else return false;
		
	}
	
	public boolean isAnnotatedDataset() {
		if(du instanceof AnnotatedDataset)
		  return true;
		else return false;
		
	}

	public boolean isTransfOrAnnotatedWithTransfParent() {
		if (du instanceof Transformation) {
			Transformation t = (Transformation) du;
			if (t.getAnnotatedDataset() == null)
				return true;
			else return false;
		}
		else if (du instanceof AnnotatedDataset){	
			AnnotatedDataset ad = (AnnotatedDataset) du;
			if (ad.getParentDataset() instanceof Transformation) {
				return true;
			}
			else return false;
		}
		else
			return false;
	}
	
	
	public boolean isItemized(){
		if (du.getItemizerStatus().equals(Dataset.ITEMS_OK)){
			return true;
		} else 
			return false;
	}
	
	public boolean isTransformed() {
   //check if any of the transformations created items
		try{
			List<Transformation> tr = du.getTransformations();
			boolean istr=false;
			for(Transformation t:tr){
				
					if( t.isOk()){
						istr=true;
						return istr;}
				
			}
			return istr;
		}catch (Exception e){
			log.error("isTransformed threw ", e );
		}
		return false;
	}
	
	
	public boolean isPublished() {
		return du.isPublished();
	}
	
	
	public boolean isProcessing(){
		boolean process=du.isProcessing();
		for( Dataset ds : du.getDerived())
			process |= ds.isProcessing();
		return process;
	}
			
	

	
	public long getUploader(){
		return du.getCreator().getDbID();
	}
	
	public String getName() {
		return du.getName();
		
	}
	
	public String getShortName() {
		String temp=du.getName();
		if(du instanceof gr.ntua.ivml.mint.persistent.DataUpload){
			temp=((DataUpload)du).getOriginalFilename();
			
		}
		else if (du instanceof Transformation){
			temp=((Transformation)du).getTargetName()+ " Transformation"+StringUtils.prettyTime(((Transformation)du).getCreated());
		}
		return StringUtils.shorten( temp,
				14,"..",14 ) ;
	}
	
	
	public String getSize() {
		if( du.getData() != null )
			return String.valueOf(du.getData().getLength());
		else
			return "";
	}
	
    public String getFormattedMessage(){
		this.formattedMessage=this.getMessage();
    	return this.formattedMessage;		
	}
    
    public boolean canDownload( User u ) {
    	return u.can( "download", du );
    }
	
	
	public String getDate() {
		Date d = du.getCreated();
		if( d == null ) return "";
		else
		return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(d);
	}
	
	
	// TODO: what is the right status?
	public String getStatus(){
		 //return du.getLoadingStatus();
		
		return this.status;
		
	}
	
	public String getMessage() {
		//return du.getLastLog().getMessage();
		return message;
	}
	
	
	
	public String getStatusIcon(){
		
		return this.statusIcon;
	}

	public List<Transformation> getTransformations() {
		return du.getTransformations();
	}

	public ObjectNode getDatasetJson (Dataset ds) {
		ObjectNode result = Jackson.om().createObjectNode();
		Import i = new Import(ds);
		result.put("id", i.getDbID());
		result.put("name", i.getName());
		result.put("creator", i.getCreator());
		result.put("created", i.getCreated().toString());
		result.put("numOfItems", i.getNoOfItems());
		result.put("isTransfOrAnnotatedWithTransfParent", i.isTransfOrAnnotatedWithTransfParent());
		result.put("status", i.getStatus());
		result.put("schema", i.getSchema());
		result.put("message", i.getMessage());
		result.put("isProccessing", i.isProcessing());
		result.put("invalidItems", i.getDu().getInvalidItemCount());
		result.put("downloads", Jackson.om().valueToTree(i.getDownloads()));
		result.put("statusIcon", i.getStatusIcon());
		result.put( "organizationId", ds.getOrganization().getDbID());
	
		if (ds instanceof Transformation) {
			Transformation t = (Transformation) ds;
			if (t.getMapping() != null) {
				result.put("mappingId", t.getMapping().getDbID());
				result.put("mappingName", t.getMapping().getName());
			}
			if( !StringUtils.empty( t.getReport())) 
				result.put("report", t.getReport());
		}
		Collection<Dataset> children = ds.getDirectlyDerived();
		if (children != null && children.size() > 0) {
			ArrayNode kids = Jackson.om().createArrayNode();
			result.put("children", kids);
			for (Dataset d : children) {
				kids.add(getDatasetJson(d));
			}
		}
		return result;
	}
	
	public void setMessage(){
		String dtype="Data Upload";
		if(du instanceof Transformation){
			dtype="Transformation";
		} else if (du instanceof AnnotatedDataset)
			dtype="AnnotatedDataset";
		if(this.isProcessing())
		    this.message=dtype+" processing";
		else if(du.isOk()){
			 this.message=dtype+" successfull";
			 if(du instanceof DataUpload){
					if(this.isTransformed()){
						this.message+=". Successfully transformed.";				    
					}
					else if(du.getTransformations().size()>0){
						this.message+=". Transformation unsuccessfull.";
					}
					if(this.isPublished()){
						this.message+=". Publication successfull.";
					}
			   }
			   else if(du instanceof Transformation ){
				   if(((Transformation) du).isStale()){
					   this.message="Transformation is stale. Please retransform using updated mappings.";
				   }
			   }
			   else if(du instanceof AnnotatedDataset){
				   if(((AnnotatedDataset) du).isStale()){
					   this.message="Annotated transformation is stale. Please retransform using updated mappings.";
				   }
			   }
			
			}
		
		else if(du.isFailed()) {
			if(du.getItemizerStatus().equals( Dataset.ITEMS_FAILED)) {
				this.message="Itemization failed";
			} else if(du.getSchemaStatus().equals( Dataset.SCHEMA_FAILED )) {
				this.message="Schema validation failed";
			} else {
				this.message=dtype+" process failed";
			}
		}
	}
	
	
	public boolean isOk(){
		return (du.isOk());
	}
	
	public void setStatus(){
		if(this.isProcessing()){
			this.status="IN PROGRESS";
			
	     }
		else if(du.isOk()){
			this.status="OK";
				
			
			}
		else if(du.isFailed())
			this.status="FAILED";
	}
	
	public void setStatusIcon(){
		
		if(this.isProcessing()){
			this.statusIcon="images/loader.gif";}
		else if(du.isOk()){
			this.statusIcon="images/ok.png";
				
			   if(du instanceof DataUpload){
				    if(du.isPublished()){
//				    	System.out.println("du.getPublishedItemCount():"+du.getPublishedItemCount());
//				    	System.out.println("du.getItemCount():"+du.getItemCount());
//				    	if(du.getPublishedItemCount() < du.getItemCount())
				    		this.statusIcon="images/published.png";
//				    	else
//				    		this.statusIcon="images/published_warning.png";
				    }
				    else if(this.isTransformed()){
					
						this.statusIcon="images/okblue.png";}
					
					else if(du.getTransformations().size()>0){
						this.statusIcon="images/tranerror.png";
						
					}
			   }
			   else if(du instanceof Transformation){
				   if(((Transformation) du).isStale()){
					   this.statusIcon="images/redflag.png";
				   }
			   }
			   else if(du instanceof AnnotatedDataset){
				   if(((AnnotatedDataset) du).isStale()){
					   this.statusIcon="images/redflag.png";
				   }
			   }	
			
			}
		else if(du.isFailed()){
			this.statusIcon="images/problem.png";
		}
	
		
	}
	
	public long getDbID() {
		return du.getDbID();
	}
	
	public int getNoOfItems() {
		
		   if(this.isItemized())	
			return (du.getItemCount());
		   else return 0;
		}
	
	public Date getCreated(){
		return (du.getCreated());
	}
	
	public String getCreator(){
		return (du.getCreator().getLogin());
	}
	
	public int getNoOfFiles() {
		
	   if(du instanceof DataUpload)	
		return ((DataUpload)du).getNoOfFiles();
	   else return 0;
	}
	
	public String getSizeDescription() {
		
		long size = du.getData().getLength();
		StringBuffer msg = new StringBuffer();
		msg.append( StringUtils.humanNumber(size));
		if(du instanceof DataUpload){
		if( ((DataUpload)du).getNoOfFiles() > 1 ) {
			if( msg.length()>0)
				msg.append( " in " );
			msg.append( ((DataUpload)du).getNoOfFiles());
			if(getOai().length()>0){
			  msg.append(" responses");
			}else{
			  msg.append(" files");				
			}			
		}}
		return msg.toString();
	}
	
	public boolean isXml() {
		if(du instanceof DataUpload){
		return  ((DataUpload)du).getStructuralFormat().equals( DataUpload.FORMAT_XML);}
		else return false;
	}
	
	public boolean isCsv() {
		if(du instanceof DataUpload)
			return  ((DataUpload)du).getStructuralFormat().equals( DataUpload.FORMAT_CSV);
		else return false;
			
	}
	
	public boolean isZip() {
		
		if(du instanceof DataUpload){
			return  (((DataUpload)du).getStructuralFormat().equals( DataUpload.FORMAT_ZIP_CSV) ||
			((DataUpload)du).getStructuralFormat().equals( DataUpload.FORMAT_ZIP_XML));}
			else return false;
	}
	
	public boolean isTgz() {
		if(du instanceof DataUpload){
			return  (((DataUpload)du).getStructuralFormat().equals( DataUpload.FORMAT_TGZ_CSV) ||
				 ((DataUpload)du).getStructuralFormat().equals( DataUpload.FORMAT_TGZ_XML));}
		else return false;
	}
	
	public boolean isOai() {
		if(du instanceof DataUpload)
			return  (((DataUpload)du).getUploadMethod().equals(DataUpload.METHOD_OAI));
		else return false;
	}
	
	
	public String getOai() {
		if(du instanceof DataUpload){
		  if( ((DataUpload)du).getUploadMethod().equals(DataUpload.METHOD_OAI)) 
			return ((DataUpload)du).getSourceURL();	
	       else return "";}
		else return "";
	}
	
	
	public boolean isLocked( User u, String sessionId ) {
		return !DB.getLockManager().canAccess( u, sessionId, du );
	}
	
	public boolean isRootDefined(){
		return (du.getItemizerStatus().equals(DataUpload.ITEMS_OK));
		
	}
	
	/**
	 * Is there's a derived Dataset with given schemaName, return the valid item count.
	 * If there is not, return -1
	 * @param schemaName
	 * @return
	 */
	public int getValidBySchemaName( String schemaName ) {
		Dataset derived = du.getBySchemaName(schemaName);
		if( derived == null ) return -1;
		return derived.getValidItemCount();
	}
}
