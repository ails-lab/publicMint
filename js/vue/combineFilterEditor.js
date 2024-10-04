Vue.component('combine-filter-editor', {
    template: '#combine-filter-editor',
  data: function() {
      return {
              letter:null,
              functionName:null,
              values:[],
              activeValue:null,
              negate:false,
        }
    },
    props: [ "availableFilterLetters", "filter" ],
	created: async function() {
		this.combineFunctions = [ "and", "or", "if..then" ];
        Object.assign( this, this.filter );
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
            if( this.values.length == 0 ) {
                this.values= [{}];
                this.activeValue=0;
            } else {
                this.activeValue = -1;
            }
		},

        negateValue( idx ) {
            let letter = this.values[idx].letter;
            let negate = this.values[idx].negate;
            this.values.splice( idx, 1, { letter:letter, negate:!negate })
        },

        valueChange( idx ) {
            let letter = this.values[idx].letter;
            let negate = this.values[idx].negate;
            this.values.splice( idx, 1, { letter:letter, negate:negate })
        },

        addValue( index ) {
            this.values.splice( index+1, 1 , {} ); 
            this.activeValue = index+1;
        },

        saveCombineFilter() {
            this.$emit( 'save-combine-editor', this.toData() );
        },

        toData() {
            return {
                letter:this.letter,
                functionName:this.functionName,
                values:[...this.values],
                negate:this.negate,
            }
        },
    }
});

