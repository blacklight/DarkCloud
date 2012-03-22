package it.unimore.weblab.darkcloud.net;

import it.unimore.weblab.darkcloud.net.DarkCloud.DarkCloudNodeType;
import it.unimore.weblab.darkcloud.protocol.Field;
import it.unimore.weblab.darkcloud.protocol.ProtocolException;
import it.unimore.weblab.darkcloud.protocol.Request;
import it.unimore.weblab.darkcloud.protocol.Request.RequestType;
import it.unimore.weblab.darkcloud.protocol.Response;
import it.unimore.weblab.darkcloud.protocol.Response.ResponseType;
import it.unimore.weblab.darkcloud.util.CryptUtil;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

import java.util.ArrayList;

/**
 * Thread object which performs a ping request to a node, in order to verify whether it is alive or not
 * @author blacklight
 */
class NodeChecker extends Thread {
	private NetNode netnode;
	
	/**
	 * Default constructor
	 * @param host
	 * @param port
	 * @param type
	 */
	public NodeChecker(NetNode netnode) {
		this.netnode = netnode;
	}
	
	/**
	 * Executed code
	 */
	@Override
	public void run()
	{
		DarkCloud net = null;
		long startTime = System.currentTimeMillis();
		String nodeid = DarkCloud.getNodeKey(netnode.getName(), netnode.getPort());
		
		try {
			net = DarkCloud.getInstance();
			Request req = new Request(RequestType.PING);
			Response resp = netnode.send(req);
			if (resp.getType() != ResponseType.ACK)
			{
				throw new ProtocolException("Protocol error - The node at " + netnode.getName() + ":" + netnode.getPort() +
					" gave an unexpected response to a PING event - Event type: " + resp.getType());
			}
			
			Field keyfield = resp.getField("pubkey");
			
			if ((keyfield) != null)
			{
				String pubkeystr = keyfield.getContent();
				
				if (pubkeystr != null)
				{
					netnode.setPubKey(CryptUtil.pubKeyFromString(pubkeystr));
				}
			}
			
			long pingTime = System.currentTimeMillis() - startTime;
			net.getLogger().debug("[DarkCloud::Debug] The node at " + netnode.getName() + ":" + netnode.getPort() + " is alive " +
				"{PingTimeMsec " + pingTime + "}");
			if (netnode.getType() == DarkCloudNodeType.SERVER) {	
				net.getServerNodes().get(nodeid).setAlive(true);
				net.getServerNodes().get(nodeid).setPingTimeMsec(pingTime);
				net.getServerNodes().get(nodeid).storeNode();
			}
			if (netnode.getType() == DarkCloudNodeType.CLIENT) {
				net.getClientNodes().get(nodeid).setAlive(true);
				net.getClientNodes().get(nodeid).setPingTimeMsec(pingTime);
				net.getClientNodes().get(nodeid).storeNode();
			}
		} catch (Exception e) {
			if (netnode.getType() == DarkCloudNodeType.SERVER) {
				net.getServerNodes().get(nodeid).setAlive(false);
			} else {
				net.getClientNodes().get(nodeid).setAlive(false);
			}
			
			net.getLogger().debug("[DarkCloud::Warning] The " + netnode.getType().toString() + " node at " +
				netnode.getName() + ":" + netnode.getPort() + " cannot be contacted: " + StackTraceUtil.getStackTrace(e));
		}
	}
}

/**
 * A client node needs to know the status of the network. Hence, this thread has the purpose to periodically check which nodes are alive on the darknet
 * @author blacklight
 */
public class NetPoll extends Thread {
	private static NetPoll poll;
	private static int msecTimeout;
	private int msecTimer;
	
	/**
	 * Default constructor
	 */
	private NetPoll() {
		msecTimer = DarkCloud.getInstance().getMsecPollTimer();
		msecTimeout = DarkCloud.getInstance().getMsecResponseTimeout();
	}
	
	/**
	 * Since we're dealing with a singleton thread object, we only create it and access to it through this static method
	 * @return The NetPoll singleton object
	 */
	public static synchronized NetPoll getPoller()
	{
		if (poll == null)
		{
			poll = new NetPoll();
		}
		
		return poll;
	}
	
	/**
	 * Code for the NetPoll thread
	 */
	@Override
	public void run()
	{
		while (true)
		{
			try {
				ArrayList<String> indexes = new ArrayList<String>(DarkCloud.getInstance().getServerNodes().keySet());
				
				for (int i=0; i < indexes.size(); i++)
				{
					new NodeChecker(DarkCloud.getInstance().getServerNodes().get(indexes.get(i))).start();
				}
				
				indexes = new ArrayList<String>(DarkCloud.getInstance().getClientNodes().keySet());
				
				for (int i=0; i < indexes.size(); i++)
				{
					new NodeChecker(DarkCloud.getInstance().getClientNodes().get(indexes.get(i))).start();
				}
				
				Thread.sleep(msecTimer);
			}
			
			catch (InterruptedException e) {}
		}
	}
	
	/**
	 * @return The time, in msec, elapsed before considering a ping request as timed out
	 */
	public static int getMsecTimeout() {
		return msecTimeout;
	}
}
