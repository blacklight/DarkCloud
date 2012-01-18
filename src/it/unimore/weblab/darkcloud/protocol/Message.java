package it.unimore.weblab.darkcloud.protocol;

import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.util.CryptUtil;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Abstract class which models a generic message on the darknet
 * @author blacklight
 */
public abstract class Message {
	/**
	 * Mock interface, only used as base for the enum objects in the derived classes
	 * @author blacklight
	 */
	protected interface MessageType {};
	
	protected MessageType type;
	protected String content;
	protected ArrayList<Field> fields;
	
	/**
	 * Constructor. Since this is an abstract class, only its direct sub-classes may access this constructor
	 * @param type Type for this message
	 */
	protected Message(MessageType type) {
		this.type = type;
		fields = new ArrayList<Field>();
	}
	
	/**
	 * @return The type of this message
	 */
	public MessageType getType() {
		return type;
	}
	
	/**
	 * @return The content of this message, if any
	 */
	public String getContent() {
		return content;
	}
	
	/**
	 * @return The list of fields for this message, if any
	 */
	public ArrayList<Field> getFields() {
		return fields;
	}
	
	/**
	 * Get a field by name
	 * @param fieldname Field name to get
	 * @return The field object, if it exists, otherwise null
	 */
	public Field getField(String fieldname)
	{
		Field f = null;
		
		for (Field ff: fields)
		{
			if (ff.getName().equalsIgnoreCase(fieldname))
			{
				f = ff;
				break;
			}
		}
		
		return f;
	}
	
	/**
	 * Set the content for this message
	 * @param content
	 * @return The updated Message object
	 */
	public Message setContent(String content) {
		this.content = content.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;");
		return this;
	}
	
	/**
	 * Append a new field to this message
	 * @param f
	 * @return The updated Message object
	 */
	public Message appendField(Field f) {
		fields.add(f);
		return this;
	}
	
	/**
	 * Converts this message to an XML string, ready to be sent over the network
	 */
	@Override
	public String toString()
	{
		String xml = "";
		boolean hasPubKey = false;
		
		if (content != null) {
			xml += "\t<content><![CDATA[" + content + "]]></content>\n";
		}
		
		if (fields != null)
		{
			if (!fields.isEmpty())
			{
				for (int i=0; i < fields.size(); i++)
				{
					if (fields.get(i).getName().trim().equalsIgnoreCase("pubkey")) {
						hasPubKey = true;
					}
					
					xml += "\t<" + fields.get(i).getName();
					
					if (!fields.get(i).getAttributesNames().isEmpty())
					{
						ArrayList<String> attributesNames = fields.get(i).getAttributesNames();
						
						for (int j=0; j < attributesNames.size(); j++)
						{
							xml += " " + attributesNames.get(j) + "=\"" +
								fields.get(i).getAttribute(attributesNames.get(j)) + "\"";
						}
					}
					
					xml += ">\n";
					
					if (fields.get(i).getContent() != null)
					{
						xml += "<![CDATA[" + fields.get(i).getContent() + "]]>";
					}
					
					xml += "</" + fields.get(i).getName() + ">\n";
				}
			}
		}
		
		if (!hasPubKey)
		{
			xml += "\t<pubkey><![CDATA[" +
				CryptUtil.pubKeyToString(DarkCloud.getInstance().getPublicKey()) +
				"]]></pubkey>\n";
		}
		
		return xml;
	}
	
	/**
	 * Static method which creates a new Message object from an XML string
	 * @param xmlString
	 * @return The new Message object
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws ProtocolException
	 */
	protected static Message fromString(String xmlString) throws ParserConfigurationException, SAXException, IOException, ProtocolException {
		Message m = new Message(new MessageType() {}) {};
		InputSource is = new InputSource();
		is.setCharacterStream(new StringReader(xmlString));
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		NodeList childNodes = document.getChildNodes();
		
		if (childNodes.getLength() == 0)
		{
			throw new ProtocolException("The provided message does not come as a valid XML\n" + xmlString);
		}
		
		Node rootNode = childNodes.item(0);
		childNodes = rootNode.getChildNodes();
		
		for (int i=0; i < childNodes.getLength(); i++)
		{
			if (childNodes.item(i).getNodeName().toLowerCase().equals("#text"))
				continue;
			
			Node node = childNodes.item(i);
			
			if (node.getNodeName().toLowerCase().equals("content")) {
				m.content = node.getTextContent();
			} else {
				Field f = new Field(node.getNodeName());
				
				if (node.getTextContent() != null)
				{
					if (!node.getTextContent().isEmpty()) {
						f.setContent(node.getTextContent());
					}
				}
				
				NamedNodeMap attrs = node.getAttributes();
				
				if (attrs != null)
				{
					for (int j=0; j < attrs.getLength(); j++)
					{
						f.appendAttribute(attrs.item(j).getNodeName(), attrs.item(j).getNodeValue());
					}
				}
				
				m.appendField(f);
			}
		}
		
		return m;
	}
}
