package gr.ntua.ivml.mint.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/*
 * Helper class to build JsonNode trees in Jackson.
 * Last time I googled that, I write it myself.
 */
public class JacksonBuilder {
	// if all fits in an obj, content is stored in here
	JacksonBuilder parent;
	
	Map<String, JacksonBuilder> obj;
	List<JacksonBuilder> arr;

	JsonNode val;
	
	// if optional is true, the node will not be printed if there is no 
	// optional child set
	boolean optionalValue = false;
	
	
	public static JacksonBuilder obj() {
		JacksonBuilder res = new JacksonBuilder();
		res.getMap();
		return res;
	}
	
	public static JacksonBuilder arr() {
		JacksonBuilder res = new JacksonBuilder();
		res.getList();
		return res;
	}
	
	public static JacksonBuilder val(String s) {
		return new JacksonBuilder().set( s );
	}
	
	public static JacksonBuilder val(int i ) {
		return new JacksonBuilder().set( i );
	}
	
	// set this and the node will only appear if here or in a child an optional value is set
	public JacksonBuilder optional() {
		optionalValue = true;
		return this;
	}
	
	
	// helper to remove an accidentially added element
	private void remove(JacksonBuilder jb) {
		if( arr != null ) arr.remove(jb);
		else if( obj != null ) obj.entrySet().removeIf( e-> e.getValue()==jb);
	}
	
	
	// dont use it, better
	// use add or append for values
	// use add( "key", "value" ) instead of with( "key" ).set( "value").up()
	
	public JacksonBuilder set( String val) {
		this.val = new TextNode( val );
		return parent;
	}

	public JacksonBuilder put( String key, String val) {
		return put(key).set( val );
	}
	public JacksonBuilder append( String val) {
		return append().set( val );
	}
	
	
	public JacksonBuilder set( int val ) {
		this.val = new IntNode( val );
		return parent;
	}
	public JacksonBuilder put( String key, int val) {
		return put(key).set( val );
	}
	public JacksonBuilder append( int val) {
		return append().set( val );
	}

	
	public JacksonBuilder set( long val ) {
		this.val = new LongNode( val );
		return parent;
	}
	public JacksonBuilder put( String key, long val) {
		return put(key).set( val );
	}
	public JacksonBuilder append( long val) {
		return append().set( val );
	}

	
	public JacksonBuilder set( double val ) {
		this.val = new DoubleNode( val );
		return parent;
	}
	public JacksonBuilder put( String key, double val) {
		return put(key).set( val );
	}
	public JacksonBuilder append( double val) {
		return append().set( val );
	}


	
	public JacksonBuilder set( boolean b ) {
		this.val = b?BooleanNode.TRUE:BooleanNode.FALSE;
		return parent;
	}
	public JacksonBuilder put( String key, boolean val) {
		return put(key).set( val );
	}
	public JacksonBuilder append( boolean val) {
		return append().set( val );
	}


	
	// Link a whole tree  in here from another object
	public JacksonBuilder set( JsonNode node) {
		this.val = node;
		return parent;
	}
	
	public JacksonBuilder put( String key, JsonNode val) {
		return put(key).set( val );
	}

	public JacksonBuilder append( JsonNode val) {
		return append().set( val );
	}

	
	public JacksonBuilder put( String key) {
		JacksonBuilder res = child();
		getMap().put( key,  res);
		return res;
	}
	
	public JacksonBuilder append() {
		JacksonBuilder res = child();
		getList().add( res );
		return res;
	}
		

	private Map<String, JacksonBuilder> getMap() {
		if( obj == null && arr == null )
			obj = new LinkedHashMap<String, JacksonBuilder>();
		return obj;
	}

	public JacksonBuilder optionalAdd( String key, Optional<JacksonBuilder> val ) {
		if( val.isPresent()) {			
			JacksonBuilder c =val.get().optional();
			c.parent = this;
			getMap().put( key, c);
		}
		return this;
	}
	
	public JacksonBuilder optionalAddText( String key, Optional<String> val ) {
		if( val.isPresent()) 		
			getMap().put( key, child().optional().set( val.get() ));
		return this;
	}
	
	
	public JacksonBuilder optionalAppend( Optional<JacksonBuilder> val ) {
		if( val.isPresent()) {			
			JacksonBuilder c =val.get().optional();
			c.parent = this;
			getList().add( c );
		}
		return this;		
	}
	
