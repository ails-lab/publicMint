package gr.ntua.ivml.mint.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.util.AnnotationFilter.FilterExpression;

public class AnnotationFilter {
	
	// there are two phases, buid the filter structure from the incoming filter description
	// in json
	
	// and then create from this a function of Annotation that either gives back null, for not applying this
	// annotation, or gives back a potentially modified annotation.
	
	public static interface FilterValue extends Predicate<ObjectNode> {
		public default FilterValue invert( boolean negate ) {
			if( negate ) return a->!this.test(a);
			else return this;
		}
		
	}

	public static interface FilterModifier extends Function<ObjectNode, ObjectNode> {
		
	}
	
	public static interface FilterExpression {
		public FilterValue buildFilter();
		public FilterModifier buildModifier();
	}
	
	
	
	public static class BasicFilterExpression implements FilterExpression {
		
		public static interface Extractor extends Function<ObjectNode,String> {
			
		}

		public static interface Setter extends BiConsumer<ObjectNode,String> {
			
		}

		private static enum OP {
			CONTAINS, MATCHES, EQUALS, EMPTY, SMALLER, SMALLEREQUALS, GREATER, SET, REPLACE, SETBYFIELD
		}
		
		public OP functionName;
		public String fieldName;
		
		public List<String> arguments;
		public boolean negate;
		public boolean ignoreCase;
		
		public BasicFilterExpression( String functionName, String fieldName, boolean negate, boolean ignoreCase ) {
			this.arguments = new ArrayList<>();
			this.functionName = OP.valueOf(functionName.toUpperCase().replaceAll("[^A-Z]", ""));
			this.fieldName = fieldName;
			this.negate = negate;
			this.ignoreCase = ignoreCase;
		}
		
		public BasicFilterExpression addArgument( String arg ) {
			this.arguments.add( arg );
			return this;
		}
		
		public FilterValue buildFilter() {
			switch( functionName ) {
			case CONTAINS: 
			case MATCHES: return buildContainsOrMatches().invert(negate);
			case EQUALS: return buildEquals().invert( negate );
			case EMPTY: return buildEmpty().invert( negate );
			case SMALLER: return buildSmaller(false).invert( negate );
			case SMALLEREQUALS: return buildSmaller(true).invert( negate );
			case GREATER: return buildSmaller(true).invert( !negate );
			
			default: return null;
			}
		}
		
		public FilterModifier buildModifier() {
			switch( functionName ) {
			case SET: return buildSet();
			case REPLACE: return buildReplace();
			case SETBYFIELD: return buildSetByField();
			default: return null;
			}
		}
		
		private FilterModifier buildSet() {
			final Setter setter = set( fieldName );
			final String val =  arguments.get(0);
			return a->{
				setter.accept( a, val );
				return a;
			};
		}
		
		private FilterValue buildEmpty() {
			final Extractor getter = extract( fieldName);
			return a-> StringUtils.empty(getter.apply(a));
		}

		private FilterValue buildSmaller( final boolean equals ) {
			final Extractor getter = extract( fieldName);
			float tmpComp = 0.0f;
			try {
				 tmpComp = Float.parseFloat(arguments.get(0 ));
			} catch( Exception e ) {
				return a->false;
			}
			final float comparison = tmpComp;
			
			return a-> {
				String val = getter.apply(a);
				if( val == null ) return false;
				try {
					float fval = Float.parseFloat(val);
					return equals?fval<=comparison:fval<comparison;
				} catch( Exception e ) {
					// return false
				}
				return false;
			};
		}
		
		private FilterModifier buildReplace() {
			final Setter setter = set( fieldName );
			final Extractor getter = extract( fieldName);
			final Pattern pattern = Pattern.compile(arguments.get(0));
			final String replaceValue = arguments.get(1 );
			
			return a->{
				String val = getter.apply( a );
				String newVal = pattern.matcher(val).replaceAll(replaceValue );
				setter.accept( a, newVal );
				return a;
			};
		}
		
		private FilterModifier buildSetByField() {
			final Setter setter = set( fieldName );
			final Extractor getter = extract( arguments.get(0));
			return a->{
				setter.accept( a, getter.apply( a ));
				return a;
			};
		}
				
		// assume flatJson rep of annotation
		private Extractor extract( String fieldname ) {
			return a->{ JsonNode jn = a.get( fieldName );
				return jn==null?null:jn.asText();
			};
		}
		
		private Setter set( String fieldName ) {
			return (a,val)->a.put( fieldName, val );
		}
		
