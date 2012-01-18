package it.unimore.weblab.darkcloud.net;

import it.unimore.weblab.darkcloud.net.DarkCloud.DarkCloudNodeType;

/**
 * Class which models a client node in the network
 * @author blacklight
 */
public class ClientNode extends NetNode {
	public ClientNode(String hostname, int listenPort, DarkCloudNodeType type)
	{
		super(hostname, listenPort, type);
	}
}
