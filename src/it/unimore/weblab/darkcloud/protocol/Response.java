package it.unimore.weblab.darkcloud.protocol;

import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.util.CryptUtil;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Object for a Response message on the network
 * @author blacklight
 */
public class Response extends Message {
	/**
	 * Response types
	 * @author blacklight
	 */
	public enum ResponseType implements MessageType
	{
		ACK,
		ERROR
	};
	
	/** Auto-incremental counter of the performed responses */
	private static int seqNum = 0;
	
	/**
	 * Constructor
	 * @param type Response type
	 */
	public Response(MessageType type) {
		super(type);
		
		synchronized(this) {
			seqNum++;
		}
	}
	
	/**
	 * Convert this Response object to an XML string ready to be sent over the network
	 */
	@Override
	public String toString() {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<response seq=\"" + seqNum +
			"\" type=\"" + type.toString() + "\">\n" + super.toString() + "</response>\n";
		
		String sign = "";
		
		try {
			sign = CryptUtil.sign(xml, (PrivateKey) DarkCloud.getInstance().getPrivateKey());
		} catch (Exception e) {
			DarkCloud.getInstance().getLogger().error("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
			return null;
		}
		
		xml = xml.length() + "\n" + sign + "\n" + xml;
		return xml;
	}
	
	/**
	 * Creates a new Response object from an XML string
	 * @param xmlString
	 * @return The new Response object
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws ProtocolException
	 */
	public static Response fromString(String xmlString) throws ParserConfigurationException, SAXException, IOException, ProtocolException {
		Message m = Message.fromString(xmlString);
		Response res = new Response(m.type);
		res.content = m.content;
		res.fields = m.fields;
		
		InputSource is = new InputSource();
		is.setCharacterStream(new StringReader(xmlString));
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		Node rootNode = document.getChildNodes().item(0);
		
		if (!rootNode.getNodeName().toLowerCase().equals("response"))
		{
			throw new ProtocolException("The provided XML respoinse is not a valid response - Missing <response> root node");
		}
		
		if (rootNode.getAttributes().getNamedItem("seq") != null)
		{
			seqNum = Integer.parseInt(rootNode.getAttributes().getNamedItem("seq").getNodeValue());
		}
		
		if (rootNode.getAttributes().getNamedItem("type") != null)
		{
			String typeString = rootNode.getAttributes().getNamedItem("type").getNodeValue();
			ResponseType[] responseTypes = ResponseType.values();
			boolean typeFound = false;
			
			for (int i=0; i < responseTypes.length && !typeFound; i++)
			{
				if (responseTypes[i].toString().trim().toLowerCase().equals(typeString.toLowerCase()))
				{
					res.type = responseTypes[i];
					typeFound = true;
				}
			}
			
			if (!typeFound)
			{
				throw new ProtocolException("The provided XML response contains an invalid value for the \"type\" " +
					"attribute: " + typeString);
			}
		} else {
			throw new ProtocolException("The provided XML response does not contain a valid \"type\" field");
		}
		
		return res;
	}
    
    /**
     * @return The sequence number for this response
     */
	public int getSequence() {
        return seqNum;
	}
}
