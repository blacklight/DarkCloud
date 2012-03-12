package it.unimore.weblab.darkcloud.protocol;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import it.unimore.weblab.darkcloud.db.Db.Table;
import it.unimore.weblab.darkcloud.db.Db.UpdateType;
import it.unimore.weblab.darkcloud.db.Tuple;
import it.unimore.weblab.darkcloud.net.DarkCloud;
import it.unimore.weblab.darkcloud.protocol.Response.ResponseType;
import it.unimore.weblab.darkcloud.util.StackTraceUtil;

public abstract class ServerResponseMethods extends ResponseMethods {
	/**
	 * Server-side put
	 * @param req Request object
	 * @return
	 */
	public static Response put(Request req)
	{
		Field nodeidField = req.getField("nodeid");
		
		if (nodeidField == null)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] No nodeid field was specified in the request");
			return (Response) new Response(ResponseType.ERROR).setContent("No nodeid field was specified in the request");
		}
		
		String nodeid = nodeidField.getContent();
		boolean hasNodeid = false;
		
		if (nodeid != null) {
			nodeid = nodeid.trim();
			
			if (!nodeid.isEmpty()) {
				hasNodeid = true;
			}
		}
		
		if (!hasNodeid)
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] No nodeid field was specified in the request");
			return (Response) new Response(ResponseType.ERROR).setContent("No nodeid field was specified in the request");
		}
		
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
		
		byte[] fileContent = file.getContent().getBytes();
		
		if (encoding.equalsIgnoreCase("base64")) {
			fileContent = Base64.decodeBase64(fileContent);
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
		
		String my_checksum = DigestUtils.md5Hex(fileContent);
		
		if (!my_checksum.equalsIgnoreCase(your_checksum))
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] Wrong checksum - The file you attempted to sent was corrupted, try again");
			return (Response) new Response(ResponseType.ERROR).setContent("Wrong checksum - The file you attempted to sent was corrupted, try again");
		}
        
		DarkCloud.getInstance().getLogger().info("[DarkCloud::Request] {SequenceNum " + req.getSequence() +
			"} {Type PUT} {Filename " + name +
			"} {Checksum " + my_checksum + "} {Uploader " + nodeid + "}");
		
		/// SAVE THE FILE TO DB START
		try {
            // TODO Check client permissions
            Tuple statement = new Tuple().
				setField("name", name).
				setField("content", file.getContent()).
				setField("checksum", my_checksum).
				setField("uploader", nodeid).
				setField("creationtime", new Date().getTime()).
				setField("modifytime", new Date().getTime());
            
            UpdateType updtype = UpdateType.CREATE;
            
            try {
			    DarkCloud.getInstance().getDb().insert(Table.FILE, statement);
            } catch (SQLException e) {
                updtype = UpdateType.UPDATE;
			    DarkCloud.getInstance().getDb().update(Table.FILE, statement, "name='" + name + "'");
            }
            
			DarkCloud.getInstance().getDb().insert(Table.FILEUPDATE, new Tuple().
				setField("filename", name).
				setField("nodeid", nodeid).
				setField("updtype", updtype).
				setField("updtime", new Date().getTime()));
		} catch (SQLException e) {
			return (Response) new Response(ResponseType.ERROR).setContent(StackTraceUtil.getStackTrace(e));
		}
		/// SAVE THE FILE TO DB END
		
		return new Response(ResponseType.ACK);
	}
    
    /**
     * Server-side GET request
     * @param req
     * @return
     */
	public static Response get(Request req)
	{
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
        
        ArrayList<ArrayList<String>> result = null;
        
		try {
			result = DarkCloud.getInstance().getDb().select(
				"SELECT content, checksum, creationtime, modifytime, uploader, modifiedby " +
				"FROM " + Table.FILE + " WHERE name='" + name + "'"
			);
		}
            
		catch (SQLException e) {
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Error] Database error: " + StackTraceUtil.getStackTrace(e));
			return (Response) new Response(ResponseType.ERROR).setContent(StackTraceUtil.getStackTrace(e));
		}
        
		if (result.isEmpty())
		{
			DarkCloud.getInstance().getLogger().warn("[DarkCloud::Warning] No such file or directory");
			return (Response) new Response(ResponseType.ERROR).setContent("No such file or directory");
		}
            
		ArrayList<String> row = result.get(0);
        String content = row.get(0);
        String checksum = row.get(1);
        String creationtime = row.get(2);
        String modifytime = row.get(3);
        String uploader = row.get(4);
        String modifiedby = row.get(5);
            
		DarkCloud.getInstance().getLogger().info("[DarkCloud::Request] {SequenceNum " + req.getSequence() +
			"} {Type GET} {Filename " + name +
			"} {Checksum " + checksum + "} {Uploader " + uploader + "}");
        
        return (Response) new Response(ResponseType.ACK).
        	appendField(new Field("file").
        		setAttribute("checksum", checksum).
        		setAttribute("creationtime", creationtime).
        		setAttribute("modifytime", modifytime).
        		setAttribute("uploader", uploader).
        		setAttribute("modifiedby", modifiedby).
        		setContent(content));
	}
	
	/**
	 * Server-side ping
	 * @param req Request object
	 * @return
	 */
	public static Response ping(Request req)
	{
		try {
			validateRequest(req);
		} catch (ProtocolException e) {
			return (Response) new Response(ResponseType.ERROR).setContent(StackTraceUtil.getStackTrace(e));
		}
		
		return ResponseMethods.ping(req);
	}
}
