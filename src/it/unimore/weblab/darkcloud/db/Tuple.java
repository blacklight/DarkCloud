package it.unimore.weblab.darkcloud.db;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Simple wrapper class for a database tuple
 * @author blacklight
 */
public class Tuple {
	private HashMap<String, Object> tuple;
	
	/**
	 * Default constructor
	 */
	public Tuple() {
		tuple = new HashMap<String, Object>();
	}
	
	/**
	 * Get the value of a tuple field, null if the field was not found
	 * @param fieldname
	 * @return
	 */
	public Object getField(String fieldname) {
		return tuple.get(fieldname);
	}
	
	/**
	 * Set the value for a tuple field
	 * @param fieldname
	 * @param fieldvalue
	 * @return The updated object
	 */
	public Tuple setField(String fieldname, Object fieldvalue) {
		tuple.put(fieldname, fieldvalue);
		return this;
	}
	
	/**
	 * @return The fields names for this tuple
	 */
	public ArrayList<String>getFields() {
		return new ArrayList<String>(tuple.keySet());
	}
}
