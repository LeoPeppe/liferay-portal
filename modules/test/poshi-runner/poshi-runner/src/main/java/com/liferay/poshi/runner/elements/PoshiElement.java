/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.poshi.runner.elements;

import com.liferay.poshi.runner.util.Dom4JUtil;
import com.liferay.poshi.runner.util.RegexUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;

/**
 * @author Kenji Heigel
 */
public abstract class PoshiElement extends DefaultElement {

	public PoshiElement(String name, Element element) {
		super(name);

		_addAttributes(element);
		_addElements(element);
	}

	public PoshiElement(String name, String readableSyntax) {
		super(name);

		parseReadableSyntax(readableSyntax);
	}

	@Override
	public void add(Attribute attribute) {
		if (attribute instanceof PoshiElementAttribute) {
			super.add(attribute);

			return;
		}

		super.add(new PoshiElementAttribute(attribute));
	}

	public abstract void parseReadableSyntax(String readableSyntax);

	@Override
	public boolean remove(Attribute attribute) {
		if (attribute instanceof PoshiElementAttribute) {
			return super.remove(attribute);
		}

		for (PoshiElementAttribute poshiElementAttribute :
				toPoshiElementAttributes(attributes())) {

			if (poshiElementAttribute.getAttribute() == attribute) {
				return super.remove(poshiElementAttribute);
			}
		}

		return false;
	}

	public String toReadableSyntax() {
		StringBuilder sb = new StringBuilder();

		for (PoshiElement poshiElement : toPoshiElements(elements())) {
			sb.append(poshiElement.toReadableSyntax());
		}

		return sb.toString();
	}

	protected static String getBracedContent(String readableSyntax) {
		return RegexUtil.getGroup(readableSyntax, ".*?\\{(.*)\\}", 1);
	}

	protected static String getNameFromAssignment(String assignment) {
		String name = assignment.split("=")[0];

		name = name.trim();
		name = name.replaceAll("@", "");
		name = name.replaceAll("property ", "");

		return name.replaceAll("var ", "");
	}

	protected static String getParentheticalContent(String readableSyntax) {
		return RegexUtil.getGroup(readableSyntax, ".*?\\((.*)\\)", 1);
	}

	protected static String getQuotedContent(String readableSyntax) {
		return RegexUtil.getGroup(readableSyntax, ".*?\"(.*)\"", 1);
	}

	protected void addElementFromReadableSyntax(String readableSyntax) {
		PoshiElement poshiElement = PoshiElementFactory.newPoshiElement(
			readableSyntax);

		add(poshiElement);
	}

	protected String createReadableBlock(String content) {
		StringBuilder sb = new StringBuilder();

		String pad = getPad();

		sb.append("\n");
		sb.append(pad);
		sb.append(getBlockName());
		sb.append(" {");

		if (content.startsWith("\n\n")) {
			content = content.replaceFirst("\n\n", "\n");
		}

		content = content.replaceAll("\n", "\n" + pad);

		sb.append(content.replaceAll("\n\t\n", "\n\n"));

		sb.append("\n");
		sb.append(pad);
		sb.append("}");

		return sb.toString();
	}

	protected abstract String getBlockName();

	protected String getPad() {
		return "\t";
	}

	protected boolean isBalancedReadableSyntax(String readableSyntax) {
		Stack<Character> stack = new Stack<>();

		for (char c : readableSyntax.toCharArray()) {
			if (!stack.isEmpty()) {
				Character topCodeBoundary = stack.peek();

				if (c == _codeBoundariesMap.get(topCodeBoundary)) {
					stack.pop();

					continue;
				}

				if (topCodeBoundary == '\"') {
					continue;
				}
			}

			if (_codeBoundariesMap.containsKey(c)) {
				stack.push(c);

				continue;
			}

			if (_codeBoundariesMap.containsValue(c)) {
				return false;
			}
		}

		return stack.isEmpty();
	}

	protected boolean isBalanceValidationRequired(String readableSyntax) {
		readableSyntax = readableSyntax.trim();

		if (readableSyntax.endsWith(";") || readableSyntax.endsWith("}")) {
			return true;
		}

		return false;
	}

	protected boolean isValidReadableBlock(String readableSyntax) {
		readableSyntax = readableSyntax.trim();

		if (readableSyntax.startsWith("property") ||
			readableSyntax.startsWith("var")) {

			if (readableSyntax.endsWith(";")) {
				return true;
			}

			return false;
		}

		if (isBalanceValidationRequired(readableSyntax)) {
			return isBalancedReadableSyntax(readableSyntax);
		}

		return false;
	}

	protected List<PoshiElementAttribute> toPoshiElementAttributes(
		List<?> list) {

		if (list == null) {
			return null;
		}

		List<PoshiElementAttribute> poshiElementAttributes = new ArrayList<>(
			list.size());

		for (Object object : list) {
			poshiElementAttributes.add((PoshiElementAttribute)object);
		}

		return poshiElementAttributes;
	}

	protected List<PoshiElement> toPoshiElements(List<?> list) {
		if (list == null) {
			return null;
		}

		List<PoshiElement> poshiElements = new ArrayList<>(list.size());

		for (Object object : list) {
			poshiElements.add((PoshiElement)object);
		}

		return poshiElements;
	}

	private void _addAttributes(Element element) {
		for (Attribute attribute :
				Dom4JUtil.toAttributeList(element.attributes())) {

			add(new PoshiElementAttribute((Attribute)attribute.clone()));
		}
	}

	private void _addElements(Element element) {
		for (Element childElement :
				Dom4JUtil.toElementList(element.elements())) {

			add(PoshiElementFactory.newPoshiElement(childElement));
		}
	}

	private static final Map<Character, Character> _codeBoundariesMap =
		new HashMap<>();

	static {
		_codeBoundariesMap.put('\"', '\"');
		_codeBoundariesMap.put('(', ')');
		_codeBoundariesMap.put('{', '}');
		_codeBoundariesMap.put('[', ']');
	}

}