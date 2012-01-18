package it.unimore.weblab.darkcloud.net;

import it.unimore.weblab.darkcloud.net.DarkCloud.DarkCloudNodeType;

/**
 * Class which models a server node on the network
 * @author blacklight
 */
public class ServerNode extends NetNode {
	public ServerNode(String hostname, int listenPort, DarkCloudNodeType type)
	{
		super(hostname, listenPort, type);
	}
}
