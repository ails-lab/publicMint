package gr.ntua.ivml.mint.api;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.node.ArrayNode;

import gr.ntua.ivml.mint.api.handlers.EuropeanaSandbox;
import gr.ntua.ivml.mint.db.DB;
import gr.ntua.ivml.mint.db.Meta;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic;
import gr.ntua.ivml.mint.persistent.AccessAuthenticationLogic.Access;
import gr.ntua.ivml.mint.persistent.AnnotatedDataset;
import gr.ntua.ivml.mint.persistent.DataUpload;
import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Translation;
import gr.ntua.ivml.mint.persistent.User;
import gr.ntua.ivml.mint.persistent.XmlSchema;
import gr.ntua.ivml.mint.services.ImageAnalysis;
import gr.ntua.ivml.mint.util.Config;
import gr.ntua.ivml.mint.util.Jackson;
import gr.ntua.ivml.mint.util.WebToken;
import gr.ntua.ivml.mint.view.Import;

public class DatasetOptions {

	public static final Logger log = Logger.getLogger( DatasetOptions.class );
	
    public static class Option {
		public String category = "publication";
		public String label;
		public boolean inProgress = false;
		public String url;
        public String kTitle = "";

        public String warning = null;
        public String method = "GET";
        
		// json|htmlNewPanel|htmlReplacePanel|copyLink
		public String response = "panel";
        
        public Option() {}

        public Option(String label, String url, String  kTitle, String response, String category) {
            this.label = label;
            this.url = url;
            this.kTitle = kTitle;
            this.response = response;
            this.category = category;
        }

        public Option(String label, String url, String response, String category) {
            this.label = label;
            this.url = url;
            this.response = response;
            this.category = category;
        }
	};

    public static ArrayNode coreDatasetOptions(Dataset ds, User u) {
    	
        ArrayNode result = Jackson.om().createArrayNode();
    	Access access = AccessAuthenticationLogic.getAccess(ds, u);
    	// no options if you cant read
    	if( access.compareTo( Access.READ) < 0 ) return result;
    	
		
		List<String> projects = Arrays.asList( ds.getOrigin().getProjectNames());
        Import im = new Import(ds);

        Option logView = new Option("Show log", "Logview?datasetId=" + ds.getDbID(), "Dataset Log", "panel", "core");
        result.add(Jackson.om().valueToTree(logView));
        
        boolean userCanModifyData = u.hasRight(User.MODIFY_DATA);
        if (im.isProcessing()) {
            return result;
        }

        if (im.isRootDefined() && im.isItemized()) {
            
            Option showAllItems = new Option("Show all items", "ItemView.action?uploadId="+ds.getDbID(), "panel", "core");
            result.add(Jackson.om().valueToTree(showAllItems));
            if (ds.getInvalidItemCount() > 0) {
                Option showInvalidItems = new Option("Show invalid items", "ItemView.action?uploadId="+ds.getDbID()+"&filter=invalid", "panel", "core");
                result.add(Jackson.om().valueToTree(showInvalidItems));
            }

            if (userCanModifyData) {
            	/*
            	<div title="Mappings" data-load='{"kConnector":"html.page", "url":"MappingSummary.action?uploadId=<s:property value='uploadId'/>&orgId=<s:property value='organizationId'/>&userId=<s:property value='userId'/>", "kTitle":"Mappings" }' class="items navigable">
				<div class="label">Mappings</div>
				<div class="tail"></div>
			</div> */
            	
                Option mapping = new Option("Mappings", "MappingSummary.action?uploadId="+ds.getDbID()+"&orgId=" + ds.getOrganization().getDbID(), "Mappings", "panel", "core");
                result.add(Jackson.om().valueToTree(mapping));

            	
                Option annotate = new Option("Annotate", "Annotator.action?uploadId="+ds.getDbID(), "Annotator", "panel", "core");
                result.add(Jackson.om().valueToTree(annotate));

                Option transform = new Option("Transform", "Transform_input.action?uploadId="+ds.getDbID(), "Transform", "panel", "core");
                result.add(Jackson.om().valueToTree(transform));

                Option enrich = new Option("Enrich", "html/enrichments.html?datasetId="+ds.getDbID()+"&orgId="+ds.getOrganization().getDbID(), "Enrich", "panel", "core");
                result.add(Jackson.om().valueToTree(enrich));

            }
        }

        if (userCanModifyData) {
            if (im.isReadOnly()==false && im.isTransfOrAnnotatedWithTransfParent()==false && im.isTransformed()==false && im.hasStats()==true) {
                String url = "itemLevelLabelRequest?uploadId="+ds.getDbID() + "&transformed=" + im.isTransformed();
                Option defineItems = new Option("Define Items", url, "Define Items", "panel", "core");
                result.add(Jackson.om().valueToTree(defineItems));

            }
        }

        if (im.hasStats()) {
            Option stats = new Option("Statistics", "html/statistics.html?uploadId="+ds.getDbID(), "Dataset Statistics", "panel", "core");

            result.add(Jackson.om().valueToTree(stats));
        }
        
        // not published, user can write and no dependent dataset  
    	if( userCanModifyData 
    		// only allow leafs to be deleted	
    		&& ds.getDirectlyDerived().isEmpty()
    		// only if not published
    		&& !DB.getPublicationRecordDAO().getByPublishedDataset(ds).isPresent() ) {
        	
        	String label = "Delete Transformation";
        	String uri = "api/dataset/"+ds.getDbID();
        	String warning = "You are about to delete this derived version of the dataset!";
        	if( ds instanceof DataUpload ) {
        		label = "Delete Upload";
        		warning = "This dataset will be permanently removed.";
        	}
        	if( ds instanceof AnnotatedDataset) {
        		label = "Reverse Annotations";
        		warning = "The changes in this Annotated Set will be undone!";
        	}
        
            Option delete = new Option(label, uri, "json", "core");
            delete.method = "DELETE";
            delete.warning = warning;
            result.add(Jackson.om().valueToTree(delete));
        }
    	
    	addTranslationOptions( result, ds, u, access );
    	
    	EuropeanaSandbox.addSandboxOptions(result, ds, access);
    	if( ImageAnalysis.canAnalyse(ds)) {
            Option opt = new Option("Image Analysis", "html/image_analysis.html?datasetId="+ds.getDbID(), "Color/Object tagging", "panel", "core");
            result.add(Jackson.om().valueToTree(opt));   		
    	}
    	
    	// a week valid, shared link
    	String downloadToken = WebToken.Content.create()
    		.expires( Duration.ofDays(7))
    		.user( u.getLogin())
    		.url( "/api/dataset/download/"+ds.getDbID())
    		.keepParameters( true )
    		.encrypt(Config.get( "secret"));
    	
    	try {
    		// currently need itemized data
    		if( ds.getItemCount() > 0 ) {
    			// this assumes its called from normal mint home location, the link is relative to the instance
    			Option share = new Option( "Copy Download Link", "api/callByToken?token="+URLEncoder.encode( downloadToken,"UTF-8"), 	
    				"copyLink","core" );
    			result.add( Jackson.om().valueToTree(share));
    		}
    	} catch(Exception e ) {
    		log.error( "",e);
    	}
    		
        return result;
    }
    
