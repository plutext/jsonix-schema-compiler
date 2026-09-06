package org.hisrc.jsonix.compilation.typescript;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Small helpers for rendering TypeScript declarations. Types are represented
 * as already-rendered strings; the grammar of declaration files is small enough
 * that no code model is required.
 */
public final class Ts {

	private Ts() {
	}

	public static final String ANY = "any";
	public static final String STRING = "string";
	public static final String NUMBER = "number";
	public static final String BOOLEAN = "boolean";
	public static final String NEVER = "never";

	/** Support types emitted into every generated declaration file. */
	public static final String XML_QNAME = "XmlQName";
	public static final String XML_CALENDAR = "XmlCalendar";
	public static final String XML_DURATION = "XmlDuration";
	public static final String XML_DOM_NODE = "XmlDomNode";
	public static final String XML_ANY_ELEMENT = "XmlAnyElement";
	public static final String XML_ANY_ATTRIBUTES = "XmlAnyAttributes";
	public static final String TYPED_NAMED_VALUE = "TypedNamedValue";
	public static final String JSONIX_MAPPING = "JsonixMapping";
	/** Phantom member carrying the root element type of a mapping (jsonix-CR-001 item 6). */
	public static final String ROOT_ELEMENT_MEMBER = "__rootElement";
	public static final String TYPE_NAME_PROPERTY = "TYPE_NAME";

	private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList("break", "case", "catch",
			"class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum", "export",
			"extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof", "new", "null",
			"return", "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with",
			"implements", "interface", "let", "package", "private", "protected", "public", "static", "yield",
			"any", "boolean", "number", "string", "symbol", "never", "unknown", "object", "type", "namespace",
			"module", "declare", "abstract", "as", "async", "await", "constructor", "get", "set", "is", "keyof",
			"readonly", "require", "of", "undefined", "bigint"));

	public static boolean isIdentifier(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		final char first = name.charAt(0);
		if (!(Character.isLetter(first) || first == '_' || first == '$')) {
			return false;
		}
		for (int i = 1; i < name.length(); i++) {
			final char c = name.charAt(i);
			if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$')) {
				return false;
			}
		}
		return true;
	}

	/** A property name as it may appear in an interface: quoted if necessary. */
	public static String member(String name) {
		return isIdentifier(name) ? name : stringLiteral(name);
	}

	/** Turns an arbitrary string into a usable, reserved-word-free identifier. */
	public static String identifier(String name) {
		final StringBuilder sb = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			final char c = name.charAt(i);
			sb.append(Character.isLetterOrDigit(c) || c == '_' || c == '$' ? c : '_');
		}
		String result = sb.toString();
		if (result.isEmpty() || Character.isDigit(result.charAt(0)) || RESERVED.contains(result)) {
			result = "_" + result;
		}
		return result;
	}

	public static String upperFirst(String name) {
		if (name == null || name.isEmpty()) {
			return name;
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	public static String stringLiteral(String value) {
		final StringBuilder sb = new StringBuilder(value.length() + 2);
		sb.append('\'');
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			switch (c) {
			case '\\':
				sb.append("\\\\");
				break;
			case '\'':
				sb.append("\\'");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
		}
		sb.append('\'');
		return sb.toString();
	}

	public static String array(String type) {
		return type.contains(" | ") ? "(" + type + ")[]" : type + "[]";
	}

	public static String union(Collection<String> types) {
		final Set<String> distinct = new LinkedHashSet<String>(types);
		if (distinct.isEmpty()) {
			return NEVER;
		}
		final StringBuilder sb = new StringBuilder();
		for (String type : distinct) {
			if (sb.length() > 0) {
				sb.append(" | ");
			}
			sb.append(type);
		}
		return sb.toString();
	}

	public static String generic(String name, String argument) {
		return name + "<" + argument + ">";
	}
}
