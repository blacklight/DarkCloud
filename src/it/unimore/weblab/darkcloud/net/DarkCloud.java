package it.unimore.weblab.darkcloud.net;

import gnu.getopt.Getopt;
import it.unimore.weblab.darkcloud.db.Db;
import it.unimore.weblab.darkcloud.main.Main;
import it.unimore.weblab.darkcloud.service.NetService;
import it.unimore.weblab.darkcloud.util.CryptUtil;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

import java.io.File;
import java.io.IOException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.PropertyConfigurator;

/**
 * Main class for the darknet implementation
 * @author blacklight
 */
public class DarkCloud {
	/**
	 * Allowed types for a node. It can be a client or a server node
	 * @author blacklight
	 */
	public enum DarkCloudNodeType
	{
		SERVER,
		CLIENT
	};
	
	private static DarkCloud _net;
	private HashMap<String, NetNode> clientNodes = new HashMap<String, NetNode>();
	private HashMap<String, NetNode> serverNodes = new HashMap<String, NetNode>();
	private DarkCloudNodeType nodeType;
	private int listenPort;
	private Key privateKey;
	private Key publicKey;
	private File confFile;
	private File workDir;
	private File keystoreFile;
	private File dbFile;
	private String keystorePwd;
	private Db db;
	private Logger logger = null;
	private boolean hasWorkdirOpt = false;
	private boolean hasTypeOpt = false;
	private int msecPollTime = 10000;
	private int msecResponseTimeout = 10000;
	public NetService service;
	
