package it.unimore.weblab.darkcloud.service;

import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.configuration.ConfigurationException;

/**
 * Background thread for the Server node, which accepts incoming requests
 * @author blacklight
 */
public class ServerService extends NetService {
	/**
	 * Constructor
	 * @param net The DarkCloud object
	 * @throws ConfigurationException
	 * @throws IOException
	 */
	public ServerService(DarkCloud net) throws ConfigurationException, IOException, NoSuchAlgorithmException {
		super(net);
	}
	
	/**
	 * Execute the thread
	 */
	@Override
	public void run()
	{
		while (true)
		{
			try
			{
				Socket sock = servsock.accept();
				sock.setSoTimeout(net.getMsecResponseTimeout());
				new Server(sock).start();
			}
			
			catch (IOException e)
			{
				try {
					net.getLogger().error("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
				} catch (Exception ee)  {}
			}
		}
	}
}