    private static void addTranslationOptions( ArrayNode result, Dataset ds, User u, Access accessLevel ) {
    	// existing translations
    	List<Translation> tl = DB.getTranslationDAO().findByOriginalDataset( ds.getOrigin());
    	// this might contain runing or failed Translations
    	if( tl.size() > 0 ) {
            Option translate = new Option("Review Translation", "html/translation_review.html?uploadId="+ds.getDbID(), "Review", "panel", "core");
            result.add(Jackson.om().valueToTree(translate));
    	}

    	if( accessLevel.compareTo( Access.WRITE ) < 0 ) return;
    	
    	// apply translation ... we only want finished ones here
    	boolean apply = false;
    	boolean running = false;
    	for( Translation t: tl ) {
    		if( Translation.OK.equals( t.getStatus()) && ds.getDbID() == t.getDataset().getDbID()) apply=true;
    		if( Translation.RUNNING.equals( t.getStatus())) running=true;
    		if( Translation.QUEUED.equals( t.getStatus())) running=true;
    	}
    		
    	// translate option
        Set<String> eligibleSchemasForTranslation = Config.getCommaDelimitedStringToSet("translation.schemas");
        if( ds.getSchema() != null )
        	if (eligibleSchemasForTranslation.contains(ds.getSchema().getName())) {
        		Option translate = null;
        		if( running ) {
        			translate = new Option("Translation running",null, "Translate", "panel", "core");
        			translate.inProgress = true;
        		} else 
        			translate = new Option("Generate translations", "html/select_translation_target_fields.html?uploadId="+ds.getDbID(), "Translate", "panel", "core");

        		result.add(Jackson.om().valueToTree(translate));

            	if( apply ) 
            		result.add( 
            			Jackson.om().valueToTree(
            				new Option("Apply Translations", "html/translation_apply.html?uploadId="+ds.getDbID(), "Translate", "panel", "core")
            			)
            		);
        	}
        
    }
    
}