	/**
	 * Parse the software configuration
	 * @throws ConfigurationException
	 * @throws IOException
	 * @throws SQLException 
	 * @throws ClassNotFoundException 
	 */
	@SuppressWarnings("deprecation")
	private synchronized void parseConf() throws ConfigurationException, IOException, ClassNotFoundException, SQLException
	{
		Configuration conf = new PropertiesConfiguration(confFile);
		
		if (!hasWorkdirOpt)
		{
			String dir = conf.getString("darkcloud.workdir", "");
		
			if (!dir.isEmpty())
			{
				workDir = new File(dir);
			}
		}
		
		PropertyConfigurator.configure(workDir + "/log4j.properties");
		FileAppender fa = new FileAppender(new PatternLayout("%d{HH:mm:ss,SSS} [%-5p] %m%n"), workDir + "/darkcloud.log", true);
		fa.setThreshold(Priority.INFO);
		logger = Logger.getLogger(Main.class);
		logger.addAppender(fa);
		
		logger.info("[DarkCloud::Message] {Application start}");
		logger.info("[DarkCloud::Config] {ConfFile " + confFile.getAbsolutePath() + "}");
		logger.info("[DarkCloud::Config] {WorkDir " + workDir.getAbsolutePath() + "}");
		
		String confKeystoreFile = conf.getString("darkcloud.keystorefile", "");
		
		if (confKeystoreFile.isEmpty())
		{
			boolean keystoreFound = false;
			String dir = ".";
		
			for (int i=0; i < 7 && !keystoreFound; i++)
			{
				keystoreFile = new File(dir + "/darkcloud.keystore");
					
				if (keystoreFile.exists() && keystoreFile.isFile())
				{
					keystoreFound = true;
				} else {
					dir = "../" + dir;
				}
			}
			
			if (!keystoreFound)
			{
				throw new IOException("Could not find the keystore file darkcloud.keystore - Consider generating a keystore " +
					"by using the generate_keys.sh script provided in the distribution directory");
			}
		} else {
			keystoreFile = new File(confKeystoreFile);
			
			if (!(keystoreFile.exists() && keystoreFile.isFile()))
			{
				throw new IOException("The keystore file specified in your configuration does not exist or is not valid");
			}
		}
		
		System.setProperty("javax.net.ssl.keyStore", keystoreFile.getAbsolutePath());
		System.setProperty("javax.net.ssl.trustStore", keystoreFile.getAbsolutePath());
		logger.info("[DarkCloud::Config] {KeystoreFile " + keystoreFile.getAbsolutePath() + "}]");
		
		keystorePwd = conf.getString("darkcloud.keystorepwd", "");
		
		if (keystorePwd.isEmpty())
		{
			throw new ConfigurationException("No darkcloud.keystorepwd option was specified as password for your SSL keystore");
		}
		
		System.setProperty("javax.net.ssl.keyStorePassword", keystorePwd);
		logger.info("[DarkCloud::Config] {KeystorePwd *****}]");
		
		String pollTime = conf.getString("darkcloud.msecpolltime", "");
		
		if (!pollTime.isEmpty())
		{
			msecPollTime = Integer.parseInt(pollTime);
		}
		
		logger.info("[DarkCloud::Config] {MsecPollTime " + msecPollTime + "}");
		
		String responseTimeout = conf.getString("darkcloud.msecresponsetimeout", "");
		
		if (!pollTime.isEmpty())
		{
			msecResponseTimeout = Integer.parseInt(responseTimeout);
		}
		
		logger.info("[DarkCloud::Config] {MsecResponseTimeout " + msecResponseTimeout + "}");
		
		String confNodeType = conf.getString("darkcloud.nodetype", "");
		
		if (confNodeType.isEmpty())
		{
			throw new ConfigurationException("No value specified for the required setting \"darkcloud.nodetype\" in " +
				confFile.getAbsolutePath());
		}
		
		if (!hasTypeOpt)
		{
			if (confNodeType.trim().equalsIgnoreCase("client"))
				nodeType = DarkCloudNodeType.CLIENT;
			else if (confNodeType.trim().equalsIgnoreCase("server"))
				nodeType = DarkCloudNodeType.SERVER;
			else
				throw new ConfigurationException("Invalid value for the configuration setting \"darkcloud.nodetype\" " +
					"in file " + confFile.getAbsolutePath() + " - valid values are \"client\" and \"server\"");
		}
		
		logger.info("[DarkCloud::Config] {NodeType " + nodeType.toString() + "}");
		
		if (listenPort == 0)
		{
			listenPort = conf.getInt("darkcloud.listenport", -1);
			
			if (listenPort < 1 || listenPort > 65535)
			{
				throw new ConfigurationException("An invalid \"darkcloud.listenport\" parameter was specified in " +
					"file " + confFile.getAbsolutePath());
			}
		}
		
		if (listenPort < 1 || listenPort > 65535)
		{
			throw new ConfigurationException("Component configured with an invalid listen port");
		}
		
		logger.info("[DarkCloud::Config] {ListenPort " + listenPort + "}");
		dbFile = new File("./darkcloud.db");
		db = new Db(dbFile);
		
		String[] hostList = conf.getStringArray("darkcloud.nodes.host");
		String[] typeList = conf.getStringArray("darkcloud.nodes.type");
		String[] portList = conf.getStringArray("darkcloud.nodes.listenport");
		
		if (hostList.length != typeList.length || hostList.length != portList.length || portList.length != typeList.length)
		{
			throw new ConfigurationException("Invalid values have been specified for the settings \"darkcloud.nodes.host\", " +
					"\"darkcloud.nodes.type\" and \"darkcloud.nodes.listenport\" - If specified, these settings should all " +
					"contain the same number of elements");
		}
		
		if (hostList.length > 0)
		{
			for (int i=0; i < hostList.length; i++)
			{
				DarkCloudNodeType nodeType = DarkCloudNodeType.CLIENT;
				
				if (typeList[i].trim().equalsIgnoreCase("client"))
					nodeType = DarkCloudNodeType.CLIENT;
				else if (typeList[i].trim().equalsIgnoreCase("server"))
					nodeType = DarkCloudNodeType.SERVER;
				else
					throw new ConfigurationException("An invalid value was specified in configuration file \"" +
						confFile.getAbsolutePath() + "\" for the setting \"darkcloud.nodes.type\" - Valid values " +
						"are \"client\" and \"server\"");
				
				int nodePort = -1;
				
				try
				{
					nodePort = Integer.parseInt(portList[i]);
				}
				
				catch (NumberFormatException e)
				{
					throw new ConfigurationException("An invalid value was specified in configuration file \"" +
						confFile.getAbsolutePath() + "\" for the setting \"darkcloud.nodes.listenport\" - Valid values " +
						"are integers in range 1-65535");
				}
				
				logger.info("[DarkCloud::Config] {NodeHost[" + i + "] " + hostList[i] + "}");
				logger.info("[DarkCloud::Config] {NodeType[" + i + "] " + nodeType.toString() + "}");
				logger.info("[DarkCloud::Config] {NodePort[" + i + "] " + nodePort + "}");
				
				if (nodeType == DarkCloudNodeType.SERVER)
				{
					NetNode node = new ServerNode(hostList[i], nodePort, nodeType);
					serverNodes.put(getNodeKey(hostList[i], nodePort), node);
				} else {
					NetNode node = new ClientNode(hostList[i], nodePort, nodeType);
					clientNodes.put(getNodeKey(hostList[i], nodePort), node);
				}
			}
		}	
		
		boolean privateKeyFileFound = false;
		File privKeyFile = null;
		String dir = ".";
		
		for (int i=0; i < 7 && !privateKeyFileFound; i++)
		{
			privKeyFile = new File(dir + "/private.key");
				
			if (privKeyFile.exists() && privKeyFile.isFile())
			{
				privateKeyFileFound = true;
			} else {
				dir = "../" + dir;
			}
		}
		
		boolean publicKeyFileFound = false;
		File pubKeyFile = null;
		dir = ".";
		
		for (int i=0; i < 7 && !publicKeyFileFound; i++)
		{
			pubKeyFile = new File(dir + "/public.key");
				
			if (pubKeyFile.exists() && pubKeyFile.isFile())
			{
				publicKeyFileFound = true;
			} else {
				dir = "../" + dir;
			}
		}
		
		if (!(privateKeyFileFound && publicKeyFileFound))
		{
			dir = ".";
			logger.info("No RSA keypair found, generating a new keypair");
            KeyPair kp = null;
            
			try {
				kp = CryptUtil.generateKeyPair();
			} catch (NoSuchAlgorithmException e) {
				logger.fatal("[DarkCloud::Fatal] " + StackTraceUtil.getStackTrace(e));
				System.exit(1);
			}
            
			privateKey = kp.getPrivate();
			publicKey = kp.getPublic();
			
			pubKeyFile = new File(dir + "/public.key");
			privKeyFile = new File(dir + "/private.key");
			CryptUtil.storePublicKey(pubKeyFile, publicKey);
			CryptUtil.storePrivateKey(privKeyFile, privateKey);
			
			logger.info("[DarkCloud::Info] A pair has just been generated to " +
				pubKeyFile.getAbsolutePath() + " and " + privKeyFile.getAbsolutePath());
		} else {
			privateKey = CryptUtil.getPrivateKey(privKeyFile);
			publicKey = CryptUtil.getPublicKey(pubKeyFile);
		}
		
		if (privateKey == null || publicKey == null || !pubKeyFile.isFile() || !privKeyFile.isFile()) {
			logger.fatal("[DarkCloud::Fatal] Unable to read the RSA keypair - Aborting the application");
			System.exit(1);
		}
	}
	
