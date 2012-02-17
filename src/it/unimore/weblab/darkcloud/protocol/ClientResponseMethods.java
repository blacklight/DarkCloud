package it.unimore.weblab.darkcloud.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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
        public static Response put(Request req)
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
                
                if (!hasName)
                {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Invalid put request: The file property has no name field");
                        return (Response) new Response(ResponseType.ERROR).setContent("The file property has no name field");
                }
                
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
                
                String fileContent = file.getContent();
        byte[] contentBytes = null;
                
                if (encoding.equalsIgnoreCase("base64")) {
                        contentBytes = Base64.decodeBase64(fileContent);
                } else {
                        contentBytes = file.getContent().getBytes();
                }
        
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
                String my_checksum = DigestUtils.md5Hex(contentBytes);
                
                if (!my_checksum.equalsIgnoreCase(your_checksum))
                {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Wrong checksum - The file you attempted to sent was corrupted, try again");
                        return (Response) new Response(ResponseType.ERROR).setContent("Wrong checksum - The file you attempted to sent was corrupted, try again");
                }
                
                
                /// REQUEST CHECK END
                
                HashMap<String, NetNode> aliveServerNodes = DarkCloud.getInstance().getAliveServerNodes();
                HashMap<String, NetNode> aliveServerNodes2 = new HashMap<String, NetNode>();
                if (aliveServerNodes.isEmpty())
                {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] No server nodes available at the moment, try again later");
                        return (Response) new Response(ResponseType.ERROR).setContent("No server nodes available at the moment, try again later");
                }
                //Calculate how many alive server we have
                int nServer = aliveServerNodes.size();
                //Function for shuffling the order of server to increase security 
                List<String> keys = new ArrayList<String>(aliveServerNodes.keySet());
                Collections.shuffle(keys);
                for (String o : keys) {
                	aliveServerNodes2.put(o,aliveServerNodes.get(o));
                }

        /// SERVER PUT REQUEST START
        
                SecretKey key = null;
        String encryptedContent = null;
        
        // Generate a symmetric encryption key for the file
        try {
            key = CryptUtil.generateSymmetricKey();
            encryptedContent = Base64.encodeBase64String(CryptUtil.encrypt(contentBytes, key, "AES"));
            //After had encrypted the file content , we calculate how big are our fragment 
            
                } catch (Exception e1) {
                        DarkCloud.getInstance().getLogger().error("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e1));
                        return (Response) new Response(ResponseType.ERROR).setContent("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e1));
                }
         int fileDimension = encryptedContent.length();
         int fileFragmentSize=fileDimension/nServer;
         String[] fragment = new String[nServer];
         for(int i=0;i<nServer;i++){
                        fragment[i]=encryptedContent.substring(i*fileFragmentSize, fileFragmentSize*(i+1));
                }   
        Response resp = null;    
        for(int i=0;i<nServer;i++){
                // TODO Implement a more smart algorithm for fetching nodes
                NetNode server = aliveServerNodes2.get(aliveServerNodes2.keySet().toArray()[i]);
                String fragment_checksum = DigestUtils.md5Hex(Base64.decodeBase64(fragment[i]));
                try {
            req.getField("file").setContent(fragment[i]);
            //  CryptUtil.decrypt(Base64.decodeBase64(encryptedContent), key, "AES")));
            req.getField("file").setAttribute("checksum", fragment_checksum);
            resp = server.send(req);
            
            if (resp.getType() == ResponseType.ACK)
            {
                DarkCloud.getInstance().getDb().insert(Table.FILEFRAGMENT, new Tuple().
                        setField("name", name).
                        setField("fragmentid", new Integer(i)).
                        setField("checksum", fragment_checksum).
                        setField("nodeid", DarkCloud.getNodeKey(server.getName(), server.getPort())));
            }
                } catch (Exception e) {
                        DarkCloud.getInstance().getLogger().error("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
                        return (Response) new Response(ResponseType.ERROR).setContent("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
                }
        }        
        try {
        String keystring = Base64.encodeBase64String(
                CryptUtil.encrypt(key.getEncoded(), DarkCloud.getInstance().getPublicKey(), "RSA/ECB/PKCS1Padding"));
        
        DarkCloud.getInstance().getDb().insert(Table.FILE, new Tuple().
                setField("name", name).
                setField("key", keystring).
                setField("checksum", my_checksum).
                setField("creationtime", new Date().getTime()).
                setField("modifytime", new Date().getTime()));
        } catch (Exception e) {
            DarkCloud.getInstance().getLogger().error("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
            return (Response) new Response(ResponseType.ERROR).setContent("[DarkCloud::Error] " + StackTraceUtil.getStackTrace(e));
        }
        /// SERVER PUT REQUEST END
                
                DarkCloud.getInstance().getLogger().info("[DarkCloud::Request] {SequenceNum " + req.getSequence() +
                        "} {Type PUT} {Filename " + name +
                        "} {Checksum " + my_checksum + "}");
                return resp;
        }

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
            Response servresp=null;
            String fileContent="";
            SecretKey filekey = (SecretKey) CryptUtil.secretKeyFromString(
                    Base64.encodeBase64String(
                            CryptUtil.decrypt(Base64.decodeBase64(result.get(0).get(0)),
                            DarkCloud.getInstance().getPrivateKey(), "RSA/ECB/PKCS1Padding")));
            
            
                        for (ArrayList<String> row : result)
                        {
                        	
                String nodeid = row.get(3);
                NetNode node = DarkCloud.getInstance().getAliveServerNodes().get(nodeid);
                
                if (node == null) {
                    DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Impossible to fetch the specified file: one or more fragments cannot be reconstructed since some server nodes are not available");
                        return (Response) new Response(ResponseType.ERROR).setContent("Impossible to fetch the specified file: one or more fragments cannot be reconstructed since some server nodes are not available");
                }  
                
                Request getreq = (Request) new Request(RequestType.GET).
                        appendField(new Field("file").
                                appendAttribute("name", name));
                
                        servresp = node.send(getreq);
                
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
                    
                   String fileFragment = file.getContent();
                    boolean hasContent = false;
                    
                    if (fileFragment != null) {
                        if (!fileFragment.isEmpty()) {
                                hasContent = true;
                        }
                    }
                    
                    if (!hasContent) {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] The server returned an invalid response");
                        return (Response) new Response(ResponseType.ERROR).setContent("The server returned an invalid response");
                    }
                    String fragment_checksum = DigestUtils.md5Hex(Base64.decodeBase64(fileFragment));
                    boolean hasChecksum1 = false;
                    String dbFragmentChecksum =row.get(2);
                    if (dbFragmentChecksum != null) {
                    	dbFragmentChecksum = dbFragmentChecksum.trim();
                        
                        if (!dbFragmentChecksum.isEmpty()) {
                                hasChecksum1 = true;
                        }
                    }
                    
                    if (!hasChecksum1) {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Empty field of checksum in db ");
                        return (Response) new Response(ResponseType.ERROR).setContent("Empty field of checksum in db");
                    }
                    if (!fragment_checksum.equalsIgnoreCase(dbFragmentChecksum)) {
                    	DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Wrong checksum - The file fragment you attempted to use was corrupted, try again");
                        return (Response) new Response(ResponseType.ERROR).setContent("Wrong checksum - The file fragment you attempted to use was corrupted, try again");
                    }
                    fileFragment=fileFragment.trim();
                    fileContent=fileContent+fileFragment;
                    fileContent=fileContent.trim();
                        }
                        }
                        fileContent = Base64.encodeBase64String(
                                CryptUtil.decrypt(
                                        Base64.decodeBase64(fileContent), filekey, "AES"));
                        servresp.getField("file").setContent(fileContent);
                        resp = servresp;
                DarkCloud.getInstance().getLogger().info("[DarkCloud::Request] {SequenceNum " + req.getSequence() +
                        "} {Type GET} {Filename " + name + "}");
            
                        return resp;
                } catch (Exception e) {
                        DarkCloud.getInstance().getLogger().warn("[DarkCloud::ERROR] " + StackTraceUtil.getStackTrace(e));
                        return (Response) new Response(ResponseType.ERROR).setContent(StackTraceUtil.getStackTrace(e));
                }
        }
}