		private FilterValue buildContainsOrMatches() {
			final List<Pattern> patterns = new ArrayList<>();
			
			if( ignoreCase ) {
				for( String arg: arguments ) 
					patterns.add( Pattern.compile(arg, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
			} else {
				for( String arg: arguments ) 
					patterns.add( Pattern.compile(arg));
				
			}
			final Extractor ex = extract( fieldName );
			
			if( functionName == OP.CONTAINS )
				return (a) -> {
					String val = ex.apply(a);
					for( Pattern p: patterns ) {
						if( p.matcher(val).find()) return true;
					}
					return false;
				};

			if( functionName == OP.MATCHES )
				return (a) -> {
					String val = ex.apply(a);
					for( Pattern p: patterns ) {
						if( p.matcher(val).matches()) return true;
					}
					return false;
				};	
			// should not get here
			return null;
		}

		// theoretically equals should not ignore case, but is easier than matches with regexps
		private FilterValue buildEquals() {
			final Extractor ex = extract( fieldName );
			
			if( ignoreCase ) {
				return (a) -> {
					String test = ex.apply(a);
					for( String s:arguments ) {
						if( s.equalsIgnoreCase( test )) return true;
					}
					return false;
				};
			} else {
				return (a) -> {
					String test = ex.apply(a);
					for( String s:arguments ) {
						if( s.equals( test )) return true;
					}
					return false;
				};				
			}
		}
	}
	
	// simplified not filter, you could do with negate and combine
	public static class NotFilter implements FilterExpression {
		private FilterExpression fe;
		
		public NotFilter( FilterExpression fe ) {
			this.fe = fe;
		}
		
		public FilterValue buildFilter() {
			FilterValue fv = fe.buildFilter().invert(true);
			return fv;
		}
		
		// makes no sense
		public FilterModifier buildModifier() {
			return null;
		}
	}
	
	public static class CombineFilterExpression implements FilterExpression {

		List<FilterExpression> arguments;
		public boolean negate;
		
		public static enum Operation {
			AND, OR, IFTHEN
		}
		
		public Operation op;
		
		public CombineFilterExpression( String op, boolean negate ) {
			arguments = new ArrayList<>();
			this.op = Operation.valueOf(op.toUpperCase().replaceAll("[^A-Z]", ""));
			this.negate = negate;
		}
		
		public CombineFilterExpression addArgument( FilterExpression fe, boolean negate ) {
			
			if( negate )
				arguments.add( new NotFilter( fe ));
			else 
				arguments.add( fe );
			
			return this;
		}
		
		public FilterValue buildFilter( ){
			if( op == Operation.AND ) return buildAnd().invert(negate);
			if( op == Operation.OR ) return buildOr().invert(negate);
			
			return buildIfThen().invert(negate);
		};

		public FilterModifier buildModifier() {
			if( op != Operation.IFTHEN ) return null;
			if( arguments.size() < 2 ) return null;
			FilterModifier fm = arguments.get( arguments.size()-1).buildModifier();
			if( fm == null ) return null;
			final FilterValue condition = buildAnd( arguments.subList(0, arguments.size()));
			
			// modify if condition is true
			return a->condition.test(a)?fm.apply(a):a;
		}
		
		private FilterValue buildAnd( List<FilterExpression> filters  ) {
			final List<FilterValue> inputFilters = filters.stream()
					.map( f->f.buildFilter())
					.filter( fv-> fv!=null )
					.collect( Collectors.toList());

			return ( a ) -> {
				for( FilterValue f: inputFilters ) {
					if( !f.test(a)) return false;
				}
				return true;
			};			
		}
		
		private FilterValue buildAnd() {
			return buildAnd( arguments );
		}
		
		public FilterValue buildOr() {
			final List<FilterValue> inputFilters = arguments.stream()
					.map( fe-> fe.buildFilter())
			
					.collect( Collectors.toList());

			return ( a ) -> {
				for( FilterValue f: inputFilters ) {
					if( f.test(a)) return true;
				}
				return false;
			};
		}

		public FilterValue buildIfThen() {
			final FilterValue condition  = buildAnd( arguments.subList(0, arguments.size()));
			final FilterValue outcome = arguments.get( arguments.size()-1).buildFilter();
			
			return ( a ) -> {
				return (!condition.test( a )) || outcome.test( a );
			};
		}

	}
	
	public static class FilterChain {
		
		private FilterExpression exp;
		private static enum Action {
			ACCEPT, REJECT,MODIFY
		}
		private Action action;
		private FilterModifier fm;
		
		public FilterChain( String action, FilterExpression exp ) {
			this.action = Action.valueOf(action.toUpperCase());
			this.exp = exp;
		}
		
		public static FilterModifier buildChain( List<FilterChain> chainLinks ) {

			for( FilterChain fc: chainLinks )
				fc.fm = fc.build();
			
			return a->{
				ObjectNode flatJson = a;
				for( FilterChain fc: chainLinks ) {
					ObjectNode newFlatJson = fc.fm.apply(flatJson);
					if( fc.action == Action.ACCEPT ) {
						if( newFlatJson!=null ) return newFlatJson;
					} else if( fc.action == Action.REJECT ) {
						if( newFlatJson==null ) return null;
					}
					flatJson = newFlatJson;
				}
				// shouldnt get here with correct chain
				return flatJson;
			};
		}
		
		private FilterModifier build() {
			if(( exp == null ) && ( action==Action.MODIFY ))
				return null;
			if( action == Action.MODIFY ) {
				return exp.buildModifier();
			}
			if( action == Action.ACCEPT ) {
				if( exp == null ) return a->a;
				else {
					final FilterValue fv = exp.buildFilter();
					return a->fv.test(a)?a:null;
				}
			}
			if( action == Action.REJECT ) {
				if( exp == null ) return a->null;
				else {
					final FilterValue fv = exp.buildFilter();
					return a->fv.test(a)?null:a;
				}
			}
			// we should not be getting here
			return null;
		}	
	}
	
	public static FilterModifier buildFromJson( ObjectNode filters ) {
		FilterModifier res = null;
		if( filters == null ) return null;
		
 		// atomicFilters{letter is key}.{letter, functionName, negate, fieldName, values[]}
		// combineFilters{letter is key}.{letter, functionName, value[].{negate, letter}
		// filterChain[].{type(accept, modify,reject),value( a letter or null ) 
		HashMap<String, FilterExpression> filterExpressionsByLetter = new HashMap<>();
		
		if( filters.has( "atomicFilters" )) {
			for( String letter: (Iterable<String>) ()->filters.get( "atomicFilters").fieldNames()) {
				ObjectNode atomicFilter = (ObjectNode) filters.get( "atomicFilters").get( letter );
				String fieldName = atomicFilter.path( "fieldName").asText();
				boolean negate = atomicFilter.path( "negate").asBoolean();
				boolean ignoreCase = atomicFilter.path( "ignoreCase").asBoolean();
				BasicFilterExpression bfe = new BasicFilterExpression(  atomicFilter.path( "functionName").asText()
						, fieldName, negate, ignoreCase );
				for( JsonNode arg: (ArrayNode)atomicFilter.path( "values")) {
					bfe.addArgument(arg.asText());
				}
				filterExpressionsByLetter.put( atomicFilter.path( "letter").asText(), bfe);
			}
		}
		
		if( filters.has( "combineFilters" )) {
			boolean addedFilter = true;
			while( addedFilter ) {
				addedFilter = false;
				// like this we only build non circular filters
				for( String letter: (Iterable<String>) ()->filters.get( "combineFilters").fieldNames()) {
					if( filterExpressionsByLetter.containsKey(letter )) continue;
					
					ObjectNode combineFilter = (ObjectNode) filters.get( "combineFilters").get( letter );
					// can I build this filter, do all the dependencies exist?
					boolean canBuild = true;
					for( JsonNode val: (ArrayNode) combineFilter.path( "values")) {
						if( !filterExpressionsByLetter.containsKey(val.path("letter").asText()))
							canBuild = false;
					}
					if( !canBuild ) continue;

					boolean negate = combineFilter.path( "negate").asBoolean();

					CombineFilterExpression cfe = new CombineFilterExpression( 
							combineFilter.path( "functionName" ).asText(), negate );
					
					for( JsonNode val: (ArrayNode) combineFilter.path( "values")) {
						String argLetter = val.path( "letter").asText();
						boolean argNegate = val.path( "negate" ).asBoolean();
						FilterExpression argFilter = filterExpressionsByLetter.get( argLetter );
						if( argFilter != null )
							cfe.addArgument( argFilter, argNegate );
					}
					filterExpressionsByLetter.put( letter, cfe);
					addedFilter = true;
				}
			}
		}
		
		if( filters.has( "filterChain")) {
			Spliterator<JsonNode> chainJson = ((ArrayNode)filters.get( "filterChain")).spliterator();
			List<FilterChain> chain = 
					StreamSupport.stream( chainJson, false)
					.map( node -> {
						String action = node.path( "type" ).asText();
						String letter = node.path( "letter").asText();
						FilterExpression fe = filterExpressionsByLetter.get( letter );
						return new FilterChain( action, fe );
					})
					.filter( link->( link!= null ))
					.collect(Collectors.toList());
			res = FilterChain.buildChain(chain);
		}
		
		return res;
	}
}
