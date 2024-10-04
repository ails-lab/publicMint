
class AtomicFilter {
	letter = null;
	function = null; //  "smaller", "greater", "greaterOrEqual", "equals", "matches One", "contains One", "empty", "set", "replace"
	fieldName = null; // as delivered from enrichment
	values = []; // strings, regexps numbers
	
	negate=false;
	ignoreCase=true;
}

class CombineFilter {
	letter=null; // its id
	function=null; // function or, and, if..then
	values=[]; // the arguments  if..then last argument is then, letters can be preceded with - for not
}

class ChainFilter {
	chainType=null; // accept, reject, modify, accept all, reject all
	letter=null; // the filter to use here
}

Vue.component('annotation-filter-component', {
  	template: '#annotation-filter-component',
	data: function() {
		return {
			// filter editors
			atomicFilter:null, 
			combineFilter: null,
			chainLink:null, 

			// filter store
			atomicFilters:{},
			combineFilters:{},
			filterChain:[],

			toggleInfo1:false,

			toggle: {
				info1:false,
				info2:false,
				info3:false
			},

			nextLetter:0,
			usedFilters:{},


		};
	},
	// the enrichment needs basic stats
	props: ["enrichment", "datasetId"],
	created: async function() {
	    this.letters = "abcdefghijklmnopqrstuvwxyz";
	    // this.enrichment = this.testEnrichment();
	    // this.testFilter();
	    // this.updateUsedFilters();
	    this.atomicFilter = null;
	    this.combineFilter= null;
	    this.filterChain = [{ "type":"accept" }];
		this.cache = {};
		if( this.enrichment ) {
			this.cache[this.enrichment.dbID] = {};
		}
	},
	mounted: function() {
	},
	watch: {
		enrichment( newVal, oldVal ) {
			if( newVal && this.cache.hasOwnProperty( newVal.dbID )) {
				let cache = this.cache[newVal.dbID ]
				this.atomicFilters = this.deepCopy( cache.atomicFilters );
				this.combineFilters = this.deepCopy( cache.combineFilters );
				this.nextLetter =  cache.nextLetter ;
				this.filterChain = this.deepCopy( cache.filterChain );
			} else if( newVal ) {
				this.cache[newVal.dbID] = {};
				this.atomicFilters = {};
				this.combineFilters = {};
				this.filterChain =  [{ "type":"accept" }];
				this.nextLetter = null;
				this.atomicFilter = null;
				this.combineFilter= null;		
			}
		}
	},
	methods: {
		// for testing, we dont have stats yet
	    testEnrichment() {
		return {
		    "stats": {
			"byField": {
			    "scope": {
				"columns":["scope","count"],
				"values":[["dc:format", "dce:medium", "dc:subject", "<empty>"][2,2,2,1]]
			    },
			    "targetField": {
				"columns":["targetField","count"],
				"values":[["dc:description", "dc:title", "dc:format"][2,1,1]]
			    },
			    "review": {
				"columns":["review","count"],
				"values":[["accepted", "<empty>"][2,1]]
			    },
			    "uri": {
				"columns":["uri","count"],
				"values":[["http://thesaurus.euscreen.eu/EUscreenXL/v1/388", "http://thesaurus.euscreen.eu/EUscreenXL/v1/304",
					   "http://thesaurus.euscreen.eu/EUscreenXL/v1/290"][1,1,1]]
			    },
			}
		    }
		}
	    },

	    testFilter() {
		this.atomicFilters = {
		    "a" : {
			"letter": "a",
			"functionName":"empty",
			"fieldName":"scope",
			"negate":true
		    },
		    "b" : {
			"letter": "b",
			"functionName": "matches",
			"ignoreCase":true,
			"fieldName":"uri",
			"values": [ ".*388$" ],
		    }
		};
		this.combineFilters = {
		    "c" : {
			"letter": "c",
			"functionName": "or",
			"values": [
			    {
				"negate":true,
				"letter":"b"
			    },
			    {
				"letter":"a"
			    }
			]
		    },
		    "f" : {
			"letter": "f",
			"functionName":"and",
			"negate":true,
			"values": [
			    {
				"letter":"c"
			    },
			    {
				"letter":"a"
			    },
			    {
				"letter":"b",
				"negate":true,
			    }
			]
		    }
		};
		this.filterChain = [
		    {
			type:"accept",
			value:"a"
		    },
		    {
			type:"modify",
			value:"c"
		    },
		    {
			type:"reject",
			value:null,
		    }
		]
		this.nextLetter = 6;
	    },

	    editAtomicFilter( af ) {
			this.atomicFilter = this.deepCopy( af );
	    },

		saveAtomicEditor( af ) {
			if( af.letter ==null ) {
				af.letter = this.letters.substring( this.nextLetter, this.nextLetter+1 );
				this.nextLetter += 1;
			}
			Vue.set( this.atomicFilters, af.letter, this.deepCopy( af ));
			this.updateUsedFilters();
			this.atomicFilter = null;
		},

		// is there a circle with existing combine and this one ?
		hasCombineCircle( cf ) {
			if( cf.letter == null ) { return false; }
			let seenLetters = { [cf.letter] : true };
			let toDoLetters = [ cf.letter ];
			while( toDoLetters.length >  0) {
				let currentLetter = toDoLetters.shift();
				let currentFilter = this.combineFilters[currentLetter];
				if( currentFilter == null ) { continue; }
				let vals = currentFilter.values;
				for( let val of vals ) {
					// circle, 
					if( seenLetters[val.letter] ) { return true; }
				}
			}
		},
		// a String rep for this atomif function 
		atomicFunctionSummaryString( af ) {
			let res = (af.negate ? "not ":"") + af.functionName

			if( af.ignoreCase && ["equals","contains", "matches"].includes( af.functionName )) {
				res += "_ignoreCase";
			}
			res += "(";
			res += af.fieldName;
			if( af.functionName == "empty" ) return res +")";
			for( let val of af.values ) {
				res += ", \""+val+"\"";
			}
			res += ")";
			return res;
		},

		filterChainChange() {
			let cache = this.cache[this.enrichment.dbID];
			cache.filterChain = this.deepCopy( this.filterChain )
			cache.atomicFilters = this.deepCopy( this.atomicFilters )
			cache.combineFilters = this.deepCopy( this.combineFilters )
			cache.nextLetter = this.nextLetter;
		},

		editCombineFilter( cf ) {
			this.combineFilter = this.deepCopy( cf );
		},


		saveCombineEditor( cf ) {
			if( this.hasCombineCircle( cf )) {
				//warn and dont store
			}
			if( cf.letter == null  ) {
				cf.letter = this.letters.substring( this.nextLetter, this.nextLetter+1 );
				this.nextLetter += 1;
			}
			Vue.set( this.combineFilters, cf.letter, this.deepCopy( cf )) ;
			this.updateUsedFilters();
			this.combineFilter = null;
		},

		combineSummaryString( cf ) {
			let res = (cf.negate ? "not ":"") + cf.functionName+"(";
			res += 
				cf.values.map( v => 
					( v.negate ? "not ":"") + v.letter
				).join( ", ");
			
			return res +")";
		},

		removeFilter( f ) {
			if( this.atomicFilters[f.letter] != null ) {
				Vue.delete( this.atomicFilters, f.letter );
			}
			if( this.combineFilters[f.letter] != null ) {
				Vue.delete( this.combineFilters, f.letter );
			}
			this.updateUsedFilters();
		},

		removeFilterChain( idx ) {
			this.filterChain.splice( idx, 1 );
			this.updateUsedFilters();
		},

		deepCopy( a ) {
			if( typeof( window.structuredClone ) == "function") {
				return window.structuredClone( a );
			} else {
				return JSON.parse( JSON.stringyfy( a  ));
			}
		},

		submitOrTest( submit ) {
		    let self = this;
		    console.log( window.location );
		    let params = { "enrichmentId": self.enrichment.dbID }
		    if( submit ) { params.datasetId = self.datasetId }
		    const url = new URL( "api/annotation/apply", window.location );
		    url.search =  new URLSearchParams(params).toString();

		    let popup= null;
		    if( ! submit ) {
			popup =  Mint2.dialog("Testing the filter");
		    }
			
		    $.ajax({
			url: url.href,
			contentType: "application/json",
			data: JSON.stringify( {
			    atomicFilters:self.atomicFilters,
			    combineFilters:self.combineFilters,
			    filterChain:self.filterChain
			}),

			type: "POST",
			error: function(resp) {
			    if( popup != null ) {
				popup.dialog( "close" );				
			    }
			    Mint2.dialog( "There was a problem" );
			},
			success: function(resp) {
			    if( popup != null ) {
				popup.dialog( "close" );				
			    }
			    if( submit ) {
				Mint2.dialog( "Enrichment of dataset queued" );
				// probably close the panel?
				
			    } else {
				Mint2.dialog( "Filter result <p/><p/>" + Mint2.jsonToList( resp  ).html());
			    }
			}
		    });
		},
	    
		availableFilters() {
			return [...Object.keys( this.atomicFilters), ...Object.keys( this.combineFilters)];
		},

		updateUsedFilters() {
			this.usedFilters = {};
			for( let filter of Object.values( this.combineFilters )) {
				for( let val of filter.values ) {
					// all argument filter letters are used and cannot be deleted
					Vue.set( this.usedFilters, val.letter, true);					
				}
			}
			for( let filter of this.filterChain) {
				Vue.set( this.usedFilters, filter.letter, true);					
			}
			this.filterChainChange();
		}

	}
});

