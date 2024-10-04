package gr.ntua.ivml.mint.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.translation.Translator;
import gr.ntua.ivml.mint.translation.Translator.FieldLiteral;
import gr.ntua.ivml.mint.translation.Translator.TranslatedLiteral;
import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.XPathContext;


/*
 * Extract Literals from EDM XML or adds translations to it
 * If you dont want to do EDM, you can use different SchemaLiteralProcessor.
 * 
 * This implementation is fairly general, with little dependency on EDM specifically,
 * It inserts the translation right after the Element that is translated.
 * EDM specific is adding the wasGeneratedBy and confidenceLevel attributes
 */
public class EdmSchemaLiteralProcessor implements Translator.SchemaLiteralProcessor {
	private static final Logger log = Logger.getLogger( EdmSchemaLiteralProcessor.class );
			
	public static class QueryResolved {
		public String queryWithPrefixes;
		public XPathContext context;
		
		public QueryResolved( String queryWithPrefixes, XPathContext context ) {
			this.context = context;
			this.queryWithPrefixes = queryWithPrefixes;
		}
	}
	
	private boolean withArrayIndices = false;
	
	public EdmSchemaLiteralProcessor( ObjectNode config ) {
		
	}
	
	private String nodeName( Node n ) {
		String localName, uri;
		String attribute = "";
		if( n instanceof Element ) {
			Element e= (Element) n;
			localName = e.getLocalName();
			uri =  e.getNamespaceURI();
		} else if( n instanceof Attribute ) {
			Attribute a = (Attribute) n;
			localName = a.getLocalName();
			uri = a.getNamespaceURI();
			attribute = "@";
		} else {
			return "";
		}
		
		if( uri != null )
			return "/"+ attribute + "{"+uri+"}:"+localName;
		else
			return "/" + attribute + localName;
	} 

	// path must include path to this element, if elem is root, path can be ""
	private void buildLiteralList( Element elem, String path,
			List<FieldLiteral> result ) {
		Optional<String> lang = Optional.empty();
		
		for( int i=0; i<elem.getAttributeCount(); i++ ) {
			Attribute att = elem.getAttribute(i);
			if( att.getNamespaceURI().equals( "http://www.w3.org/XML/1998/namespace" ) 
				&& att.getLocalName().equals( "lang")) {
				lang = Optional.of(att.getValue());
				continue;
			}
			String content = att.getValue();
			// replace funny spaces with real ones
			content = content.replaceAll("[\u00A0\u1680\u180E\u2000-\u200B\u202F\u205F\u3000\uFEFF]", " ");
			if( StringUtils.isNotBlank(content)) {
				FieldLiteral lit = new FieldLiteral();
				lit.language = null;
				lit.fieldName = path+nodeName( att);
				lit.literal = content;
				result.add( lit );
			}
		}
		
		// if elem has element child, we dont want a literal, its mixed content in best case
		
		if( elem.getChildElements().size() == 0 ) {
			String textContent = elem.getValue();
			textContent = textContent.replaceAll("[\u00A0\u1680\u180E\u2000-\u200B\u202F\u205F\u3000\uFEFF]", " ");
			if( StringUtils.isNotBlank( textContent )) {
				FieldLiteral lit = new FieldLiteral();
				lit.language = lang.orElse(null);
				lit.fieldName = path;
				lit.literal = textContent;
				result.add( lit );			
			}
			return;
		}
		
		// check for multiple occurencess of the same element (to add [n])
		HashMap<String, Integer> counters = new HashMap<>();
		Elements elems = elem.getChildElements();
		
		if( withArrayIndices) {			
			for( int i=0; i<elems.size(); i++ ) {
				Element e  = elems.get(i);
				counters.merge( nodeName(e), 1, (a,b)-> a+b);
			}	
		}
		
		HashMap<String,Integer> countersUpward = new HashMap<>();
		
		for( int i=0; i<elems.size(); i++ ) {
			Element e  = elems.get(i);
			String name = nodeName( e );
			if( counters.getOrDefault(name, 0) > 1 ) {
				int pos = countersUpward.merge(name, 1, (a,b)->a+b);
				if (withArrayIndices)
					name += "["+pos+"]";
			}
			buildLiteralList( e, path + name, result );
		}	
	}
	
