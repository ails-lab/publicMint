package gr.ntua.ivml.mint.db;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import gr.ntua.ivml.mint.util.JSONUtils;
import gr.ntua.ivml.mint.util.Tuple;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;


/**
 * Class to use a key value store table.
 * 
 * @author Arne Stabenau 
 *
 */
public class Meta {
	
	static private final SimpleDateFormat timestamp = new SimpleDateFormat( "dd/MM/yy HH:mm:ss" );
	private static final Logger log = Logger.getLogger( Meta.class );
	
	/**
	 * Generate a key based on objects class and, if it is a database object, dbID
	 * @param object
	 * @return the key (example: gr.ntua.ivml.mint.persistent.User[1000])
	 */
	public static String generateKey(Object object) {
		Long id = new Long(0);
		Class<?> objectClass = object.getClass();
		try {
			Method dbID = objectClass.getMethod("getDbID");
			if(dbID != null) id = (Long) dbID.invoke(object);
		} catch (Exception e) {
			e.printStackTrace();
		}
		String name = object.getClass().getName();
		
		//Remove hibernate name for proxy class 
		
		name  = name.replaceAll("_\\$\\$_.*$", "");
		
		return name + "[" + id.longValue() + "]"; 
	}
	
	/**
	 * Generate a key based on the objects class, the object's dbID if it exists, and a property name
	 * @param object
	 * @param property
	 * @return the key (example gr.ntua.ivml.mint.persistent.User[1000].property)
	 */
	public static String generateKey(Object object, String property) {
		return Meta.generateKey(object) + "." + property;
	}


	/**
	 * Puts key and value String in db. If key exists, the value is replaced. 
	 * @param key
	 * @param value
	 */
	public static void put( String key, String value ) {
		String result = Meta.get(key);
		if(result == null) {
			DB.getStatelessSession().createSQLQuery("insert into meta( meta_id, meta_key, meta_value ) " +
					" values( nextval('seq_meta_id'), ?, ? )")
					.setParameter( 0, key )
					.setParameter( 1, value )
					.executeUpdate();
		} else {
			DB.getStatelessSession().createSQLQuery("update meta set meta_value = ? where meta_key = ?")
					.setParameter( 0, value)
					.setParameter( 1, key)
					.executeUpdate();
		}

		DB.commitStateless();
	}
	
	/**
	 * Puts assigns a value to a key generated by the specified object and property
	 * @param object
	 * @param property
	 * @param value
	 */
	public static void put(Object object, String property, String value ) {
		String key = Meta.generateKey(object, property);
		Meta.put(key, value);
	}
	
	
	public static void putTimestamp( Object object, String property ) {
		String time = timestamp.format(new Date());
		put( object, property, time );
	}
	
	public static Date getTimestamp( Object object, String property ) {
		String date = get( object, property );
		if( date == null ) return null;
		try {
			return timestamp.parse(date);
		} catch( ParseException pe ) {
			log.error( "Not a date in " + property );
			return null;
		}
	}
	
	/**
	 * Return null on not found. Gets the first entry with this key.
	 * Multiple same key entries are allowed, but not recommended.
	 * 
	 * You can get the with getLike() out again.
	 * @param key
	 * @return
	 */
	public static String get( String key ) {
		String res = (String ) DB.getStatelessSession().createSQLQuery( "select meta_value from meta where meta_key = ? order by meta_id")
		.setParameter(0,key)
		.uniqueResult();
		return res;
	}
	
	/**
	 * Gets a value stored for this object's property.
	 * @param object
	 * @param property
	 * @return the value
	 */
	public static String get(Object object, String property) {
		String key = Meta.generateKey(object, property);
		return Meta.get(key);
	}
	
	/**
	 * Delete all entries with exactly the given key.
	 * @param key
	 */
	public static void delete( String key ) {
		DB.getStatelessSession().createSQLQuery("delete from meta " +
		" where meta_key = ?")
		.setParameter( 0, key )
		.executeUpdate();	
		DB.commitStateless();
	}
	
	/**
	 * Deletes entries for the specified object and property.
	 * @param object
	 * @param property
	 */
	public static void delete(Object object, String property) {
		String key = Meta.generateKey(object, property);
		Meta.delete(key);
	}
	
