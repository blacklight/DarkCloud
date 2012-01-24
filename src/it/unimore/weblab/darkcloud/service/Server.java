package it.unimore.weblab.darkcloud.service;

import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.net.NetNode;
import it.unimore.weblab.darkcloud.protocol.Field;
import it.unimore.weblab.darkcloud.protocol.ProtocolException;
import it.unimore.weblab.darkcloud.protocol.Request;
import it.unimore.weblab.darkcloud.protocol.Request.RequestType;
import it.unimore.weblab.darkcloud.protocol.Response;
import it.unimore.weblab.darkcloud.protocol.ServerResponseMethods;
import it.unimore.weblab.darkcloud.util.CryptUtil;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

/**
 * Thread executed for a Server node
 * @author blacklight
 */
public class Server extends Thread {
	private Socket sock;
	private BufferedReader in;
	private PrintWriter out;
	
	/**
	 * Constructor, it has package visibility
	 * @param sock Socket associated to a performed request
	 * @throws IOException
	 */
	Server(Socket sock) throws IOException
	{
		this.sock = sock;
		in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		out = new PrintWriter(sock.getOutputStream(), true);
	}
	
	/**
	 * Code executed by the thread
	 */
	@Override
	public void run()
	{
		DarkCloud net = null;
		
		try
		{
			net = DarkCloud.getInstance();
			String reqLenStr = in.readLine();
			int reqLen = Integer.parseInt(reqLenStr);
			
			if (reqLen < 1) {
				throw new NumberFormatException("The provided request has an invalid length");
			}
			
			String sign = in.readLine();
			char[] buf = new char[reqLen];
			in.read(buf, 0, reqLen);
			String bufstr = new String(buf);
			Request req = Request.fromString(bufstr);
			
			/// SIGNATURE CHECK START ///
			Field nodeidField = req.getField("nodeid");
			
			if (nodeidField == null) {
				throw new ProtocolException("No nodeid field specified in this request");
			}
			
			String nodeid = nodeidField.getContent();
			
			if (nodeid == null) {
				throw new ProtocolException("No nodeid field specified in this request");
			}
			
			nodeid = nodeid.trim();
			NetNode node = DarkCloud.getInstance().getClientNodes().get(nodeid);
			
			if (node == null) {
				throw new ProtocolException("No client node was found with id " + nodeid);
			}
			
			PublicKey pubkey = (PublicKey) node.getPubKey();
			
			if (pubkey == null) {
				Field pubkeyField = req.getField("pubkey");
				
				if (pubkeyField != null) {
					String pubkeyStr = pubkeyField.getContent();
					
					if (pubkeyStr != null) {
						pubkeyStr = pubkeyStr.trim();
						pubkey = (PublicKey) CryptUtil.pubKeyFromString(pubkeyStr);
						node.setPubKey(pubkey);
					}
				}
				
				if (pubkey == null) {
					throw new ProtocolException("The network node has no associated public key and none was provided");
				}
			}
			
			if (!CryptUtil.verifySign(bufstr, sign, pubkey)) {
				throw new ProtocolException("The provided signature is invalid");
			}
			/// SIGNATURE CHECK END ///
			
			boolean methodFound = false;
			Method methods[] = ServerResponseMethods.class.getMethods();
			
			for (int i=0; i < methods.length && !methodFound; i++)
			{
				String requestType = ((RequestType) req.getType()).toString().trim().toLowerCase();
				String methodName = methods[i].getName().trim().toLowerCase();
				
				if (methodName.equals(requestType))
				{
                    if (requestType.equalsIgnoreCase("ping")) {
    					net.getLogger().debug("[DarkCloud::Debug] " + requestType + " request from " +
    						sock.getRemoteSocketAddress());
                    } else {
    					net.getLogger().info("[DarkCloud::Info] " + requestType + " request from " +
    						sock.getRemoteSocketAddress());
                    }
					
					methodFound = true;
					Response resp = (Response) methods[i].invoke(null, (Request) req);
					
					resp.appendField(
						new Field("nodeid").setContent(
							DarkCloud.getNodeKey(
								sock.getLocalAddress().getHostAddress(),
								DarkCloud.getInstance().getListenPort())));
		
					out.write(resp.toString());
					out.flush();
				}
			}
			
			if (!methodFound) {
				throw new ProtocolException();
			}
		} catch (IOException e)  {
			if (net != null) {
				net.getLogger().error("[DarkCloud::Error] Socket exception" + StackTraceUtil.getStackTrace(e));
			}
		} catch (ParserConfigurationException e) {
			if (net != null) {
				net.getLogger().error("[DarkCloud::Fatal] Parser configuration error" + StackTraceUtil.getStackTrace(e));
			}
		} catch (SAXException e) {
		} catch (ProtocolException e) {
			if (net != null) {
				net.getLogger().error("[DarkCloud::Error] Invalid request according to the protocol" +
					StackTraceUtil.getStackTrace(e));
			}
		} catch (IllegalArgumentException e) {
			net.getLogger().error("[DarkCloud::Error] Illegal argument passed to the protocol method" + StackTraceUtil.getStackTrace(e));
		} catch (IllegalAccessException e) {
			net.getLogger().error("[DarkCloud::Error] Illegal access to the protocol method" + StackTraceUtil.getStackTrace(e));
		} catch (InvocationTargetException e) {
			net.getLogger().error("[DarkCloud::Error] Illegal access to the protocol method" + StackTraceUtil.getStackTrace(e));
		} catch (InvalidKeyException e) {
			net.getLogger().error("[DarkCloud::Error] The provided key for this node is invalid" + StackTraceUtil.getStackTrace(e));
		} catch (NoSuchAlgorithmException e) {
			net.getLogger().error("[DarkCloud::Error] The provided encryption algorithm is invalid" + StackTraceUtil.getStackTrace(e));
		} catch (SignatureException e) {
			net.getLogger().error("[DarkCloud::Error] The provided signature is invalid" + StackTraceUtil.getStackTrace(e));
		} finally {
			try {
				if (in != null) {
					in.close();
				}
				
				if (out != null) {
					out.close();
				}
				
				if (sock != null) {
					sock.close();
				}
			} catch (IOException e)  {}
		}
	}
}