	@Override
	public List<FieldLiteral> extractLiterals(Document doc, JsonNode config  ) {
		Element root = doc.getRootElement();
		
		ArrayList<FieldLiteral> res = new ArrayList<>();
		buildLiteralList(root, nodeName( root ), res );
		// there will be some processing according to the config
		return res;
	}

	

	public void translateElement( Element original, String translatedText, String language, Optional<String> confidence ) {
		Element copy = new Element(original);
		copy.removeChildren();
		Attribute attLang = new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", language );
		copy.addAttribute(attLang);
		
		Attribute att = new Attribute("edm:wasGeneratedBy", "http://www.europeana.eu/schemas/edm/",  "SoftwareAgent");
		copy.addAttribute( att );
		
		if( confidence.isPresent()) {
			att = new Attribute("edm:confidenceLevel", "http://www.europeana.eu/schemas/edm/", confidence.get());
			copy.addAttribute( att );
		}
		copy.appendChild( translatedText );
		// insert right after
		int pos = original.getParent().indexOf(original);
		original.getParent().insertChild( copy, pos+1);
	}
	
	
	public static QueryResolved resolveQuery( String queryWithNamespaces ) {
		
		String prefixes = "abcdefghijklmnopqrstuvwxyz";
		// cant have more than 26 prefixes :-)
		int prefixIndex = 0;
		XPathContext context = new XPathContext(); 
		StringBuffer query = new StringBuffer();
		StringBuffer currentUri = new StringBuffer();
		
		boolean inUri = false;
		for( char c: queryWithNamespaces.toCharArray() ) {
			if( inUri ) {
				if( c == '}' ) {
					inUri = false;
					context.addNamespace(prefixes.substring(prefixIndex, prefixIndex+1), currentUri.toString());
					query.append(prefixes.substring(prefixIndex, prefixIndex+1));
					prefixIndex += 1;
					currentUri.setLength(0);
				} else 
					currentUri.append(c);				
			} else {
				if( c == '{' ) {
					inUri = true;
				} else {
					query.append(c);
				}
			}
		}

		return new QueryResolved(query.toString(), context);
	}
	
	// need to make prefixes for all namespaces in {} in the query
	private Nodes query( Document doc, String fieldname ) {

		QueryResolved qr = resolveQuery(fieldname);
		return doc.query( qr.queryWithPrefixes, qr.context );
	}
	
	
	public static String removePositionsFromPath( String xpath ) {
		// zero length positive look ahead (?=...)
		xpath = xpath.replaceAll("\\[\\d+\\](?=/)", "");
		xpath = xpath.replaceAll("\\[\\d+\\]$", "");
		return xpath;
	}
	
	
	@Override
	public void applyTranslation(Document doc, Collection<TranslatedLiteral> translations) {
		// get all the fieldnames ... and backward, find the original and append sibling with the translation
		// backwards, so the positional searches are still correct.
		// we assume the literals are in document order
		List<TranslatedLiteral> reverse = new ArrayList<>();
		for( TranslatedLiteral tl: translations )
			reverse.add( 0, tl );
		for( TranslatedLiteral tl: reverse ) {
			Nodes nodes = query( doc, tl.fieldName );
			if( nodes.size() != 1 ) {
				log.warn( "Translation original not found\n" + tl.originalLanguage +"->"+tl.translatedLanguage +"\n" + "'"+ tl.originalLiteral +"'" );
				continue;
			}
			
			Node node = nodes.get(0);
			if( node instanceof Element ) {
				Optional<String> confidence = tl.score>0? Optional.of(
						String.format("%.3g",(tl.score/1000.0f))
					) : Optional.empty();
						
				translateElement( (Element) node, tl.translatedLiteral, tl.translatedLanguage, confidence );
			}
		}
	}
}
