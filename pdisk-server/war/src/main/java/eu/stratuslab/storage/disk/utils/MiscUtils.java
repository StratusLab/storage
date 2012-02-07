package eu.stratuslab.storage.disk.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;

public final class MiscUtils {

	private MiscUtils() {

	}

	public static void checkForNullEntity(Representation entity) {
		if (entity == null) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"post with null entity");
		}
	}

	public static String getTimestamp() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();

		return dateFormat.format(date);
	}

	public static String join(List<String> list, String conjunction) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String item : list) {
			if (first)
				first = false;
			else
				sb.append(conjunction);
			sb.append(item);
		}
		return sb.toString();
	}

	public static String sub(String pattern, String replace, String string) {
		Pattern p = Pattern.compile(pattern);
		Matcher m = p.matcher(string);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(sb, replace);
		}
		m.appendTail(sb);
		return sb.toString();
	}

	public static <T> T last(T[] array) {
		return array[array.length - 1];
	}

	public static void sleep(int ms) {
		try {
			Thread.currentThread();
			Thread.sleep(ms);
		} catch (InterruptedException ie) {
			// Do nothing
		}
	}
}
