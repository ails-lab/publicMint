package gr.ntua.ivml.mint.util;

import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.XpathHolder;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import javax.servlet.jsp.JspWriter;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gr.ntua.ivml.mint.util.Jackson;

public class JSStatsTree {
	public JSStatsTree() {
	}
	
	public ArrayNode getStatisticsJson (Dataset ds) {
		ArrayNode result = Jackson.om().createArrayNode();
		try{
		   xpathTableRecurse(result, ds.getRootHolder(),0,0,0);
		}catch (Exception e){
			System.out.println(e.getMessage());
		}
				
		return result;
	}

	public void xpathTableRecurse(ArrayNode resarray,XpathHolder xp,int level , int indent,int parent) throws IOException {
		if (!xp.isTextNode()){
			String name = xp.getNameWithPrefix(true);
			ObjectNode result = Jackson.om().createObjectNode();
			int id=resarray.size();
			result.put("id",  id);
			result.put("xpath", xp.getNameWithPrefix(true));
			result.put("xpathHolderId", xp.getDbID());
			result.put("count", xp.getCount());
			if(xp.getChildren().size()==1 && (xp.getChildren().get(0).isTextNode())) {
				indent=indent+2;
			} else if (xp.getChildren().size()>0 && name.length()>0) {
				indent++;  
			} else if (xp.getChildren().size()==0 || xp.isAttributeNode() || xp.getTextNode()!=null) {
				indent=indent+2; 
			}
			result.put("indent", indent);
			if(level>0 && resarray.size()>0)
				result.put("parent", parent);
			else {
				result.put("parent","");
			}
			if (xp.isAttributeNode()) {
				//attribute stuff
				result.put("count",xp.getCount());
				float ln=xp.getAvgLength();
				DecimalFormat oneDPoint = new DecimalFormat("#.#");
				result.put("length",oneDPoint.format(ln));
				result.put("distinct",xp.getDistinctCount());
			} else {
				XpathHolder text = xp.getTextNode();
				if (text == null) {
					result.put("length","");
					result.put("distinct","");
				} else {
					// text node stuff
					result.put("count",text.getCount());
					float ln=text.getAvgLength();
					DecimalFormat oneDPoint = new DecimalFormat("#.#");
					result.put("length",oneDPoint.format(ln));
					result.put("distinct",text.getDistinctCount());
				}
			}

			if(name.length()>0) 
				resarray.add(result);

			for (XpathHolder child : xp.getChildren())
				xpathTableRecurse(resarray,child, level+1 , indent,id);

		}
	}
	 
}