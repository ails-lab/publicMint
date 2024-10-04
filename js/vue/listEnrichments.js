Vue.component('list-enrichments-component', {
	template: '#list-enrichments-component',

    data: function () {
		return {
	    	    enrichments: [],
		    start:0,
		    pageSize:4,
		    hasMore:false,
			viewer:null,
			selectedEnrichment:null,
		    viewEnrichment:null,
		    viewFilter:null,
		    viewStat:null,
		};
    },
	// if there is a dataset id, than this is for picking and applying an erichment
    props:["datasetId", "orgId"],
    created: async function () {
    	this.getPage();
    },
    mounted: function() {
    },
	computed: {
		isSelection() { let res = (this.datasetId > 0 )
		console.log( res )
		return res   },
		orgName() { return sessionStorage.getItem( "org#"+this.orgId )},
	},
    methods: {
    	isVisible: function() {
    		return (document.querySelector( "#enrichments") != null); 
    	},
    	
    	pageBack: function () {
    		this.start = this.start-this.pageSize;
    		if( this.start < 0 ) this.start=0;
    		this.getPage();
    	},
    	canPageBack: function() {
    		return this.start>0;
    	},
    	canPageForward: function() {
    		return this.hasMore;
    	},
    	pageForward: function() {
    		this.start += this.pageSize;
    		this.getPage();
    	},
    	
    	pagingNeeded: function() {
    		return (this.start>0||this.hasMore);
    	},
    	
    	getPage: async function() {
    		let self = this;
 	       $.ajax({
 		         url: "api/annotation/list",
 		         data: {
 		        	 "start": self.start,
 		        	 "count": self.pageSize+1,
 		        	 "orgId":self.orgId
 		         },
 		         type: "GET",
 		         error: function (resp) {
 		           alert("Cant get annotations \n" + resp.err );
 		           self.closeTab();
 		         },
 		         success: function (resp) {
 		        	//expecting a json array
 		        	self.hasMore = (resp.length>self.pageSize);
 		        	// take only pageSize elements, the last is just to check if there is more coming
 		           	self.enrichments = resp.slice( 0, self.pageSize );
 		         }
 		       });
    	},

    	copyAbsoluteUrlToClipboard: function ( relativeUrl ) {
			const absoluteUrl = new URL(relativeUrl, window.location.href )
			const tempTextArea = document.createElement("textarea");
            tempTextArea.value = absoluteUrl.href;
            document.body.appendChild(tempTextArea);
            tempTextArea.select();
            document.execCommand("copy");
            document.body.removeChild(tempTextArea);
		},

		shareClicked: function( share, event ) {
			this.copyAbsoluteUrlToClipboard( share );
			// some feedback here 
			Mint2.infoPopup( event.target, "Copied to Clipboard");
		},
		
		isRunning: function( enrichment ) {
			return ( ( "status" in enrichment ) && (enrichment.status != "ERROR"));
		},    	
    	
		isError: function( enrichment ) {
			return	( ( "status" in enrichment )&& (enrichment.status == "ERROR"));
		},
		
		isOk: function( enrichment ) {
			return	!( "status" in enrichment );
		},
		
		viewValues( enrichment ) {
			this.selectedEnrichment = enrichment;
			this.viewer = "Values";
		}, 
		
		openFilters( enrichment ) {
			if( this.isSelection ) {
				this.selectedEnrichment = enrichment;
				this.viewer = "Filters";
			}
		},

		viewStats( enrichment ) {
			this.selectedEnrichment = enrichment;
			this.viewer = "Stats";
		}, 
    
		info( enrichment, event ) {
			Mint2.dialog( enrichment.msg, "Progress report "+enrichment.lastUpdate );
		},
		
		deleteEnrichment: async function( enrichment, confirm ) {
			let self = this;
			if( confirm ) {
				Mint2.modalDialogWithCancel( "Do you really want to delete this enrichment?", 
						"Confirm DELETE", ()=> self.deleteEnrichment( enrichment, false ));
			} else {
	 	       $.ajax({
	 		         url: "api/annotation/"+ enrichment.dbID,
	 		         type: "DELETE",
	 		         error: function (resp) {
	 		           alert("Cannot delete annotation \n" + resp.err );
	 		         },
	 		         success: function (resp) {
	 		        	 self.getPage();
	 		         }
	 		       });
			}
		},
		closeDetails() {
			this.viewer = null;
			this.enrichment = null;
		},

      closeTab: function() {
	      let thisPanel = $($(this.$el).closest('div[id^=kp]'))
	      let previousPanelId = 'kp' + (thisPanel[0].id.at(-1) - 1);
	      let previousPanel = $('div[id^='+previousPanelId+']');
	      $K.kaiten('reload', previousPanel);
	      $K.kaiten('remove', thisPanel, false);
	   }
    },
  });
 
