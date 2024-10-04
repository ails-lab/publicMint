Vue.component('statistics-component', {
  template: '#statistics-component',
  props: {
    translationFlow: {
      type: Boolean,
      default: false
    },
    uploadId: String | Number
  },
  data() {
    return {
      dataset_statistics: [],
      dataset_details: {},
      loading: true,
      selectedTranslationTargetFields: [],
      normalizationMode: false
    };
  },
  mounted: async function () {
    await this.getDatasetDetails();
    await this.getDatasetStatistics();
    console.log(this.dataset_statistics)
    this.addHasChildrenField();
    this.addTranslateBoolean();
    this.loading = false;
  },
  methods: {
    getDatasetStatistics: async function () {
      await $.ajax({
        url: "api/datasetStatistics/" + this.uploadId,
        type: "GET",
        context: this,
        error: function () {
          alert("Error while getting options for Dataset #" + this.uploadId);
        },
        success: function (response) {
          this.dataset_statistics = response;
        }
      });
    },
    getDatasetDetails: async function () {
      await $.ajax({
        url: "api/datasetDetails/" + this.uploadId,
        type: "GET",
        context: this,
        error: function () {
          alert("Error while getting options for Dataset #" + this.uploadId);
        },
        success: function (response) {
          this.dataset_details = response;
        }
      });
    },
    addHasChildrenField: function () {
      this.dataset_statistics.forEach(stat => {
        stat.hasChildren = false;
        if (stat.parent === "") return;
        if (!this.dataset_statistics[stat.parent].hasChildren) {
          this.dataset_statistics[stat.parent].hasChildren = true;
          this.dataset_statistics[stat.parent].expanded = true;
          this.dataset_statistics[stat.parent].children = [];
        }
        this.dataset_statistics[stat.parent].children.push(stat.id)
      })
    },
    addTranslateBoolean: function () {
      this.dataset_statistics.forEach(stat => {
        stat.toBeTranslated = false;
      })
    },
    cellPaddingLeft: function (indent) {
      let padding = indent * 15 + 'px'
      return 'padding-left: ' + padding;
    },
    noItemsCondition: function (stat) {
      return stat.distinct == stat.count && stat.count == this.dataset_details.numOfItems
    },
    toggleClicked: function (stat, condition) {
      this.loading = true;
      if (typeof stat == 'number') stat = this.dataset_statistics[stat]
      if (stat.hasChildren) {
        stat.children.forEach(child => this.toggleClicked(child, condition))
      }
      stat.expanded = condition;
      this.loading = false;
    },
    openStatisticInfo: function (stat, event) {
      var panelcount = $('div[id^="kp"]:last');
      var parenttitle = panelcount.find('.titlebar > table > tbody > tr > td.center > div.title').html();

      if (parenttitle.includes("Statistics") || parenttitle.includes("Translate")) {
        $K.kaiten("load", {
          kConnector: 'html.page',
          url: 'valuebrowsing?name=' + this.dataset_details.name + '&xpathHolderId=' + stat.xpathHolderId,
          kTitle: 'Value Browsing'
        });
      } else {
        $K.kaiten("reload", panelcount, {
          kConnector: 'html.page',
          url: 'valuebrowsing?name=' + this.dataset_details.name + '&xpathHolderId=' + stat.xpathHolderId,
          kTitle: 'Value Browsing'
        });
      }
    },
    removeChildrenTabs: function ( ) {
      let thisPanel = $($(this.$el).closest('div[id^=kp]'))
      $K.kaiten('removeChildren', thisPanel, false);
    },
    changeNormalizationMode: function() {
      this.normalizationMode = !this.normalizationMode;
    },
    continueToNormalization: function () {
      this.selectedTranslationTargetFields = this.dataset_statistics.filter(stat => stat.toBeTranslated);
      if(!this.selectedTranslationTargetFields.length) return;
      this.removeChildrenTabs()
      this.changeNormalizationMode();
    }
  },
});