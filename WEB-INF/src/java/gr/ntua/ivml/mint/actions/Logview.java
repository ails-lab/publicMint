package gr.ntua.ivml.mint.actions;
import org.apache.log4j.Logger;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.convention.annotation.Results;

import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.persistent.DataUpload;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.DatasetLog;
import gr.ntua.ivml.mint.persistent.Transformation;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.JacksonBuilder;
import gr.ntua.ivml.mint.util.StringUtils;


@Results({
	@Result(name="error", location="error.jsp"),
	@Result(name="access", location="accessViolation.jsp" ),
	@Result(name="success", location="logview.jsp")
})



public class Logview extends GeneralAction {

	protected final Logger log = Logger.getLogger(getClass());

	private Dataset dataset;
	private long datasetId;
	private ObjectNode json;
	
	
	public void setDatasetId(String id) {
		datasetId = -1l;
		try {
			datasetId = Long.parseLong(id);
		} catch( Exception e) {}
	}
	
	public String getDatatsetId() {
		return Long.toString( datasetId );
	}
	
	public Dataset getDataset() {
		if( dataset == null ) {
			dataset = DB.getDatasetDAO().getById(datasetId, false);
		}
		return dataset;
	}
	
	private ObjectNode fromLog( DatasetLog dl ) {
		return JacksonBuilder.obj()
				.put( "msg", dl.getMessage() )
				.put( "detail", dl.getDetail().replaceAll("\\n", "<br/>"))
			.put( "date", dl.getEntryTime().toString())
			.put( "nicedate", StringUtils.prettyTime(dl.getEntryTime()))
			.put( "dbID", dl.getDbID()).getObj();
	}
	
	private ObjectNode logsFromDataset( Dataset ds ) {
		String title = ds.getName();
		if( ds instanceof Transformation ) {
			Transformation tr = (Transformation) ds;
			title = tr.getTargetName();
		} else if( ds instanceof DataUpload ) {
			DataUpload du = (DataUpload ) ds;
			title = "Data upload " + du.getName();
		}
		JacksonBuilder jds = JacksonBuilder.obj()
			.put( "title", title )
			.put( "dbID", ds.getDbID());
		
		// the logs go into an Array
		
		for( DatasetLog dl: ds.getLogs())
			jds.withArray( "logs" ).append( fromLog( dl ));
		
		// dependent datasets go into the tail
		for( Dataset dsd: ds.getDirectlyDerived())
			jds.withArray( "tail" ).append( logsFromDataset( dsd ));
		
		return jds.getObj();
	}
	
	public String getJson() {
		try {
			if( json == null ) {
				json = logsFromDataset( getDataset());
			}
			return Jackson.om().writerWithDefaultPrettyPrinter().writeValueAsString(json);
		} catch( Exception e ) {
			log.error( "Json problem", e );
		}
		return "Json trouble";
	}
		
	@Action(value="Logview")
	public String execute() throws Exception {
		
		Dataset ds = getDataset();
		if( ds == null ) return ERROR;
		// check access
		
		if( ! ( user.can("view data", ds.getOrganization()) ||
				user.sharesProject(ds.getOrigin()))) return "access";
		
		
		return SUCCESS;
	}
}
	  
