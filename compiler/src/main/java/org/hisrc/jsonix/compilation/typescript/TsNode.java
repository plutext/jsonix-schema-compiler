package org.hisrc.jsonix.compilation.typescript;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A namespace level of the generated declarations: the declarations that live
 * directly in it, and child namespaces (used for scoped types such as
 * {@code Items.Item}, which are rendered as {@code namespace Items { interface Item }}).
 */
public class TsNode {

	public static final String INDENT = "  ";

	private final List<String> declarations = new ArrayList<String>();
	private final Map<String, TsNode> children = new LinkedHashMap<String, TsNode>();
	private final Set<String> names = new HashSet<String>();

	public TsNode child(String segment) {
		TsNode child = children.get(segment);
		if (child == null) {
			child = new TsNode();
			children.put(segment, child);
		}
		return child;
	}

	/** Descends along a dotted path such as {@code Items.Item}. */
	public TsNode path(String dottedPath) {
		TsNode node = this;
		if (dottedPath != null && !dottedPath.isEmpty()) {
			for (String segment : dottedPath.split("\\.")) {
				node = node.child(segment);
			}
		}
		return node;
	}

	/** Reserves a declaration name in this namespace. */
	public boolean reserve(String name) {
		return names.add(name);
	}

	/** Reserves {@code name}, or {@code name2}, {@code name3}, ... on collision. */
	public String uniqueName(String name) {
		if (reserve(name)) {
			return name;
		}
		for (int i = 2;; i++) {
			final String candidate = name + i;
			if (reserve(candidate)) {
				return candidate;
			}
		}
	}

	public void add(String declaration) {
		declarations.add(declaration);
	}

	public boolean isEmpty() {
		return declarations.isEmpty() && children.isEmpty();
	}

	public void render(StringBuilder sb, String indent) {
		boolean first = true;
		for (String declaration : declarations) {
			if (!first) {
				sb.append('\n');
			}
			first = false;
			appendIndented(sb, indent, declaration);
		}
		for (Map.Entry<String, TsNode> entry : children.entrySet()) {
			final TsNode child = entry.getValue();
			if (child.isEmpty()) {
				continue;
			}
			if (!first) {
				sb.append('\n');
			}
			first = false;
			sb.append(indent).append("export namespace ").append(entry.getKey()).append(" {\n");
			child.render(sb, indent + INDENT);
			sb.append(indent).append("}\n");
		}
	}

	private static void appendIndented(StringBuilder sb, String indent, String text) {
		for (String line : text.split("\n", -1)) {
			if (line.isEmpty()) {
				sb.append('\n');
			} else {
				sb.append(indent).append(line).append('\n');
			}
		}
	}
}
