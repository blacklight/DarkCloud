package it.unimore.weblab.darkcloud.protocol;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.crypto.SecretKey;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import it.unimore.weblab.darkcloud.db.Tuple;
import it.unimore.weblab.darkcloud.db.Db.Table;
import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.net.NetNode;
import it.unimore.weblab.darkcloud.protocol.Request.RequestType;
import it.unimore.weblab.darkcloud.protocol.Response.ResponseType;
import it.unimore.weblab.darkcloud.util.CryptUtil;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

public abstract class ClientResponseMethods extends ResponseMethods {
	
	/////////////////////////////////////////////////////////////////////////////PUT///////////////////////////////////////////////////////////////////////////////////////
	
	public static Response put(Request req)
	{
		/// REQUEST CHECK START
		//recupera il campo che indica il file
		Field file = req.getField("file");
		
		//controlla che non sia null
		if (file == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Invalid put request: No file property found");
			return (Response) new Response(ResponseType.ERROR).setContent("Invalid put request: No file property found");
		}
		
		//legge il nome del file
		String name = file.getAttribute("name");
		boolean hasName = false;
		
		if (name != null) {
			//toglie gli spazi bianchi prima e dopo il nome
			name = name.trim();
			
			if (!name.isEmpty()) {
				hasName = true;
			}
		}
		
		//errore restituito se non c'� un nome
		if (!hasName)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Invalid put request: The file property has no name field");
			return (Response) new Response(ResponseType.ERROR).setContent("The file property has no name field");
		}
		
		//prende l'attributo del file "encoding" ??? si vede che fabio vuole specificare nel caricamento del file o cmq nell'astrazzione superiore
		//la codifica da usare sul file
		String encoding = file.getAttribute("encoding");
		boolean hasEncoding = false;
		
		if (encoding != null) {
			encoding = encoding.trim();
			
			if (!encoding.isEmpty()) {
				hasEncoding = true;
			}
		}
		
		if (!hasEncoding) {
			encoding = "plain";
		} else if (!encoding.equalsIgnoreCase("base64")) {
			encoding = "plain";
		}
		
		if (file.getContent() == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Invalid put request: No file content was specified");
			return (Response) new Response(ResponseType.ERROR).setContent("No file content was specified");
		}
		
		//recupera il contenuto del file come una stringa 
		String fileContent = file.getContent();
		//crea l'array per caricare i byte del dato
        byte[] contentBytes = null;
		
		if (encoding.equalsIgnoreCase("base64")) {
			//se il contenuto del file � criptato decodifica la stringa e lo salva nell'array di byte
			contentBytes = Base64.decodeBase64(fileContent);
		} else {
			//se il file non � criptato copia direttamente il contenuto nell'array ! 
			contentBytes = file.getContent().getBytes();
		}
        
		//legge il checksum
		String your_checksum = file.getAttribute("checksum");
		boolean hasChecksum = false;
		
		if (your_checksum != null) {
			your_checksum = your_checksum.trim();
			
			if (!your_checksum.isEmpty()) {
				hasChecksum = true;
			}
		}
		
