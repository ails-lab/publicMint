Vue.component('translate-normalization-component', {
  template: '#translate-normalization-component',
  props: {
    fields: {
      type: Array,
      required: true
    },
    selectedFields: {
      type: Array,
      required: true
    },
    dataset_details: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      languageTags: [],
      translationMethod: '',
      selectedDefaultLang: '',
      loading: false,
      languageTagObjects: [],
      langs: [
        {
          code: 'bg',
          label: 'Bulgarian'
        },
        {
          code: 'hr',
          label: 'Croatian'
        },
        {
          code: 'cs',
          label: 'Czech'
        },
        {
          code: 'da',
          label: 'Danish'
        },
        {
          code: 'nl',
          label: 'Dutch'
        },
        {
          code: 'et',
          label: 'Estonian'
        },
        {
          code: 'fi',
          label: 'Finnish'
        },
        {
          code: 'fr',
          label: 'French'
        },
        {
          code: 'de',
          label: 'German'
        },
        {
          code: 'el',
          label: 'Greek'
        },
        {
          code: 'hu',
          label: 'Hungarian'
        },
        {
          code: 'ga',
          label: 'Irish'
        },
        {
          code: 'it',
          label: 'Italian'
        },
        {
          code: 'lv',
          label: 'Latvian'
        },
        {
          code: 'lt',
          label: 'Lithuanian'
        },
        {
          code: 'mt',
          label: 'Maltese'
        },
        {
          code: 'pl',
          label: 'Polish'
        },
        {
          code: 'pt',
          label: 'Portuguese'
        },
        {
          code: 'ro',
          label: 'Romanian'
        },
        {
          code: 'sk',
          label: 'Slovak'
        },
        {
          code: 'sl',
          label: 'Slovenian'
        },
        {
          code: 'es',
          label: 'Spanish'
        },
        {
          code: 'sv',
          label: 'Swedish'
        },
      ],
      error: ''
    };
  },
  mounted: async function () {
    this.loading = true;
    console.log(this.dataset_details, this.selectedFields);
    for (let sel of this.selectedFields) {
      if(!sel.children) continue;
      for (let childIdx of sel.children) {
        let child = this.fields[childIdx];
        if (child.xpath == '@xml:lang') await this.getLanguageTagsById(child.xpathHolderId);
      }
    };
    this.loading = false;
    this.languageTags.push('n/a')
    this.createLanguageTagsObjects(); 
  },
  methods: {
    getLanguageTagsById: async function (id) {
      await $.ajax({
        url: `ValueList?xpathHolderId=${id}&start=0&max=15`,
        type: "GET",
        context: this,
        error: function () {
          alert("Error while getting options for Dataset #" + this.uploadId);
        },
        success: function (response) {
          response?.values?.forEach(tag => {
            if (!this.languageTags.includes(tag.value)) this.languageTags.push(tag.value)
          });
        }
      });
    },
    createLanguageTagsObjects: function () {
      this.languageTagObjects = this.languageTags.map(lang => {
        return {
          label: lang,
          normalizedLabel: '',
          applyDetection: false
        }
      })
    },
    translationMethodChanged: function () {
      this.selectedDefaultLang = '';
      this.error = '';
      this.createLanguageTagsObjects();
      console.log(this.translationMethod)
    },
    applyDetectionChanged: function(index) {
      this.languageTagObjects[index].applyDetection = !this.languageTagObjects[index].applyDetection;
    },
    submitTags: async function () {
      for(let tag of this.languageTagObjects) {
        if(tag.normalizedLabel == '') {
          this.error = "You have to select a normalized language for every language tag."
          return;}
      }
      this.error = '';
      let body = this.createTranslateBody();
      console.log(body);
	  await this.submit(body);      
	  this.closeTab();
    },
    submitDefaultLang: async function () {
      if (!this.selectedDefaultLang.length) {
        this.error = "You have to select the default language."
        return;
      }
      this.error = '';
      let body = this.createTranslateBody();
      console.log(body);
	  await this.submit(body);
      this.closeTab();
    },
    createTranslateBody: function () {
      if (this.translationMethod == 'default'){
        return {
          datasetId: this.dataset_details.id,
          defaultLanguage: this.selectedDefaultLang,
          selectedFields: this.selectedFields.map(field => field.xpathHolderId)
        }
      }
      return {
        datasetId: this.dataset_details.id,
        normalizedTags: this.languageTagObjects.filter(tag => tag.normalizedLabel != "no-translation"),
        selectedFields: this.selectedFields.map(field => field.xpathHolderId)
      }
    },
    submit: async function( body ) {
  	  await $.ajax({
        url: "api/translation/create",
        data: JSON.stringify( body),
        contentType: "application/json; charset=utf-8",
        type: "POST",
        context: this,
        error: function () {
          alert("Could not create translation");
        },
        success: function () {
		  alert( "Translation job queued");
        }
      });		
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