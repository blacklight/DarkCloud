package it.unimore.weblab.darkcloud.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Utility class which returns the stack trace of a Throwable object as a String
 * @author blacklight
 *
 */
public final class StackTraceUtil {
	public static String getStackTrace(Throwable aThrowable)
	{
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		aThrowable.printStackTrace(printWriter);
		return result.toString();
	}
}
