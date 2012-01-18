package it.unimore.weblab.darkcloud.service;

import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.net.DarkCloud.DarkCloudNodeType;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLServerSocketFactory;

import org.apache.commons.configuration.ConfigurationException;

/**
 * Class for a generic network service. It models both client and server services on the network
 * @author blacklight
 */
public abstract class NetService extends Thread {
	protected ServerSocket servsock;
	protected DarkCloud net;
	
	/**
	 * Constructor, only seen by its sub-classes
	 * @param net DarkCloud object
	 * @throws IOException
	 * @throws ConfigurationException
	 */
	protected NetService(DarkCloud net) throws IOException, ConfigurationException, NoSuchAlgorithmException
	{
		this.net = net;
		int listenPort = net.getListenPort();
		servsock = SSLServerSocketFactory.getDefault().createServerSocket(listenPort);
	}
	
	/**
	 * Static builder which wraps the constructor, starting the appropriate service (client or server) according to DarkCloud configuration
	 * @param net DarkCloud object
	 * @return The created NetService object
	 */
	public static NetService createService(DarkCloud net) throws ConfigurationException, IOException, NoSuchAlgorithmException
	{
		NetService serv = null;
		
		if (net.getNodeType() == DarkCloudNodeType.CLIENT) {
			serv = new ClientService(net);
		} else {
			serv = new ServerService(net);
		}
		
		return serv;
	}
}