	public JacksonBuilder optionalAppendText( Optional<String> val ) {
		if( val.isPresent()) 
			getList().add( child().optional().set( val.get() ));
		return this;		
	}
	
	
	private JacksonBuilder child() {
		JacksonBuilder res = new JacksonBuilder();
		res.parent = this;
		return res;
	}
	
	private List<JacksonBuilder> getList() {
		if( obj == null && arr == null )
			this.arr = new ArrayList<JacksonBuilder>();
		return arr;
	}
	
	// same as put
	public JacksonBuilder withObj( String name ) {
		JacksonBuilder res = child();
		getMap().put( name,  res);
		return res;
	}
		
	// write a predicate that may put this node in, possibly set some value in it first!
	public JacksonBuilder put( String name, Predicate<JacksonBuilder> childBuild ) {
		JacksonBuilder res =new JacksonBuilder();
		if( childBuild.test(res))
			getMap().put( name,  res);
		return this;
	}
		
	// write a closure that uses this node
	public JacksonBuilder with( Consumer<JacksonBuilder> putOrAppend ) {
		putOrAppend.accept( this );
		return this;
	}
	
	// write a predicate that returns true if it could set something in the argument
	public JacksonBuilder append( Predicate<JacksonBuilder> childBuild ) {
		JacksonBuilder res =new JacksonBuilder();
		if( childBuild.test(res))
			getList().add( res);
		return this;
	}

	public JacksonBuilder withArray( String name ) {
		JacksonBuilder list = getMap().computeIfAbsent(name, k->child());
		list.getList();
		return list;		
	}
	
	// end this with up()
	// same as append
	public JacksonBuilder appendObj( ) {
		return append();
	}
	
	public JacksonBuilder up() {
		return parent;
	}
	
		
	public boolean isArray() {
		return arr != null;
	}
	
	public boolean isObj() {
		return obj != null;
	}
	
	public JsonNode get() {
		return get( new ObjectMapper());
	}
	
	
	
	public JsonNode get( ObjectMapper om) {
		
		if( obj != null ) {
			ObjectNode res = om.createObjectNode();
			
			for( String key: obj.keySet()) {
				JacksonBuilder jb = obj.get(key);
				if( !jb.optionalValue || jb.hasOptionalValue()) {
					JsonNode child = jb.get( om );
					res.set( key, child );
				}
			}
			return res;
		} else if( arr != null ) {
			// make array Node
			ArrayNode res = om.createArrayNode();
			for( JacksonBuilder value: arr ) {
				if( !value.optionalValue || value.hasOptionalValue()) 
					res.add( value.get(om ));
			}
			return res;
		} else if( val != null ) {
			return val;
		}
		// ideally we never get here 
		return om.getNodeFactory().nullNode();
	}
	
	// do I or my children have a set optional value
	private boolean hasOptionalValue() {
		boolean res = false;
		if( obj != null ) {
			for( String key: obj.keySet()) 
				res |= obj.get(key).hasOptionalValue();
		} else if( arr != null ) {
			for( JacksonBuilder value: arr ) 
				res |= value.hasOptionalValue();			
		} else if( val != null ) {
			return optionalValue;
		}
		return res;
	}
	
	public ObjectNode getObj() {
		return (ObjectNode) get();
	}
	
	public ArrayNode getArray() {
		return (ArrayNode) get();
	}
	
	public static void main( String[] args ) {
		// Optional<String> desc = Optional.of( "Optional Description");
		Optional<String> desc = Optional.empty();
		Optional<String> title = Optional.of( "Im here");
		Optional<String> uri = Optional.of( "http://some.url/is/here");
		Optional<String> format = Optional.of( "A4 Film");
		//Optional<String> format = Optional.empty();
		
		ObjectNode res = JacksonBuilder.obj().withObj("description").optional()
				.optionalAddText( "text" , desc )
				.put( "lang", "en")
				.up()
		.optionalAddText( "title",  title)
		.optionalAddText( "uri", uri)
		.withObj( "format").optional()
			.optionalAddText( "size", format)
			.put( "shape", "rect")
			.up()
		.withArray("allFormats")
			//.optional()
			.append("fixedFormat")
			.optionalAppendText(format)
			.up()
		.getObj();
		System.out.println( res.toString());
				
		
	}

	public JacksonBuilder addNumber(String key, Number n) {
		JacksonBuilder child = child();
		child.set( new LongNode( n.longValue() ) );
		getMap().put( key, child );
		return this;
	}
	
	
}
