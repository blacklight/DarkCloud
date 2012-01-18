package it.unimore.weblab.darkcloud.db;

import java.io.File;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Types;
import java.util.ArrayList;

public class Db {
	private File dbFile;
	private String dbConnString;
	
	public static enum NodeType
	{
		CLIENT,
		SERVER
	};
	
	public static enum UpdateType
	{
		CREATE,
		UPDATE,
		DELETE
	};
	
	public static enum PermissionType
	{
		READ,
		UPDATE,
		DELETE
	};
	
	public static enum Table
	{
		FILE,
		NODETYPE,
		UPDATETYPE,
		FILEFRAGMENT,
		FILEUPDATE,
		NODE,
		PERMISSIONTYPE,
		FILEPERMISSION
	};
	
	/**
	 * Create or re-create the database
	 * @param conn Connection object to the database
	 * @param stat Statement object refered to the database
	 * @throws SQLException 
	 */
	private void createDb() throws SQLException
	{
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e)  {}
		
		Connection conn = DriverManager.getConnection(dbConnString);
		Statement stat = conn.createStatement();
		
		stat.executeUpdate("DROP TABLE IF EXISTS file;");
		stat.executeUpdate(
			"CREATE TABLE file(" +
			"name text primary key, " +
			"content text, " +
			"key text, " +
			"checksum text, " +
			"uploader text," +
			"creationtime datetime, " +
			"modifytime datetime, " +
			"modifiedby text);"
		);
		
		stat.executeUpdate("DROP TABLE IF EXISTS nodetype;");
		stat.executeUpdate(
			"CREATE TABLE nodetype(" +
			"nodetypeid integer primary key, " +
			"nodetypestr text);"
		);
		
		stat.executeUpdate("DROP TABLE IF EXISTS updatetype;");
		stat.executeUpdate(
			"CREATE TABLE updatetype(" +
			"updatetypeid integer, " +
			"updatetypestr text);"
		);
		
		stat.executeUpdate("DROP TABLE IF EXISTS filefragment;");
		stat.executeUpdate(
			"CREATE TABLE filefragment(" +
			"name text, " +
			"fragmentid integer, " +
			"checksum text, " +
            "nodeid text, " +
			"primary key(name, fragmentid), " +
			"foreign key(nodeid) references node(nodeid), " +
			"foreign key(name) references file(name));"
		);
		
		stat.executeUpdate("DROP TABLE IF EXISTS fileupdate;");
		stat.executeUpdate(
			"CREATE TABLE fileupdate(" +
			"updid integer primary key, " +
			"filename text, " +
			"nodeid text, " +
			"updtype integer, " +
			"upddate datetime, " +
			"foreign key(filename) references file(name), " +
			"foreign key(updtype) references updatetype(updatetypeid));"
		);
		
		stat.executeUpdate("DROP TABLE IF EXISTS node;");
		stat.executeUpdate(
			"CREATE TABLE node(" +
			"nodeid text primary key, " +
			"pubkey text, " +
			"type integer, " +
			"addr text, " +
			"port integer," +
			"foreign key(type) references nodetype(nodetypeid));"
		);
		
		stat.executeUpdate("DROP TABLE IF EXISTS permissiontype");
		stat.executeUpdate(
			"CREATE TABLE permissiontype(" +
			"permtypeid integer primary key, " +
			"permtypestr text);"
		);
		
		stat.executeUpdate("DROP TABLE IF EXISTS filepermission;");
		stat.executeUpdate(
			"CREATE TABLE filepermission(" +
			"nodeid text," +
			"filename text, " +
			"perm integer, " +
			"primary key(nodeid, filename), " +
			"foreign key(nodeid) references node(nodeid), " +
			"foreign key(filename) references file(name), " +
			"foreign key(perm) references permission(permid));"
		);
		
		NodeType[] nodeTypes = NodeType.values();
		
		if (nodeTypes.length > 0)
		{
			for (NodeType type : nodeTypes)
			{
				insert(Table.NODETYPE, new Tuple().
					setField("nodetypeid", new Integer(type.ordinal()+1)).
					setField("nodetypestr", type.toString()));
			}
		}
		
		UpdateType[] updTypes = UpdateType.values();
		
		if (updTypes.length > 0)
		{
			for (UpdateType type : updTypes)
			{
				insert(Table.UPDATETYPE, new Tuple().
					setField("updatetypeid", new Integer(type.ordinal()+1)).
					setField("updatetypestr", type.toString()));
			}
		}
		
		PermissionType[] perms = PermissionType.values();
		
