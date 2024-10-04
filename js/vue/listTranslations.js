Vue.component('list-translations-component', {
	template: '#list-translations-component',

    data: function () {
		return {
	    	translations: [],
		    start:0,
		    pageSize:4,
		    url:"",
		    // method = "none" for invisible selected radiobutton
		    method:null,
		};
    },
	// org lists all translation for org, dataset only for dataset
    props:["datasetId", "orgId" ],
    created: async function () {
    	this.getTranslations();
    },
    mounted: function() {
    },
	computed: {
		isDataset() { return this.datasetId ? true : false },
		orgName() { return sessionStorage.getItem( "org#"+this.orgId )},
		translationPage() { return this.translations.slice( this.start, this.pageSize )}
	},
    methods: {
    	isVisible: function() {
    		return (document.querySelector( ".translations") != null); 
    	},
    	
    	pageBack: function () {
    		this.start = this.start-this.pageSize;
    		if( this.start < 0 ) this.start=0;
    	},
    	canPageBack: function() {
    		return this.start>0;
    	},
    	canPageForward: function() {
    		return this.start+this.pageSize < translations.length
    	},
    	pageForward: function() {
    		this.start += this.pageSize;
    	},
    	
    	pagingNeeded: function() {
    		return (this.translations.length > this.pageSize );
    	},
    	
    	getTranslations: async function() {
    		let self = this;
    		let data = {};
			if( self.orgId ) {
				data["orgId"] = self.orgId;
			} else {
				data["datasetId"] = self.orgId;
			}
 	       $.ajax({
 		         url: "api/translation/list",
 		         data: data,
 		         type: "GET",
 		         error: function (resp) {
 		           alert("Cant get translations \n" + resp.err );
 		           self.closeTab();
 		         },
 		         success: function (resp) {
 		        	//expecting a json array
 		           	self.translations = resp.slice( 0, resp.length );
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
		
		isRunning: function( translation ) {
			return (translation.status == "RUNNING");
		},    	
    	
		isError: function( translation ) {
			return	( translation.status == "ERROR");
		},
		
		isOk: function( translation ) {
			return	( translation.status == "OK");
		},
		
		
		deleteTranslation: async function( translation, confirm ) {
			let self = this;
			if( confirm ) {
				Mint2.modalDialogWithCancel( "Do you really want to delete this translation?", 
						"Confirm DELETE", ()=> self.deleteTranslation( translation, false ));
			} else {
	 	       $.ajax({
	 		         url: "api/translation/"+ translation.translationId,
	 		         type: "DELETE",
	 		         error: function (resp) {
	 		           alert("Cannot delete translation \n" + resp.err );
	 		         },
	 		         success: function (resp) {
	 		        	 self.getTranslations();
	 		         }
	 		       });
			}
		},
		importPecat() {
			let self = this;
			Mint2.slowCall({
		    	url: "api/translation/pecatReview",
	         	data: {url:this.url},
	         	type: "POST",
		        error: function (resp) {
					Mint2.dialog( Mint2.message( resp.responseJSON.error, Mint2.ERROR), "Cant merge Pecat review" );
					self.url = "";
					self.method = "none";
	         	},
		        success: function (resp) {
					Mint2.dialog( "Review processing queued" );
					self.url = "";
					self.method = "none";
	         	}
	       });

		},
		
		closeDetails() {
			this.viewer = null;
			this.enrichment = null;
		},
		
		shortenString( val, max ) {
			if( val.length > max )
				return val.substring( 0,max-3)+"...";
			else 
				return val;		
		},
      // fields and languages, but not more than 30 chars 		
	  summaryOfTranslation( translation ) {
		  let langs = "";
		  if( translation.defaultLanguage ) {
			  langs = "(" + translation.defaultLanguage + "->en)";
		  } else if( translation.normalizedTags ) {
			  langs = "(" + translation.normalizedTags.map( ({label})=>label).join( ",") + " ->en)";
		  } else {
			  langs = "( conf problem)"
		  }
		  let fields = this.shortenString( translation.fieldNames.join(", "), 30-langs.length);
		  return"[" + fields + "  " + langs + "]";
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
 
