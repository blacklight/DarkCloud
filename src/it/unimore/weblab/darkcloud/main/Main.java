package it.unimore.weblab.darkcloud.main;

import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.service.NetService;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

/**
 * Main class, it just starts the service
 * @author blacklight
 */
public class Main {
	/** Arguments list passed via command line */
	public static String[] sysargs;
	
	/**
	 * Main function
	 * @param args Arguments passed to the program. NOTE: The software first checks for settings passed via command line, then in the configuration file, and at last it uses the default values, if available. Available options: <ul><li><b>-c</b> <i>&lt;configuration file&gt;</i> - Path to the configuration file (by default, the program search for ./darkcloud.conf)</li><li><b>-d</b> <i>&lt;working directory&gt;</i> - Path to the working directory - by default, the same working directory where the program was launched will be used, unless the configuration file specifies otherwise</li><li><b>-p</b> <i>&lt;listen port&gt;</i> - Listen port</li><li><b>-t</b> <i>&lt;node type&gt;</i> - Node type. It can be "client" or "server"
	 */
	public static void main(String[] args)
	{
		DarkCloud net = null;
		sysargs = args.clone();
		
		try
		{
			net = DarkCloud.getInstance();
			net.service = NetService.createService(net);
			net.service.start();
			net.service.join();
		}
		
		catch (Exception e)
		{
			try
			{
				boolean canLog = false;
				
				if (net != null)
				{
					if (net.getLogger() != null) {
						canLog = true;
					}
				}
				
				if (!canLog) {
					e.printStackTrace();
				} else {
					net.getLogger().fatal("[DarkCloud::FatalError] {" + StackTraceUtil.getStackTrace(e) + "}");
				}
			}
			
			catch (Exception ioe)  { e.printStackTrace(); }
		}
		
		finally
		{
			try
			{
				if (net != null) {
					net.getLogger().info("[DarkCloud::Message] {Application stop}");
				}
			}
			
			catch (Exception ioe)  {}
		}
	}
}