		if (!hasChecksum)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] No checksum was provided for this file");
			return (Response) new Response(ResponseType.ERROR).setContent("No checksum was provided for this file");
		}
		
		//calcolo il mio checksum 
		String my_checksum = DigestUtils.md5Hex(contentBytes);
		
		//lo confronto con quello del file
		if (!my_checksum.equalsIgnoreCase(your_checksum))
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Wrong checksum - The file you attempted to sent was corrupted, try again");
			return (Response) new Response(ResponseType.ERROR).setContent("Wrong checksum - The file you attempted to sent was corrupted, try again");
		}
		
		/// REQUEST CHECK END
		
		//metto in una struttura i server attivi con il loro riferimento
		HashMap<String, NetNode> aliveServerNodes = DarkCloud.getInstance().getAliveServerNodes();
		
		//se la lista non � vuota
		if (aliveServerNodes.isEmpty())
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] No server nodes available at the moment, try again later");
			return (Response) new Response(ResponseType.ERROR).setContent("No server nodes available at the moment, try again later");
		}		
		        
        /// SERVER PUT REQUEST START
        
		SecretKey key = null;
        String encryptedContent = null;
        int fileDimension = 0;
        
        // Generate a symmetric encryption key for the file
        
        try {
        	//genero una chiave
            key = CryptUtil.generateSymmetricKey();
            //cripto tutti i byte del file ora il mio contenuto informativo � in encryptedContent 
            encryptedContent = Base64.encodeBase64String(CryptUtil.encrypt(contentBytes, key, "AES"));
    		fileDimension = encryptedContent.length();
		} catch (Exception e1) {
			DarkCloud.getInstance().getLogger().error("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e1));
			return (Response) new Response(ResponseType.ERROR).setContent("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e1));
		}
        
		//conto quanti server attivi ci sono
		int nServer = aliveServerNodes.size();
		int filefragmentSize=fileDimension/nServer;
        
        String[] fragment = new String[nServer];
        for(int i=0;i<nServer;i++){
        	fragment[i]=encryptedContent.substring(i*filefragmentSize, filefragmentSize);
        }
        
		// TODO Implement a more smart algorithm for fetching nodes
        Response resp = null;
        
        for(int i=0; i < nServer; i++){
        	//prende il primo server attivo , invece io devo metterci tutti i server a cui mandare un frammento !!! ^_^
        	NetNode server = aliveServerNodes.get(aliveServerNodes.keySet().toArray()[i]);

        	try {
        		//io client modifico la "richiesta" ricevuta dallo script inserendogli il contenuto effettivo del file criptato 
        		req.getField("file").setContent(fragment[i]);
                
        		//sostituisco nella "richiesta" anche il checksum
        		req.getField("file").setAttribute("checksum", DigestUtils.md5Hex(Base64.decodeBase64(fragment[i])));
                
        		//invio un tipo "risposta" a server (che � il primo della lista) dando come oggetto la "richiesta" dello script modificata !!! ^_^
        		resp = server.send(req);

        		//il server risp che va tutto bene
        		if (resp.getType() == ResponseType.ACK)
        		{
        			//inserisce nel db locale la voce del frammento
        			DarkCloud.getInstance().getDb().insert(Table.FILEFRAGMENT, new Tuple().
        					setField("name", name).
        					setField("fragmentid", new Integer(i)).
        					setField("checksum", my_checksum).
        					setField("nodeid", DarkCloud.getNodeKey(server.getName(), server.getPort())));
        		}
        	} catch (Exception e) {
        		DarkCloud.getInstance().getLogger().error("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
        		return (Response) new Response(ResponseType.ERROR).setContent("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
        	}

        }
        
        try{
        	//genero la chiave locale e la salvo nel db
        	String keystring = Base64.encodeBase64String(
                	CryptUtil.encrypt(key.getEncoded(), DarkCloud.getInstance().getPublicKey(), "RSA/ECB/PKCS1Padding"));
        	 //inserisce nel db locale la voce del file
            	DarkCloud.getInstance().getDb().insert(Table.FILE, new Tuple().
            		setField("name", name).
            		setField("key", keystring).
            		setField("checksum", my_checksum).
            		setField("creationtime", new Date().getTime()).
            		setField("modifytime", new Date().getTime()));
            	
        }catch (Exception e) {
			DarkCloud.getInstance().getLogger().error("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
			return (Response) new Response(ResponseType.ERROR).setContent("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
		}
        
        /// SERVER PUT REQUEST END
		
		DarkCloud.getInstance().getLogger().info("[DarkCloud::Request] {SequenceNum " + req.getSequence() +
			"} {Type PUT} {Filename " + name +
			"} {Checksum " + my_checksum + "}");
        
		return resp;
	}
    
	///////////////////////////////////////////////////////////////////////////////GET/////////////////////////////////////////////////////////////////////////////////
	
	public static Response get(Request req)
	{
		/// REQUEST CHECK START
		Field file = req.getField("file");
		
		if (file == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Invalid put request: No file property found");
			return (Response) new Response(ResponseType.ERROR).setContent("Invalid put request: No file property found");
		}
		
		String name = file.getAttribute("name");
		boolean hasName = false;
		
		if (name != null) {
			name = name.trim();
			
			if (!name.isEmpty()) {
				hasName = true;
			}
		}
		
		if (!hasName) {
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Invalid put request: The file property has no name field");
			return (Response) new Response(ResponseType.ERROR).setContent("The file property has no name field");
		}
		/// REQUEST CHECK END
        
        ArrayList<ArrayList<String>> result = null;
        
        try {
            // TODO In a fragment replication scenario, count the number of fragments for each file
        	// through a GROUP BY query, then check for each fragment how many nodes have that fragment
			result = DarkCloud.getInstance().getDb().select(
				"SELECT key, fragmentid, frag.checksum, nodeid " +
				"FROM " + Table.FILE + " f JOIN " + Table.FILEFRAGMENT + " frag " +
				"ON f.name=frag.name " +
				"WHERE f.name='" + name + "' " +
				"ORDER BY fragmentid");
            
			if (result.isEmpty()) {
    			return (Response) new Response(ResponseType.ERROR).setContent("No such file or directory");
			}
            
            Response resp = null;
            
			for (ArrayList<String> row : result)
			{
                String nodeid = row.get(3);
                NetNode node = DarkCloud.getInstance().getAliveServerNodes().get(nodeid);
                
                if (node == null) {
                    DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Impossible to fetch the specified file: one or more fragments cannot be reconstructed since some server nodes are not available");
                	return (Response) new Response(ResponseType.ERROR).setContent("Impossible to fetch the specified file: one or more fragments cannot be reconstructed since some server nodes are not available");
                }
                
                //int fragmentid = Integer.parseInt(row.get(1));
                //String checksum = row.get(2);
                SecretKey filekey = (SecretKey) CryptUtil.secretKeyFromString(
                    Base64.encodeBase64String(
                	    CryptUtil.decrypt(Base64.decodeBase64(row.get(0)),
                    	    DarkCloud.getInstance().getPrivateKey(), "RSA/ECB/PKCS1Padding")));
                
                Request getreq = (Request) new Request(RequestType.GET).
                	appendField(new Field("file").
                		appendAttribute("name", name));
                
    			Response servresp = node.send(getreq);
                
    			if (servresp.getType() == ResponseType.ACK)
    			{
                    file = servresp.getField("file");
                    
                    if (file == null) {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] The server returned an invalid response");
                        return (Response) new Response(ResponseType.ERROR).setContent("The server returned an invalid response");
                    }
                    
                    boolean hasChecksum = false;
                    String serv_checksum = file.getAttribute("checksum");
                    
                    if (serv_checksum != null) {
                    	serv_checksum = serv_checksum.trim();
                    	
                    	if (!serv_checksum.isEmpty()) {
                    		hasChecksum = true;
                    	}
                    }
                    
                    if (!hasChecksum) {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] The server returned an invalid response");
                        return (Response) new Response(ResponseType.ERROR).setContent("The server returned an invalid response");
                    }
                    
                    String fileContent = file.getContent();
                    boolean hasContent = false;
                    
                    if (fileContent != null) {
                    	if (!fileContent.isEmpty()) {
                    		hasContent = true;
                    	}
                    }
                    
                    if (!hasContent) {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] The server returned an invalid response");
                        return (Response) new Response(ResponseType.ERROR).setContent("The server returned an invalid response");
                    }
                    
                    fileContent = Base64.encodeBase64String(
                    	CryptUtil.decrypt(
                    		Base64.decodeBase64(fileContent), filekey, "AES"));
                    
                    servresp.getField("file").setContent(fileContent);
    			}
                
    			resp = servresp;
			}
            
    		DarkCloud.getInstance().getLogger().info("[DarkCloud::Request] {SequenceNum " + req.getSequence() +
    			"} {Type GET} {Filename " + name + "}");
            
			return resp;
		} catch (Exception e) {
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::ERROR] " + StackTraceUtil.getStackTrace(e));
			return (Response) new Response(ResponseType.ERROR).setContent(StackTraceUtil.getStackTrace(e));
		}
	}
}
