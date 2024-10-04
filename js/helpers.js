function getValueAndStoreToSessionStorage(valueName, pageUrl) {
  let value;
  $(".k-panel").each(function (i, obj) {
    let kpanelUrl = $('#' + obj.id).data().kpanel._state.load.data.url;
    if (obj.id && kpanelUrl && kpanelUrl.includes(pageUrl) && kpanelUrl.includes(valueName)) {
      value = kpanelUrl.split(valueName + '=')[1];
      if (value.includes('&')) {
        value = value.split('&')[0];
      }
      sessionStorage.setItem(valueName, value)
    } else {
      sessionStorage.removeItem( valueName )
    }
  })
}

// kaiten panel url analysis
function panelUrlParam( elem, paramName ) {
  let panel = $($(elem).closest('div[id^=kp]'))
  let url = panel.data().kpanel._state.load.data.url;
  let urlObj = new URL( url,document.baseURI )
  return urlObj.searchParams.get( paramName )
} 