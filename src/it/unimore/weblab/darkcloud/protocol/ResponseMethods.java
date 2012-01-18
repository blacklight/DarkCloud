package it.unimore.weblab.darkcloud.protocol;

import java.security.Key;
import java.sql.SQLException;

import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.net.NetNode;
import it.unimore.weblab.darkcloud.protocol.Response.ResponseType;
import it.unimore.weblab.darkcloud.util.CryptUtil;

/**
 * Interface which implements static methods called via reflection when a request is received. All the methods take a Request object as parameter and return a Response object
 * @author blacklight
 */
public abstract class ResponseMethods {
	protected static void validateRequest(Request req) throws ProtocolException
	{
		Field nodeidField = req.getField("nodeid");
		
		if (nodeidField == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warn] No nodeid was specified in the provided request");
			throw new ProtocolException("No nodeid was specified in the provided request");
		}
		
		String nodeid = nodeidField.getContent();
		
		if (nodeid == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warn] No nodeid was specified in the provided request");
			throw new ProtocolException("No nodeid was specified in the provided request");
		}
		
		nodeid = nodeid.trim();
		NetNode node = DarkCloud.getInstance().getClientNodes().get(nodeid);
		
		if (node == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warn] The provided nodeid is unknown to me");
			throw new ProtocolException("The provided nodeid is unknown to me");
		}
		
		Field pubkeyField = req.getField("pubkey");
		
		if (pubkeyField == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warn] No pubkey field was provided for this request");
			throw new ProtocolException("No pubkey field was provided for this request");
		}
		
		String pubkey = pubkeyField.getContent();
			
		if (pubkey == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warn] No pubkey field was provided for this request");
			throw new ProtocolException("No pubkey field was provided for this request");
		}
		
		pubkey = pubkey.trim();
		Key nodepubkey = node.getPubKey();
		
		if (nodepubkey == null)
		{
			// TODO Fetch the key from the database
			node.setPubKey(CryptUtil.pubKeyFromString(pubkey));
		} else {
			if (!CryptUtil.pubKeyToString(nodepubkey).trim().equals(pubkey))
			{
				DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warn] The provided public key for the node does not match with the one I know");
				throw new ProtocolException("The provided public key for the node does not match with the one I know");
			}
		}
	}
	
	/**
	 * Ping request
	 */
	public static Response ping(Request req)
	{
		String pubkey = null;
		String nodeid = null;
		
		Field pubkeyField = req.getField("pubkey");
		
		if (pubkeyField != null) {
			pubkey = pubkeyField.getContent();
			
			if (pubkey != null) {
				pubkey = pubkey.trim();
			}
		}
		
		Field nodeidField = req.getField("nodeid");
		
		if (nodeidField != null) {
			nodeid = nodeidField.getContent();
			
			if (nodeid != null) {
				nodeid = nodeid.trim();
			}
		}
		
		if (nodeid != null && pubkey != null) {
			boolean isClient = false;
			boolean isServer = false;
			
			if (DarkCloud.getInstance().getClientNodes().get(nodeid) != null) {
				isClient = true;
			} else if (DarkCloud.getInstance().getServerNodes().get(nodeid) != null) {
				isServer = true;
			}
			
			if (isClient) {
				DarkCloud.getInstance().getClientNodes().get(nodeid).setPubKey(CryptUtil.pubKeyFromString(pubkey));
				
				try {
					DarkCloud.getInstance().getClientNodes().get(nodeid).storeNode();
				} catch (SQLException e) {}
			} else if (isServer) {
				DarkCloud.getInstance().getServerNodes().get(nodeid).setPubKey(CryptUtil.pubKeyFromString(pubkey));
				
				try {
					DarkCloud.getInstance().getServerNodes().get(nodeid).storeNode();
				} catch (SQLException e) {}
			}
		}
		
		return new Response(ResponseType.ACK);
	}
}