	/**
	 * Constructor for the darknet object
	 * @throws ConfigurationException
	 * @throws IOException
	 * @throws SQLException 
	 * @throws ClassNotFoundException 
	 */
	private DarkCloud() throws ConfigurationException, IOException, ClassNotFoundException, SQLException
	{
		String dir = ".";
		boolean confFound = false;
		listenPort = 0;
			
		if (Main.sysargs.length > 0)
		{
			Getopt opts = new Getopt("darkcloud", Main.sysargs, "c:d:p:t:");
			boolean hasConffileOpt = false;
			int c;
			
			while ((c = opts.getopt()) > 0)
			{
				if (c == 'c')
				{
					hasConffileOpt = true;
					confFile = new File(opts.getOptarg());
					
					if (!confFile.exists() || !confFile.isFile())
					{
						throw new IOException(confFile.getAbsolutePath() + " is not a valid configuration file");
					}
					
					confFound = true;
				} else if (c == 'd') {
					hasWorkdirOpt = true;
					workDir = new File(opts.getOptarg());
					
					if (!workDir.exists() || !workDir.isDirectory())
					{
						throw new IOException(workDir.getAbsolutePath() + " is not a valid directory");
					}
					
					if (!hasConffileOpt)
					{
						confFile = new File(workDir.getAbsolutePath() + "/darkcloud.conf");
						
						if (!confFile.exists() || !confFile.isFile())
						{
							throw new IOException(confFile.getAbsolutePath() + " is not a valid configuration file");
						}
						
						confFound = true;
					}
				} else if (c == 'p') {
					try
					{
						listenPort = Integer.parseInt(opts.getOptarg());
						
						if (listenPort < 1 || listenPort > 65535)
						{
							throw new NumberFormatException();
						}
					}
					
					catch (NumberFormatException e)
					{
						throw new ConfigurationException("Invalid value for the listen port: " + listenPort);
					}
				} else if (c == 't') {
					hasTypeOpt = true;
					
					if (opts.getOptarg().trim().equalsIgnoreCase("client"))
						nodeType = DarkCloudNodeType.CLIENT;
					else if (opts.getOptarg().trim().equalsIgnoreCase("server"))
						nodeType = DarkCloudNodeType.SERVER;
					else
						throw new ConfigurationException("Invalid type \"" + opts.getOptarg() +
							"\" - allowed types are \"client\" and \"server\"");
				}
			}
		}
		
		if (!confFound)
		{
			for (int i=0; i < 7 && !confFound; i++)
			{
				confFile = new File(dir + "/darkcloud.conf");
					
				if (confFile.exists() && confFile.isFile())
				{
					confFound = true;
					workDir = new File(dir);
				} else {
					dir = "../" + dir;
				}
			}
			
			if (!confFound)
			{
				throw new IOException("Could not find the configuration file darkcloud.conf");
			}
		}
		
		parseConf();
	}
	
