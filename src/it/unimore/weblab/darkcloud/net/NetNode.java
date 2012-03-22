package it.unimore.weblab.darkcloud.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.net.ssl.SSLSocketFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import it.unimore.weblab.darkcloud.db.Db;
import it.unimore.weblab.darkcloud.db.Tuple;
import it.unimore.weblab.darkcloud.db.Db.Table;
import it.unimore.weblab.darkcloud.net.DarkCloud.DarkCloudNodeType;
import it.unimore.weblab.darkcloud.protocol.Field;
import it.unimore.weblab.darkcloud.protocol.ProtocolException;
import it.unimore.weblab.darkcloud.protocol.Request;
import it.unimore.weblab.darkcloud.protocol.Response;
import it.unimore.weblab.darkcloud.util.CryptUtil;

/**
 * Class which models a generic network node
 * @author blacklight
 */
public abstract class NetNode {
	protected String hostname;
	protected int listenPort;
	protected DarkCloudNodeType type;
	private boolean alive;
	private long pingTimeMsec;
	private Key pubKey;
	
	/**
	 * Since we are dealing with an abstract class, only its direct sub-classes can call the constructor
	 * @param hostname
	 * @param listenPort
	 * @param type
	 */
	protected NetNode(String hostname, int listenPort, DarkCloudNodeType type)
	{
		this.hostname = hostname;
		this.listenPort = listenPort;
		this.type = type;
		alive = true;
	}

	/**
	 * @return The node's name/host/IP
	 */
	public String getName() {
		return hostname;
	}

	/**
	 * @return The node's listen port
	 */
	public int getPort() {
		return listenPort;
	}

	/**
	 * @return The node's type, CLIENT or SERVER
	 */
	public DarkCloudNodeType getType() {
		return type;
	}
	
	/**
	 * @return true if the node is alive, i.e. if it replied to our PING and generic requests, false otherwise
	 */
	public boolean isAlive() {
		return alive;
	}
	
	/**
	 * Set the alive status of the node
	 * @param alive
	 */
	public void setAlive(boolean alive) {
		this.alive = alive;
	}
	
	/**
	 * @return The RTT in msec of the latest ping request
	 */
	public long getPingTimeMsec() {
		return pingTimeMsec;
	}
	
	/**
	 * Set the RTT in msec of the latest ping request
	 * @param pingTimeMsec
	 */
	public void setPingTimeMsec(long pingTimeMsec) {
		this.pingTimeMsec = pingTimeMsec;
	}
	
	/**
	 * Send a request to the node and reads the response
	 * @param req Request to be sent
	 * @return Response object, if any
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws ProtocolException
	 * @throws SignatureException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 */
	public Response send(Request req) throws UnknownHostException, IOException, ParserConfigurationException, SAXException, ProtocolException, InvalidKeyException, NoSuchAlgorithmException, SignatureException
	{
		Socket sock = SSLSocketFactory.getDefault().createSocket(hostname, listenPort);
		sock.setSoTimeout(DarkCloud.getInstance().getMsecResponseTimeout());
		BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
		
		req.appendField(
			new Field("nodeid").setContent(
				DarkCloud.getNodeKey(
					sock.getLocalAddress().getHostAddress(),
					DarkCloud.getInstance().getListenPort())));
		
		out.write(req.toString());
		out.flush();
		
		int resLen = Integer.parseInt(in.readLine());
		
		if (resLen < 1) {
			throw new NumberFormatException("The provided request has an invalid length");
		}
		
		String sign = in.readLine();
		char[] buf = new char[resLen];
		in.read(buf, 0, resLen);
		in.close();
		out.close();
		sock.close();
		
		Response resp = Response.fromString(new String(buf));
		
		/// SIGNATURE CHECK START ///
		Field nodeidField = resp.getField("nodeid");
		
		if (nodeidField == null) {
			throw new ProtocolException("No nodeid field specified in this request");
		}
		
		String nodeid = nodeidField.getContent();
		
		if (nodeid == null) {
			throw new ProtocolException("No nodeid field specified in this request");
		}
		
		nodeid = nodeid.trim();
		NetNode node = null;
		HashMap<String, NetNode> clientNodes=DarkCloud.getInstance().getClientNodes();
		HashMap<String, NetNode> serverNodes=DarkCloud.getInstance().getServerNodes();
		if(clientNodes.containsKey(nodeid)){node = DarkCloud.getInstance().getClientNodes().get(nodeid);}
		if(serverNodes.containsKey(nodeid)){node = DarkCloud.getInstance().getServerNodes().get(nodeid);}
		
		
		if (node == null) {
			throw new ProtocolException("No server or client node was found with id " + nodeid);
		}
		
		PublicKey pubkey = (PublicKey) node.getPubKey();
		
		if (pubkey == null) {
			Field pubkeyField = resp.getField("pubkey");
			
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
		
		if (!CryptUtil.verifySign(new String(buf), sign, pubkey)) {
			throw new ProtocolException("The provided signature is invalid");
		}
		/// SIGNATURE CHECK END ///
			
		return resp;
	}
	
	/**
	 * Set the public key for this node
	 * @param k Key to be set
	 */
	public void setPubKey(Key k) {
		pubKey = k;
	}
	
	/**
	 * @return The public key for this node
	 */
	public Key getPubKey() {
		if (pubKey == null) {
			String nodeid = DarkCloud.getNodeKey(hostname, listenPort);
			ArrayList<ArrayList<String>> res = null;
			
			try {
				res = DarkCloud.getInstance().getDb().select("SELECT pubkey FROM " +
					Table.NODE.toString() + " WHERE nodeid='" + nodeid + "'");
				
				if (res.isEmpty()) {
					return null;
				}
				
				pubKey = CryptUtil.pubKeyFromString(res.get(0).get(0));
			} catch (SQLException e) {
				return null;
			}
		}
		
		return pubKey;
	}
	
	/**
	 * Store the network node to the database
	 * @throws SQLException 
	 */
	public void storeNode() throws SQLException
	{
		Db db = DarkCloud.getInstance().getDb();
        System.out.println("***** LISTEN PORT: " + listenPort);
		Tuple nodeData = new Tuple().
			setField("nodeid", DarkCloud.getNodeKey(hostname, listenPort)).
			setField("pubkey", (pubKey == null) ? null : CryptUtil.pubKeyToString(pubKey)).
			setField("type", new Integer(type.ordinal())).
			setField("addr", hostname).
			setField("port", listenPort);
		
		try {
			db.insert(Table.NODE, nodeData);
		} catch (SQLException e) {
			// The tuple already exists, attempt to update it
			db.update(Table.NODE, nodeData, "nodeid='" + DarkCloud.getNodeKey(hostname, listenPort) + "'");
		}
	}
}
