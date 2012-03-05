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
 * Request message on the network
 * @author blacklight
 */
public class Request extends Message {
	/**
	 * Possible request types
	 * @author blacklight
	 */
	public enum RequestType implements MessageType
	{
		PING,
        GET,
		PUT,
		SHARE,
		RECEIVE
	};
	/** Auto-incremental counter of the performed requests */
	private static int seqNum = 0;
	
	/**
	 * Constructor
	 * @param type Request type
	 */
	public Request(MessageType type) {
		super(type);
		
		synchronized(this) {
			seqNum++;
		}
	}
	
	/**
	 * Generates a string for this request ready to be sent over the network
	 */
	@Override
	public String toString() {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<request seq=\"" + seqNum +
			"\" type=\"" + type.toString() + "\">\n" + super.toString() +
			"</request>\n";
		
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
	 * Creates a Request object from an XML string
	 * @param xmlString
	 * @return The Request object
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws ProtocolException
	 */
	public static Request fromString(String xmlString) throws ParserConfigurationException, SAXException, IOException, ProtocolException {
		Message m = Message.fromString(xmlString);
		Request req = new Request(m.type);
		req.content = m.content;
		req.fields = m.fields;
		
		InputSource is = new InputSource();
		is.setCharacterStream(new StringReader(xmlString));
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		Node rootNode = document.getChildNodes().item(0);
		
		if (!rootNode.getNodeName().toLowerCase().equals("request"))
		{
			throw new ProtocolException("The provided XML request is not a valid request - Missing <request> root node");
		}
		
		if (rootNode.getAttributes().getNamedItem("seq") != null)
		{
			seqNum = Integer.parseInt(rootNode.getAttributes().getNamedItem("seq").getNodeValue());
		}
		
		if (rootNode.getAttributes().getNamedItem("type") != null)
		{
			String typeString = rootNode.getAttributes().getNamedItem("type").getNodeValue();
			RequestType[] requestTypes = RequestType.values();
			boolean typeFound = false;
			
			for (int i=0; i < requestTypes.length && !typeFound; i++)
			{
				if (requestTypes[i].toString().trim().toLowerCase().equals(typeString.toLowerCase()))
				{
					req.type = requestTypes[i];
					typeFound = true;
				}
			}
			
			if (!typeFound)
			{
				throw new ProtocolException("The provided XML request contains an invalid value for the \"type\" " +
					"attribute: " + typeString);
			}
		} else {
			throw new ProtocolException("The provided XML request does not contain a valid \"type\" field");
		}
		
		return req;
	}
    
    /**
     * @return The sequence number for this request
     */
	public int getSequence() {
        return seqNum;
	}
}
