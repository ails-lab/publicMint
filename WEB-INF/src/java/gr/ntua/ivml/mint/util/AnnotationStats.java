package gr.ntua.ivml.mint.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gr.ntua.ivml.mint.persistent.Enrichment;

/**
 * 
 * Make stats for general flat json and some extra for the annotation flat json
 * @author arne
 *
 */
public class AnnotationStats {
	
	// any field, any value, added and counted,
	private HashMap< String, HashMap<String, Integer>> basicValues = new HashMap<>();
	
	private static class ValueCount {
		public String value;
		public int count;
		
		public ValueCount( String value, int count ) {
			this.value= value;
			this.count=count;
		}
	}
	
	// extract the limit most frequent values from the counts table and give them
	// back in a sorted list
	public List<ValueCount> convertToTable( HashMap<String, Integer> counts, int limit ) {
		List<ValueCount> tmp =
		counts.entrySet().stream()
			.map( e ->  new ValueCount( e.getKey(), e.getValue()))
			.sorted( 
					Comparator
						.comparingInt( (ValueCount val)->val.count)
						.reversed()
						.thenComparing( vc->vc.value )
					)
			.collect( Collectors.toList());
		
		if( tmp.size()> limit ) {
			List<ValueCount> res = new ArrayList<>();
			
			res.addAll( tmp.subList( 0, limit-1 ));
			if( counts.containsKey("<empty>")) {
				// we want the empty if its not already in res
				if( !res.stream().anyMatch( vc->vc.value.equals( "<empty>" ))) {
					res.remove( limit-1 );
					res.add( new ValueCount( "<empty>", counts.get( "<empty>")));
				}	
			}
			res.add( new ValueCount( "<other>", 
					tmp.subList(limit-1, tmp.size()).stream()
						.filter( vc->!vc.value.equals("<empty>"))
						.mapToInt(vc->vc.count)
						.sum()));
			res.add( new ValueCount( "<distinct>", counts.size()));
			return res;
		} else {
			return tmp;
		}
	}
	
	// stats for flat json, that should work on any csv in json format as well
	public void simpleStats( Collection<ObjectNode> flattenedData ) {
		// find the keynames
		Set<String> keys = new HashSet<>();
		for( ObjectNode annotationRecord: flattenedData ) 
			for( String key: (Iterable<String>) ()->annotationRecord.fieldNames()) 
				keys.add( key );

		for( ObjectNode annotationRecord: flattenedData ) {
			for( String key: keys ) {
				Map<String, Integer> counters = basicValues.computeIfAbsent(key, k->new HashMap<String, Integer>());
				if( annotationRecord.has( key )) {
					String value = annotationRecord.get( key ).asText();
					if( StringUtils.empty(value))
						counters.merge("<empty>", 1, Integer::sum );
					else
						counters.merge( value, 1, Integer::sum );
				} else {
					counters.merge("<empty>", 1, Integer::sum );
				}
			}
		}
	}
	
	public ObjectNode createStats( Collection<ObjectNode> flattenedData, int limitEntries ) {
		ObjectNode res = Jackson.om().createObjectNode();
		ObjectNode stats = res.objectNode();
		ObjectNode byField = res.objectNode();
		stats.set( "byField", byField );
		res.set( "stats", stats );
		
		// read the values into basicValues, create <empty> entries where needed
		simpleStats( flattenedData );
		
		
				
		// for each of the keys (columns in csv) make a value table
		for( String fieldName: basicValues.keySet()) {
			ObjectNode fieldStats = res.objectNode();
			byField.set( fieldName, fieldStats );
			
			ArrayNode columns = byField.arrayNode();
			columns.add( fieldName );
			columns.add( "count");
			
			ArrayNode allValues = byField.arrayNode();
			ArrayNode values = byField.arrayNode();
			ArrayNode counts = byField.arrayNode();
			allValues.add( values);
			allValues.add( counts );
			fieldStats.set( "columns", columns );
			fieldStats.set("values", allValues);
			fieldStats.put("description", "Shows all the values in the '" + fieldName 
					+ "' field. If there are more than 20, gives the <distinct> count and how often the <other> values appear."  ); 
			HashMap<String, Integer> counters = basicValues.get( fieldName);
			for( ValueCount vc: convertToTable( counters, limitEntries )) {
				values.add( vc.value );
				counts.add( vc.count );
			}
		}
		return res;
	}
	
	private void buildTargetFieldStats( Enrichment e, List<ObjectNode> flatJson ) {
		// build record and annotation count and distinct entries per targetField
		// record and annotation count per annotator if there is more than one
		// targetField:uri if there are less than 20 for a target field, make record count 
		HashMap<String, HashSet<String>> counters = new HashMap<>();
		for( ObjectNode on: flatJson ) {
			String targetField = StringUtils.getDefault( on.path( "targetField").asText(), "<empty>" );
			String entry = StringUtils.getDefault( on.path( "uri").asText(), on.path( "textValue" ).asText(), "<empty>" );
			String recordId = StringUtils.getDefault( on.path( "recordId").asText(), "<empty>" );
			HashSet<String> ids = counters.computeIfAbsent(targetField+" entry", k->new HashSet<>());
			ids.add( entry );
			ids = counters.computeIfAbsent(targetField+" record", k->new HashSet<>());
			ids.add( recordId );
			ids = counters.computeIfAbsent(targetField, k->new HashSet<>());
			ids.add( entry+recordId );
		}
		
		ArrayNode columns = Jackson.om().valueToTree(new String[] { "targetField", "records annotated", "distinct annotations", "annotations total" });
		ArrayList<String> targetFields = new ArrayList<>();
		targetFields.addAll(  basicValues.get("targetField").keySet());
		targetFields.remove( "<empty>");
		ArrayNode valuesAll = columns.arrayNode();
		
		valuesAll.add (Jackson.om().valueToTree( targetFields ));
		
		for( String column: Arrays.asList( " record", " entry", "" )) {
			ArrayNode values = columns.arrayNode();
			for( String targetField: targetFields ) {
				if( counters.containsKey(targetField+column)) 
					values.add( counters.get( targetField+column).size());
				else values.add( 0 );
			}
			valuesAll.add( values );
		}

		ObjectNode targetFieldStat = Jackson.om().createObjectNode();
		targetFieldStat.put( "description", "Per inserted target field we count, how many records were annotated, "
				+"how many different annotations exist for that field and how many total annotations can be made.");
		targetFieldStat.put( "title", "target field specific counters of annotations");
		targetFieldStat.set( "values", valuesAll );
		targetFieldStat.set( "columns", columns );
		ObjectNode stats = e.getStats();
		((ObjectNode)stats.get("stats")).withArray("other").add( targetFieldStat );
		e.setStats(stats);
	}
	
	// create stats and sets the stats json in enrichment 
	public static void buildStats( Enrichment e ) {
		AnnotationStats as = new AnnotationStats();
		List<ObjectNode> flatJson = e.streamFlatJson().collect( Collectors.toList());
		e.setStats( as.createStats( flatJson, 20 ));
		// if enrichment is annotation json-ld we should create more sophisticated stats 
		if( e.getFormat() != Enrichment.Format.ANNOTATION_JSONLD ) return;
		as.buildTargetFieldStats(e, flatJson);
		
		
	}

}
