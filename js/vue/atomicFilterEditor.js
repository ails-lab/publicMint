Vue.component('atomic-filter-editor', {
    template: '#atomic-filter-editor',
  data: function() {
      return {
              fieldName:null,
              letter:null,
              functionName:null,
              values:[],
              activeValue:null,
              negate:false,
              ignoreCase:false,
        }
    },
    props: [ "enrichment", "filter" ],
	created: async function() {
		this.atomicFunctions = [ "smaller", "greater", "smallerOrEqual", "equals", "matches", "contains", "empty", "set", "replace", "setByField" ];
        Object.assign( this, this.filter );
        console.log( this );
	},
	mounted: function() {
	},
	watch: {
        filter( oldValue, newValue ) {
            Object.assign( this, newValue );
        }
	},
	methods: {
        valueInit() {
			this.values= [""];
            this.activeValue=0
		},
        
        addValue( index ) {
            this.values.splice( index+1, 1 , '' ); 
            this.activeValue = index+1;
        },

        fieldValues( fieldName ) {
	    if( this.enrichment.stats.byField[fieldName] == null ) {
		return [];
	    }
	    let res = this.enrichment.stats.byField[fieldName].values[0]
		.filter( elem => ! ['<empty>','<other>','<distinct>'].includes( elem ))
	    return res;
	},
        saveAtomicFilter() {
            this.$emit( 'save-atomic-editor', this.toData() );
        },

        toData() {
            return {
                fieldName:this.fieldName,
                letter:this.letter,
                functionName:this.functionName,
                values:[...this.values],
                negate:this.negate,
                ignoreCase:this.ignoreCase,
            }
        },
    }
});

