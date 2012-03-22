package it.unimore.weblab.darkcloud.service;

import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.net.NetNode;
import it.unimore.weblab.darkcloud.protocol.ClientResponseMethods;
import it.unimore.weblab.darkcloud.protocol.Field;
import it.unimore.weblab.darkcloud.protocol.ProtocolException;
import it.unimore.weblab.darkcloud.protocol.Request;
import it.unimore.weblab.darkcloud.protocol.Response;
import it.unimore.weblab.darkcloud.protocol.ServerResponseMethods;
import it.unimore.weblab.darkcloud.protocol.Request.RequestType;
import it.unimore.weblab.darkcloud.util.CryptUtil;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.BufferedReader;
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
 * Thread executed for a Client node
 * @author blacklight
 */
public class Client extends Thread {
	private Socket sock;
	private BufferedReader in;
	private PrintWriter out;
	
	/**
	 * Constructor, it has package visibility
	 * @param sock Socket associated to a performed request
	 * @throws IOException
	 */
	Client(Socket sock) throws IOException
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
			int reqLen = Integer.parseInt(in.readLine());
			if (reqLen < 1) {
				throw new NumberFormatException("The provided request has an invalid length");
			}
			in.readLine();
			char[] buf = new char[reqLen];
			in.read(buf, 0, reqLen);
				Request req = Request.fromString(new String(buf));
			boolean methodFound = false;
			Method methods[] = ClientResponseMethods.class.getMethods();
			
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
//	
}
