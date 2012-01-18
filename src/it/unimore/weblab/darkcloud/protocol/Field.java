package it.unimore.weblab.darkcloud.protocol;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class which models a generic field in a DarkCloud request
 * @author blacklight
 */
public class Field {
	private String name;
	private String content;
	private HashMap<String, String> attributes;
	
	/**
	 * Constructor
	 * @param name Field name
	 */
	public Field(String name) {
		this.name = name;
		attributes = new HashMap<String, String>();
	}

	/**
	 * @return The content of the field, if any
	 */
	public String getContent() {
		return content;
	}

	/**
	 * Set the content for this field
	 * @param content
	 * @return The updated Field object
	 */
	public Field setContent(String content) {
		this.content = content;
		return this;
	}
	
	/**
	 * Append a new attribute to this field object
	 * @param name Attribute name
	 * @param value Attribute value
	 * @return The updated Field object
	 */
	public Field appendAttribute(String name, String value) {
		attributes.put(name, value);
		return this;
	}

	/**
	 * @return The name of this field
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * @return An array which contains the names of the attributes set for this field
	 */
	public ArrayList<String> getAttributesNames() {
		return new ArrayList<String>(attributes.keySet());
	}
	
	/**
	 * @param attr Attribute name to fetch
	 * @return The content of the attribute <i>attr</i>, null if that attribute does not exist in this field
	 */
	public String getAttribute(String attr) {
		return attributes.get(attr);
	}
    
    /**
     * Alias for appendAttribute
     * @param name
     * @param value
     * @return
     */
	public Field setAttribute(String name, String value) {
		return appendAttribute(name, value);
	}
}
