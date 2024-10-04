Vue.component('enrichment-values-component', {
  	template: '#enrichment-values-component',
	data: function() {
		return {
			values: [],
			bufferStart: 0,
			bufferSize: 50,
			bufferEnd: null,
			currentRow: 0,
			headers: {},
		};
	},
	props: ["enrichmentId", "maxRowCount"],
	created: async function() {
		this.load();
	},
	mounted: function() {
	},
	watch: {
		enrichmentId( oldValue, newValue ) {
			this.currentRow = 0;
			this.bufferStart = 0;
			this.headers = {};
			this.load();
		}
	},
	methods: {
		back() {
			if (this.currentRow > 0) {
				this.currentRow -= 1;
				if (this.currentRow < this.bufferStart) {
					this.bufferStart -= this.bufferSize;
					this.load();
				}
			}
		},

		forward() {
			if (this.currentRow < this.maxRowCount - 1) {
				this.currentRow += 1;
				if (this.currentRow > this.bufferEnd) {
					this.bufferStart += this.bufferSize;
					this.load();
				}
			}
		},

		pageBack() {
			if (this.bufferStart > 0) {
				this.bufferStart -= this.bufferSize;
				this.currentRow -= this.bufferSize;
				this.load();
			}
		},

		pageForward() {
			if (this.bufferStart + this.bufferSize < this.maxRowCount) {
				this.bufferStart += this.bufferSize;
				this.currentRow += this.bufferSize;
				this.load();
			}
		},

		canBack() {
			return this.currentRow > 0;
		},

		canForward() {
			return this.currentRow < this.maxRowCount - 1;
		},
		// paging one buffer size 
		canPageBack() {
			return (this.currentRow - this.bufferSize >= 0);
		},
		canPageForward() {
			return (this.bufferStart + this.bufferSize < this.maxRowCount);
		},

		directGoto(rowNum) {
			if (rowNum >= this.maxRowCount) return;

			this.bufferStart = Math.floor(rowNum / this.bufferSize) * this.bufferSize;
			this.currentRow = rowNum;
			load();
		},

		load() {
			let self = this;
//			$.ajax({
			Mint2.slowCall({
				url: "api/annotation/listValues",
				data: {
					"start": self.bufferStart,
					"count": self.bufferSize,
					"enrichmentId": self.enrichmentId
				},
				type: "GET",
				error: function(resp) {
					alert("Cant get annotations \n" + resp.err);
				},
				success: function(resp) {
					//expecting a json array
					self.bufferEnd = self.bufferStart + resp.length;
					self.values = resp;

					self.updateHeaders();
				}
			});

		},

		updateHeaders() {
			for (const row of this.values) {
				for (const label of Object.keys( row )) {
					if(( label !== undefined ) && ( label !== null ))
						Vue.set(this.headers, label, true);
				}
			}
		},
	}
});

