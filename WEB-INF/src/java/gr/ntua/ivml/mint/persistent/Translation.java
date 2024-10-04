package gr.ntua.ivml.mint.persistent;

import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.JacksonBuilder;

public class Translation implements AccessCheckEnabled {

	public static final String QUEUED = "QUEUED";
	public static final String RUNNING = "RUNNING";
	public static final String OK = "OK";
	public static final String FAILED = "FAILED";

	public static final Logger log = Logger.getLogger(Translation.class);

	private long dbID;
	private Organization organization;
	private User creator;
	public List<Integer> projectIds;
	
	// the following fields might disappear during the translation lifetime,
	// we keep the translation anyway!
	private Dataset dataset;
	private Dataset originalDataset;
	private Date startDate, endDate;

	// RUNNING, FAILED, OK
	private String status;
	
	private String jsonConfig, jsonStats;
	
	
	public ObjectNode listView() {
		
		JacksonBuilder jb = JacksonBuilder.obj()
			.put( "translationId", dbID )	
			.put( "status", getStatus())
			.put( "stats", b->{
				try {
					b.set( Jackson.om().readTree( getJsonStats()));
					return true;
				} catch( Exception e ) {
					return false;
				}
			})
			.put( "started", getStartDate().toString())
			.put( "ended", getEndDate().toString())
			.put( "dataset")
				.put( "originalId", getOriginalDataset().getDbID().intValue())
				.put( "id", getDataset().getDbID().intValue())
				.put( "name", getOriginalDataset().getName())
				.up();
		try {
			ArrayNode fieldNames = Jackson.om().createArrayNode();
			ObjectNode conf = (ObjectNode) Jackson.om().readTree( getJsonConfig());
			JsonNode fields = conf.at( "/selectedFields");
		
			if( fields.isArray()) {
				for( JsonNode pathNode: (ArrayNode)fields) {
					XpathHolder path = DB.getXpathHolderDAO().getById( (long)pathNode.asLong(), false);
					fieldNames.add( path.getXpath().replaceAll(".*/([^/]+)(/text\\(\\))?$", "$1"));
				}
			}
			jb
			.put( "fieldNames", fieldNames )
			.put( "defaultLanguage", b-> {
				if( !conf.has( "defaultLanguage")) return false;
				b.set( conf.get( "defaultLanguage"));
				return true;
			})
			.put( "normalizedTags", b-> {
				if( !conf.has("normalizedTags")) return false;
				b.set( conf.get("normalizedTags" ));	
				return true;
			});

		} catch( Exception e ) {
			log.error( "Conf not Json in Translation", e );
		}
		
		return jb.getObj();
	}
		
	
//
//  Getters Setters
//
	

	
	public long getDbID() {
		return dbID;
	}

	public void setDbID(long dbID) {
		this.dbID = dbID;
	}

	public Organization getOrganization() {
		return organization;
	}

	public void setOrganization(Organization organization) {
		this.organization = organization;
	}

	public User getCreator() {
		return creator;
	}

	public void setCreator(User creator) {
		this.creator = creator;
	}

	public List<Integer> getProjectIds() {
		return projectIds;
	}

	public void setProjectIds(List<Integer> projectIds) {
		this.projectIds = projectIds;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public Dataset getOriginalDataset() {
		return originalDataset;
	}

	public void setOriginalDataset(Dataset originalDataset) {
		this.originalDataset = originalDataset;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getJsonConfig() {
		return jsonConfig;
	}

	public void setJsonConfig(String jsonConfig) {
		this.jsonConfig = jsonConfig;
	}

	public String getJsonStats() {
		return jsonStats;
	}

	public void setJsonStats(String jsonStats) {
		this.jsonStats = jsonStats;
	}

	
}

/*

SELECT execute( $$ CREATE TABLE translation (
    translation_id int primary key NOT NULL,
    organization_id integer references organization,
    creator_id integer references users on delete set null,
    project_ids smallint[] default '{}',
	dataset_id integer references dataset on delete set null,
	original_dataset_id integer references dataset on delete set null,
	
	-- when the process started
	start_date timestamp without time zone,
	-- end when it finished
	end_date timestamp without time zone,
	
	
	-- RUNNING, OK, FAILED
    status text,
    
    -- some representation of the input paramters for this job
    json_config text,
    
    -- on OK how many literals of what language were translated, how many language detected ...etc
    json_stats text
  
)  $$ )
*/
