<%@ include file="_include.jsp"%>
<%@ page language="java" errorPage="error.jsp"%>
<%@page pageEncoding="UTF-8"%>

<div class="panel-body">
	<div class="block-nav dataset-options-page"
		:class="{'pale-yellow-bg': dataset_details.isTransfOrAnnotatedWithTransfParent}" v-if="!loading">
		<div class="summary">
			<div class="label">
				<img v-if="dataset_details.isTransfOrAnnotatedWithTransfParent" src="images/okblue.png"
					style="margin-top:10px;">
				{{dataset_details.name}}
				<div style="float:right;font-size:0.5em;"><a
						onClick="$(this).closest('.label').next('div.info').find('div.setdetails').toggle()">
						<img border="0" align="middle" style="margin-right:3px;margin-top:11px;" src="images/plus.gif">Show
						details</a></div>
			</div>

			<div class="info">
				<b>Status:</b>
				<font v-if="dataset_details.status == 'FAILED'" color="red">
					{{ dataset_details.message }}
				</font>
				<font v-else color="blue">
					{{ dataset_details.message }}
				</font>
				<br />
				<template v-if="dataset_details.schema != ''">
					<b>Schema:</b>
					<font color="blue">
						{{ dataset_details.schema }}
					</font>
				<br />
				</template>
				<template v-if="dataset_details.mappingName != null">
					<b>Mapping:</b>
					<font color="blue">
						{{ dataset_details.mappingName }}
					</font><br/>
				</template>

				<s:if test="hasActionErrors()">
					<div class="errors">
						<s:iterator value="actionErrors">
							<div>
								<font color="red">
									<s:property escapeHtml="false" />
								</font>
							</div>
						</s:iterator>
					</div>
				</s:if>
				<div class="setdetails" style="display:none;">
					<b>No of Items:</b>
					<font color="blue">
						{{ dataset_details.numOfItems }}
					</font><br />
					<b>Creation date:</b>
					<font color="blue">
						{{ dataset_details.created }}
					</font></br>
					<b>By user:</b>
					<font color="blue">
						{{ dataset_details.creator }}
					</font></br>
					<b>Set Spec:</b>
					<font color="blue">
						{{ dataset_details.organizationId }}
					</font></br>
					<b>Dataset Id:</b>
					<font color="blue">
						{{ dataset_details.id }}
					</font><br/>
					<template v-if="dataset_details.report != null">
						<b>Report:</b><br/>
						<textarea rows="5" cols="50" readonly="true" class="pale-yellow-bg">{{ dataset_details.report }}</textarea>
					</template>
				</div>
			</div>
		</div>
		<div v-for="option of dataset_options" :title="option.label" :data-load="getKConnector(option)"
			@click="datasetOptionClicked(option,$event)" class="items navigable">
			<div class="head" v-if="option.label.includes('Delete')"><img src="images/trash_can.png"></div>
			<div class="head" v-if="option.label.includes('Statistics')"><img src="images/stats2.png"></div>
			<div class="label">{{ option.label }}</div>
			<div class="detail" v-if="option.label == 'Show all items'">{{ dataset_details.numOfItems }} items</div>
			<div class="detail" v-if="option.label == 'Show invalid items'">{{ dataset_details.invalidItems }} items</div>
			<div class="tail" v-if="option.response=='panel'"></div>
		</div>

		<template v-if="dataset_details.downloads && dataset_details.downloads.length > 0">
			<div class="info">&nbsp;</div>
			<div class="accordion">
				<h3><a href="#">Downloads </a></h3>
				<div>
					<template v-for="download of dataset_details.downloads">
						<div title="download" @click="downloadOptionClicked(download.url)" class="items clickable">
							<div class="head"><img src="images/kaiten/download-16.png"></div>
							<div class="label" style="width:80%">
								{{ download.title }}
							</div>
							<div class="info"></div>
							<div class="tail"></div>
					</template>
				</div>
			</div>
		</template>


		<template
			v-if="!dataset_details.isTransfOrAnnotatedWithTransfParent && dataset_details.children && dataset_details.children.length > 0">
			<div class="summary">
				<div class="label" style="margin-bottom:-10px;margin-top:20px;">Transformations</div>
			</div>

			<div v-for="transformation of dataset_details.children" :title="transformation.name">
				<transformation-item v-bind:transformation="transformation" v-bind:left="0"></transformation-item>
			</div>
		</template>

	</div>
