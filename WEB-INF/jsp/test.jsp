<%@ include file="top.jsp" %>  

<script>
	document.title = "MINT Home";
</script>
<style>
	#k-topbar #logout-button {
	    border : 0 none;
        background:url('images/logout.png') no-repeat;
        background-position: 6px 7px;
        width:32px;
        height:32px;
	}	

	#k-topbar #logout-button:hover {
	    border : 0 none;
        background:url('images/logout_hover.png') no-repeat;
        background-position: 6px 7px;
        width:32px;
        height:32px;
	}

	#k-topbar #help-button {
	    border : 0 none;
        background:url('images/help.png') no-repeat;
        background-position: 4px 4px;
        width:32px;
        height:32px;
	}	


</style>

<script>
	(function($){
		// keep a reference to Kaiten's container
		$K = $('#container');
		// initialize Kaiten
		$K.kaiten({ 
			// 3 panels max. on the screen
			columnWidth : '33%',
			optionsSelector : '#custom-options-text',
			startup : function(dataFromURL){
				// handle URL parameters sent when opening a panel in a new tab
				this.kaiten('load', {kConnector:'html.page', url: "html/image_analysis.html?datasetId=1000", kTitle:'Test' } )	
				$($K.data("kaiten").selectors.optionsCustom).hide();
				$($K.data("kaiten").selectors.appMenuContainer).append($("<button>").attr("title", "Help").attr("id", "help-button").click(function () {
					Mint2.documentation.openDocumentation({
						resource: "/documentation",
						target: "_blank"
					});
				}));
					
				$($K.data("kaiten").selectors.appMenuContainer).append($("<button>").attr("title", "Logout").attr("id", "logout-button").click(function () {
						location.href="Logout.action";
					}));
			}				
			
		});
		
	
	})(jQuery);
	  
</script>

</div>

</body>
</html>