		if (perms.length > 0)
		{
			for (PermissionType type : perms)
			{
				insert(Table.PERMISSIONTYPE, new Tuple().
					setField("permtypeid", new Integer(type.ordinal()+1)).
					setField("permtypestr", type.toString()));
			}
		}
	}
	
	/**
	 * Constructor for a database object
	 * @param dbFile Database file where the information will be stored
	 * @throws ClassNotFoundException The specified JDBC driver (SQLite in this case) was not found
	 * @throws SQLException Generic database exception
	 */
	public Db(File dbFile) throws ClassNotFoundException, SQLException
	{
		this.dbFile = dbFile;
		dbConnString = "jdbc:sqlite:" + this.dbFile.getAbsolutePath();
		
		try {
			select("SELECT * FROM file");
		} catch (SQLException e) {
			System.out.println("The database file " + this.dbFile.getAbsolutePath() + " does not exist - Creating it...");
			
			try {
				createDb();
				System.out.println("Database file " + this.dbFile.getAbsolutePath() + " successfully created");
			} catch (SQLException e1) {
				System.out.println("Unable to create the database file " + this.dbFile.getAbsolutePath());
				e1.printStackTrace();
			}
		}
	}
	
	/**
	 * Insert a tuple into a table
	 * @param table
	 * @param data
	 * @throws SQLException
	 */
	public void insert(Table table, Tuple data) throws SQLException
	{
		String insertStatement = "INSERT INTO " + table.toString().toLowerCase() + "(";
		boolean isFirst = true;
		ArrayList<String> fieldsnames = data.getFields();
		
		for (String field : fieldsnames)
		{
			if (isFirst)
			{
				insertStatement += field;
				isFirst = false;
			} else {
				insertStatement += ", " + field;
			}
		}
		
		insertStatement += ") VALUES(";
		
		for (int i=0; i < fieldsnames.size(); i++)
		{
			if (i == 0) {
				insertStatement += "?";
			} else {
				insertStatement += ", ?";
			}
		}
		
		insertStatement += ");";
		Statement stat = null;
		Connection conn = null;
		
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {}
		
		try
		{
			conn = DriverManager.getConnection(dbConnString);
			stat = conn.createStatement();
			
			PreparedStatement prep = conn.prepareStatement(insertStatement);
			
			for (int i=0; i < fieldsnames.size(); i++)
			{
				Object value = data.getField(fieldsnames.get(i));
				
				if (value == null) {
					prep.setNull(i+1, Types.VARCHAR);
				} else if (value.getClass() == Integer.class) {
					prep.setInt(i+1, (Integer) value);
				} else if (value.getClass() == String.class) {
					prep.setString(i+1, (String) value);
				} else if (value.getClass() == Date.class) {
					prep.setDate(i+1, (Date) value);
				} else if (value.getClass() == Blob.class) {
					prep.setBlob(i+1, (Blob) value);
				} else if (value.getClass() == Time.class) {
					prep.setTime(i+1, (Time) value);
				} else if (value.getClass() == Double.class) {
					prep.setDouble(i+1, (Double) value);
				} else if (value.getClass() == Float.class) {
					prep.setFloat(i+1, (Float) value);
				}
			}
			
			prep.addBatch();
			conn.setAutoCommit(false);
			prep.executeBatch();
			conn.setAutoCommit(true);
		} finally {
			if (stat != null) {
				stat.close();
			}
			
			if (conn != null) {
				conn.close();
			}
		}
	}
	
	/**
	 * Update a table with the given tuple
	 * @param table
	 * @param data
	 * @param whereCondition WHERE condition, e.g. "id=1 and name='foo'". It could be null, in this case all the rows in the table will be updated
	 * @throws SQLException 
	 */
	public void update(Table table, Tuple data, String whereCondition) throws SQLException
	{
		boolean emptyCond = true;
		
		if (whereCondition != null) {
			if (!whereCondition.isEmpty()) {
				emptyCond = false;
			}
		}
		
		String updateStatement = "UPDATE " + table.toString().toLowerCase() + " SET ";
		boolean isFirst = true;
		ArrayList<String> fieldsnames = data.getFields();
		
		for (String field : fieldsnames)
		{
			if (isFirst)
			{
				updateStatement += field + "=?";
				isFirst = false;
			} else {
				updateStatement += " AND " + field + "=?";
			}
		}
		
		if (!emptyCond) {
			updateStatement += " WHERE " + whereCondition;
		}
		
		Statement stat = null;
		Connection conn = null;
		
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {}
		
		try
		{
			conn = DriverManager.getConnection(dbConnString);
			stat = conn.createStatement();
			
			PreparedStatement prep = conn.prepareStatement(updateStatement);
			
			for (int i=0; i < fieldsnames.size(); i++)
			{
				Object value = data.getField(fieldsnames.get(i));
				
				if (value == null) {
					prep.setNull(i+1, Types.VARCHAR);
				} else if (value.getClass() == Integer.class) {
					prep.setInt(i+1, (Integer) value);
				} else if (value.getClass() == String.class) {
					prep.setString(i+1, (String) value);
				} else if (value.getClass() == Date.class) {
					prep.setDate(i+1, (Date) value);
				} else if (value.getClass() == Blob.class) {
					prep.setBlob(i+1, (Blob) value);
				} else if (value.getClass() == Time.class) {
					prep.setTime(i+1, (Time) value);
				} else if (value.getClass() == Double.class) {
					prep.setDouble(i+1, (Double) value);
				} else if (value.getClass() == Float.class) {
					prep.setFloat(i+1, (Float) value);
				}
			}
			
			prep.addBatch();
			conn.setAutoCommit(false);
			prep.executeBatch();
			conn.setAutoCommit(true);
		} finally {
			if (stat != null) {
				stat.close();
			}
			
			if (conn != null) {
				conn.close();
			}
		}
	}
	
	/**
	 * Execute an SQL query on the database and return a ResulSet object
	 * @param query
	 * @return
	 * @throws SQLException
	 */
	public ArrayList<ArrayList<String>> select(String query) throws SQLException
	{
		Connection conn = null;
		Statement stat = null;
		ResultSet res = null;
		ArrayList<ArrayList<String>> rows = new ArrayList<ArrayList<String>>();
		
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {}
		
		try
		{
			conn = DriverManager.getConnection(dbConnString);
			stat = conn.createStatement();
			res = stat.executeQuery(query);
			
			while (res.next())
			{
				ArrayList<String> row = new ArrayList<String>();
				boolean validColumn = true;
				
				for (int i=1; validColumn; i++)
				{
					try {
						row.add(res.getString(i));
					} catch (SQLException e) {
						validColumn = false;
					}
				}
				
				if (!row.isEmpty()) {
					rows.add(row);
				}
			}
			
			res.close();
		} finally {
			if (stat != null) {
				stat.close();
			}
			
			if (conn != null) {
				conn.close();
			}
		}
		
		return rows;
	}
}
