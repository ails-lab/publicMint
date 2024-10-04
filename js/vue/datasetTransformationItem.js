
Vue.component('transformation-item', {
  template: `<div>
								<div class="items navigable" @click="transformationClicked(transformation)" :id="'transformation-'+transformation.id">
									<div class="label" style="width: 80%; margin-left: 10px;" :style="'left:'+left+'px' ">
											{{ transformation.name }}&nbsp;&nbsp;&nbsp;<font size="0.8em">
												{{ transformation.created }}
											</font>
									</div>
									<div class="info">
										<img :id="'transcontext'+transformation.id" :src="transformation.statusIcon"
											style="vertical-align:sub;margin-top:10px;width:16px;height:16px;cursor: pointer;"
											:title="transformation.message">
									</div>
									<div class="tail"></div>
								</div>
								<div v-if=transformation.children v-for="transformationChild of transformation.children" :title="transformationChild.name">
									<transformation-item v-bind:transformation="transformationChild" v-bind:left="left+10"></transformation-item>
								</div>
				</div>`,
  props: {
    transformation: Object,
    left: Number
  },
  methods: {
    transformationClicked: function (transformation) {
      var thisPanel = $($(this.$el).closest('div[id^=kp]'));
      $K.kaiten('removeChildren', thisPanel, false);
      $(thisPanel).find('div.k-active').removeClass('k-active');
      $(thisPanel).find('div#transformation-' + transformation.id).toggleClass('k-active');
      $K.kaiten('load', {
        kConnector: 'html.page',
        url: 'DatasetOptions_input.action?uploadId=' + transformation.id,
        kTitle: 'Dataset Options'
      });
    },
  }
});