	/**
	 * The darknet object is a singleton, thus we can only create it in a static way
	 * @return The darknet singleton object
	 * @throws ConfigurationException
	 * @throws IOException
	 */
	public static synchronized DarkCloud getInstance()
	{
		if (_net == null)
		{
			try {
				_net = new DarkCloud();
			} catch (Exception e) {
				if (_net != null)
				{
					if (_net.logger != null) {
						_net.logger.fatal("[DarkCloud::Fatal] " + StackTraceUtil.getStackTrace(e));
					} else {
						System.err.println(StackTraceUtil.getStackTrace(e));
					}
					
					return null;
				} else {
					e.printStackTrace();
					return null;
				}
			}
		}
		
		return _net;
	}
	
	/**
	 * @return The server nodes of the network configured for this node
	 */
	public HashMap<String, NetNode> getServerNodes() {
		return serverNodes;
	}
	
	/**
	 * @return The client nodes of the network configured for this node
	 */
	public HashMap<String, NetNode> getClientNodes() {
		return clientNodes;
	}
	
	/**
	 * @return The alive server nodes
	 */
	public synchronized HashMap<String, NetNode> getAliveServerNodes() {
		HashMap<String, NetNode> nodes = new HashMap<String, NetNode>();
		ArrayList<String> keys = new ArrayList<String>(serverNodes.keySet());
		
		for (String key: keys)
		{
			if (serverNodes.get(key).isAlive()) {
				nodes.put(key, serverNodes.get(key));
			}
		}
		
		return nodes;
	}
	
	/**
	 * @return The alive client nodes
	 */
	public synchronized HashMap<String, NetNode> getAliveClientNodes() {
		HashMap<String, NetNode> nodes = new HashMap<String, NetNode>();
		ArrayList<String> keys = new ArrayList<String>(clientNodes.keySet());
		
		for (String key: keys)
		{
			if (clientNodes.get(key).isAlive()) {
				nodes.put(key, clientNodes.get(key));
			}
		}
		
		return nodes;
	}
	
	/**
	 * @return The type of this node. It could be CLIENT or SERVER
	 */
	public DarkCloudNodeType getNodeType() {
		return nodeType;
	}
	
	/**
	 * @return The log4j logger object for the class
	 */
	public Logger getLogger() {
		return logger;
	}
	
	/**
	 * @return The location of the keystore file
	 */
	public String getKeystoreFile()
	{
		return keystoreFile.getAbsolutePath();
	}
	
	/**
	 * @return The keystore password
	 */
	public String getKeystorePwd()
	{
		return keystorePwd;
	}
	
	/**
	 * @return The directory used as working directory by the application
	 */
	public File getWorkDir() {
		return workDir;
	}
	
	/**
	 * @return The port this node will be listening on
	 */
	public int getListenPort() {
		return listenPort;
	}
	
	/**
	 * @param host Name of the node for which we want to get the key
	 * @param port Port of the node for which we want to get the key
	 * @return The list of server and client nodes of the network is identified by a unique string, generated starting from the name node and its listen port. This function returns this key
	 */
	public static String getNodeKey(String host, int port) {
		return DigestUtils.md5Hex(DigestUtils.sha512Hex(host + ":" + port));
	}
	
	/**
	 * @return The time, in milliseconds, between two execution of the poll algorithm for checking alive nodes on the network (default: 10000)
	 */
	public int getMsecPollTimer() {
		return msecPollTime;
	}
	
	/**
	 * @return The time, in milliseconds, elapsed before considering a response as timed out (default: 10000)
	 */
	public int getMsecResponseTimeout() {
		return msecResponseTimeout;
	}
	
	/**
	 * @return Node's RSA public key
	 */
	public Key getPublicKey() {
		return publicKey;
	}
	
	/**
	 * @return Node's RSA private key
	 */
	public Key getPrivateKey() {
		return privateKey;
	}
	
	/**
	 * @return The reference to the database file
	 */
	public File getDbFile() {
		return dbFile;
	}
	
	/**
	 * @return The local database object
	 */
	public Db getDb() {
		return db;
	}
}
