

package gr.ntua.ivml.mint.actions;


import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.util.Config;

import org.apache.log4j.Logger;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.convention.annotation.Results;

@Results({ @Result(name = "error", location = "projectstatistics2.jsp"),//projectstatistics.jsp
		@Result(name = "success", location = "projectstatistics2.jsp") })//projectstatistics.jsp
public class ProjectStatistics extends GeneralAction {

	protected final static Logger log = Logger.getLogger(ProjectStatistics.class);

	long organizationId;
	 
	
	
	public long getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(long organizationId) {
		this.organizationId = organizationId;
	}

	public String getName() {
		
		return Config.get("mint.title");
		
		
	}

	
	@Action(value = "ProjectStatistics")
	public String execute() throws Exception {
		return SUCCESS;
	}

}
