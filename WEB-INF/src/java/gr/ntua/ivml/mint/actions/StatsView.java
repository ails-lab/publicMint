package gr.ntua.ivml.mint.actions;

import org.apache.log4j.Logger;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.convention.annotation.Results;

import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.util.JSStatsTree;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gr.ntua.ivml.mint.util.Jackson;

@Results({
	  @Result(name="error", location="json.jsp"),
	  @Result(name="success", location="json.jsp")
	})


public class StatsView extends GeneralAction {

	protected final static Logger log = Logger.getLogger(StatsView.class);

	String name;
	Dataset dataset;
	public ArrayNode json;
	private long datasetId;
	
	public String getName() {
		return this.dataset.getName();
	}
	public long getDatasetId() {
		return this.datasetId;
		
	}
	
	public void setDatasetId(long dtid){
		this.datasetId=dtid;
		
	}
	
	public ArrayNode getJson(){
		this.json=getArrayNode();
		return this.json;
	}
	
	public ArrayNode getArrayNode(){
			try{
				JSStatsTree stats=new JSStatsTree();
				this.json=stats.getStatisticsJson(this.dataset);
		} catch( Exception e ) {
			json.add(Jackson.om().createObjectNode().put( "error", e.getMessage()));
			log.error( "No values", e );
		}
		return this.json;
	}

	@Action(value="StatsView")
	public String execute() throws Exception {
		this.dataset=DB.getDatasetDAO().getById(this.getDatasetId(), false);
		getArrayNode();
		return SUCCESS;
	}

}