	/**
	 * Get a list of key values that fit the 'LIKE' pattern
	 * @param pattern
	 * @return
	 */
	public static List<Tuple<String, String>> getLike( String pattern ) {
		// broken on oreo ..
		List<Object[]> queryRes = (List<Object[]>) DB.getStatelessSession().createSQLQuery( "select meta_key, meta_value from meta where meta_key like ? order by meta_id")
		.setParameter( 0, pattern)
		.list();
		if(( queryRes == null) || ( queryRes.isEmpty())) return Collections.emptyList();

		List<Tuple<String, String>> res = new ArrayList<Tuple<String, String>>();
		for( Object[] s2: queryRes ) {
			res.add( new Tuple<String, String>( s2[0].toString(), s2[1].toString()));
		}
		return res;
	}

	
	/**
	 * Gets all entries for the specified object.
	 * @param object
	 * @return
	 */
	public static List<Tuple<String, String>> getAllProperties(Object object) {
		String key = Meta.generateKey(object) + ".%";
		return Meta.getLike(key);
	}
	
	/**
	 * Get all property keys for the specified object
	 * @param object
	 * @return
	 */
	public static List<String> getAllPropertyKeys(Object object) {
		return Meta.getAllPropertyKeys(object, null);
	}

	/**
	 * Get all property keys for the specified object that start with a specific string
	 * @param object
	 * @param startsWith string that should exist at the start of the property. Set to null to get all properties
	 * @return
	 */
	public static List<String> getAllPropertyKeys(Object object, String startsWith) {
		String key = Meta.generateKey(object);
		if(startsWith != null) key += "." + startsWith;
		List<String[]> queryRes = (List<String[]>) DB.getStatelessSession().createSQLQuery( "select meta_key from meta where meta_key like ? order by meta_id")
		.setParameter( 0, key + "%" )
		.list();
		if(( queryRes == null) || ( queryRes.isEmpty())) return Collections.emptyList();

		List<String> res = new ArrayList<String>();
		for( Object[] s2: queryRes ) {
			res.add( s2[0].toString() );
		}
		return res;
	}

	
	/**
	 * Delete entries where the key fits the 'LIKE' pattern.
	 * @param pattern
	 */
	public static void deleteLike( String pattern ) {
		DB.getStatelessSession().createSQLQuery("delete from meta " +
		" where meta_key like ?")
		.setParameter( 0, pattern )
		.executeUpdate();		
		DB.commitStateless();
	}
	
	/**
	 * Deletes all entries for the specified object.
	 * @param object
	 */
	public static void deleteAllProperties( Object object ) {
		String key = Meta.generateKey(object) + ".%";
		Meta.deleteLike(key);
	}
	
	/**
	 * helps with testing
	 */
	
	public static int countLike( String pattern ) {
		BigInteger res = (BigInteger ) DB.getStatelessSession().createSQLQuery( "select count(*) from meta where meta_key like ?")
		.setParameter(0,pattern)
		.uniqueResult();
		
		return res.intValue();
	}
	
	/*
	 *	JSON wrappers 
	 */
	
	/**
	 * Get and parse a json object meta property of a specified object.
	 * @param object
	 * @param key key of the meta property.
	 * @return JSONObject with contents of meta property, or an empty JSONObject if parsing fails.
	 */
	public static JSONObject getObject(Object object, String key) {
		String property = Meta.get(object, key);
		JSONObject result = new JSONObject();
		
		if(property != null) {
			try {
				result = JSONUtils.parse(property);
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
		
		return result;		
	}
	
	/**
	 * Get and parse a json array meta property of a specified object.
	 * @param object
	 * @param key key of the meta property.
	 * @return JSONArray with contents of meta property, or an empty JSONArray if parsing fails.
	 */
	public static JSONArray getArray(Object object, String key) {
		String property = Meta.get(object, key);
		JSONArray result = new JSONArray();
		
		if(property != null) {
			try {
				result = JSONUtils.parseArray(property);
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
		
		return result;		
	}

	/**
	 * Add a json object at the start of a json array saved as a meta property of an object.
	 * @param object
	 * @param key key of the meta property.
	 * @param entry entry to add at the start of the meta property array.
	 * @return
	 */
	public static JSONArray prepend(Object object, String key, JSONObject entry) {
		JSONArray array = Meta.getArray(object, key);
		array.add(0, entry);
		Meta.put(object, key, array.toString());
		return array;
	}
	
	/**
	 * Add a json object at the end of a json array saved as a meta property of an object.
	 * @param object
	 * @param key key of the meta property.
	 * @param entry entry to add at the end of the meta property array.
	 * @return
	 */
	public static JSONArray append(Object object, String key, JSONObject entry) {
		JSONArray array = Meta.getArray(object, key);
		array.add(entry);
		Meta.put(object, key, array.toString());
		return array;
	}
}