</div>

<style>
.info br {
	margin-bottom:0.2em;
}

</style>
<script>
	let uploadId, organizationId, userId;
	$(".k-panel").each(function (i, obj) {
		let kpanelUrl = $('#' + obj.id).data().kpanel._state.load.data.url;
		if (obj.id && kpanelUrl && kpanelUrl.includes('DatasetOptions_input.action') && kpanelUrl.includes('uploadId')) {
			uploadId = kpanelUrl.split('uploadId=')[1];
			if (uploadId.includes('&')) {
				uploadId = uploadId.split('&')[0];
			}
		}
	})
	sessionStorage.setItem("uploadId", uploadId)

	let closestPanels = $(".dataset-options-page").closest('div[id^=kp]');
	let closestPanelId = closestPanels[closestPanels.length - 1].id;
	let closestDatasetOptionsPage = $('#' + closestPanelId).find('.dataset-options-page');
	let closestDatasetOptionsPageId = 'dataset-options-page-' + closestPanelId;
	closestDatasetOptionsPage.attr('id', closestDatasetOptionsPageId)

	const EventBus = new Vue();

	var dataset_options_page = new Vue({
		el: '#' + closestDatasetOptionsPageId,
		data: {
			uploadId: null,
			organizationId: null,
			userId: null,
			dataset_options: [],
			dataset_details: {},
			loading: true
		},
		mounted: async function () {
			this.uploadId = sessionStorage.getItem("uploadId");
			await this.getDatasetDetails();
			await this.getDatasetOptions();
			this.loading = false;
		},
		methods: {
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
			getDatasetOptions: async function () {
				await $.ajax({
					url: "api/datasetOptions/" + this.uploadId,
					type: "GET",
					context: this,
					error: function () {
						alert("Error while getting options for Dataset #" + this.uploadId);
					},
					success: function (response) {
						this.dataset_options = response;
					}
				});
			},
			getKConnector: function (option) {
				if ( ["json", "copyLink"].includes( option.response )) return '';
				let connectorType = "html.page";
				if( "external" == option.response ) connectorType= "iframe"; 
				return JSON.stringify( 
					{
						"kConnector":connectorType ,
						"url": option.url,
						"kTitle" : option.label 
					} );
			},
			datasetOptionClicked: function (option, event ) {
				if (option.response == 'panel') return;
				if( option.response == 'copyLink' ) {
					this.copyAbsoluteUrlToClipboard( option.url);
					// some feedback here 
					Mint2.infoPopup( event.target, "Copied to Clipboard");
					return;
				}
				this.apiResponse(option.url, option.method, option.warning)
			},
			downloadOptionClicked: function (url) {
				window.location = url;
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
			// if prev is truthy, reload the panel to the left instead
			reloadPanel: function ( prev ) {
				let thisPanel = $($(this.$el).closest('div[id^=kp]'))
				if( prev ) thisPanel = thisPanel.prev()
				$K.kaiten('removeChildren', thisPanel, false);
				$K.kaiten('reload', thisPanel);
			},
			apiResponse: function (url, method, warning) {
				// method= 'DELETE'
				// warning = 'Caution: Delete data upload';
				var self = this;
				if(warning){	
					Mint2.modalDialogWithCancel(Mint2.message(warning, Mint2.WARNING), "" + url, () => self.apiCallTriggered(url , method))
				}
				else {
					this.apiCallTriggered(url, method)
				}
			},
			
			apiCallTriggered: function (url, method) {
				var self = this;
				$.ajax({
					url: url,
					type: method,
					error: function (response) {
						console.log(response)
						response = response.responseJSON
						Mint2.modalDialog(Mint2.message(response.error, Mint2.ERROR), "" + url, () => self.reloadPanel())
					},
					success: function (response) {
						console.log(response)
						// if the dataset DELETE call was done, there is no point in reloading the options panel
						// we need to reload the panel to the left
						let prev = url.includes( "dataset") && (method=="DELETE")
						Mint2.modalDialog(Mint2.message(response.msg, Mint2.OK), "" + url, () => self.reloadPanel(prev))
					}
				});
			},
		},
	});

</script>