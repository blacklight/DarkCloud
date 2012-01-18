package it.unimore.weblab.darkcloud.protocol;

/**
 * Exception raised in case of a protocol error in the communication between two nodes
 * @author blacklight
 */
public class ProtocolException extends Exception {
	private static final long serialVersionUID = 5923557362821029635L;
	private String msg;
	
	/**
	 * Default, empty constructor
	 */
	public ProtocolException() {
		this("");
	}
	
	/**
	 * Constructor with a custom description message
	 * @param msg
	 */
	public ProtocolException(String msg) {
		this.msg = msg;
	}
	
	/**
	 * @return The error message for this exception, if any
	 */
	public String what() {
		return msg;
	}
}
