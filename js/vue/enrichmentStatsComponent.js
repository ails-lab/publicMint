Vue.component('enrichment-stats-component', {
  	template: '#enrichment-stats-component',
	data: function() {
		return {
			selectedStatsName:null,
		};
	},
	props: [ "enrichment" ],
	created: async function() {
	},
	mounted: function() {
		this.createSlick();
	},
	watch: {
		enrichment( oldValue, newValue ) {
			this.selectedStatsName = null
			$("#slickBox").empty()
		}
	},
	computed: {

		stat() {
			if( this.enrichment.stats.byField.hasOwnProperty( this.selectedStatsName )) {
				return this.enrichment.stats.byField[this.selectedStatsName];
			} else {
				if( ! this.enrichment.stats.hasOwnProperty( "other")) {return null;}

				for( let i=0; i<this.enrichment.stats.other.length; i++ ) {
					if(this.enrichment.stats.other[i].title == this.selectedStatsName ) {
						return this.enrichment.stats.other[i];
					}
				}
			}
			return null;
		},

		rows() {
			// title, description, columns, values[][]
			if( this.stat == null ) return null;

			let rows = []
			let values = this.stat.values;
			
			for ( let i=0; i<values[0].length; i ++ ) {
				let row=[]
				for( let column=0; column < values.length; column++ ) {
					row.push( values[column][i])
				}
				rows.push( row )
			}
			return rows;
		},

		slickRows() {
			if( this.stat == null ) return null;

			let rows = []
			let values = this.stat.values;
			
			for ( let i=0; i<values[0].length; i ++ ) {
				let row={}
				for( let column=0; column < values.length; column++ ) {
					row[ this.stat.columns[column]] = values[column][i];
				}
				rows.push( row )
			}
			console.log( rows )
			return rows;
		}
	},
	methods: {
		createSlick() {
			if( this.stat == null ) return ;
			let columns = this.stat.columns.map( (name, pos) => {
				return { "id":pos, "name":name, "field":name };
			});
			console.log( columns )
			let grid = new Slick.Grid( "#slickBox", this.slickRows, columns, {forceFitColumns:true} );
			grid.resizeCanvas();
		},

		availableTitles() {
			let titles = [...Object.keys( this.enrichment.stats.byField )];
			if( this.enrichment.stats.hasOwnProperty( "other")) {
				let others = this.enrichment.stats.other;
				for( let stat of others ) {
					titles.push( stat.title )
				}
			}
			return titles;
		},

	}
});

