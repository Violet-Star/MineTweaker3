/*
 * This file is part of MineTweaker API, licensed under the MIT License (MIT).
 * 
 * Copyright (c) 2014 MineTweaker <http://minetweaker3.powerofbytes.com>
 */
package org.openzen.zencode.symbolic.annotations;

import java.util.ArrayList;
import java.util.List;
import org.openzen.zencode.parser.ParsedAnnotation;
import org.openzen.zencode.symbolic.expression.IPartialExpression;
import org.openzen.zencode.symbolic.method.IMethod;
import org.openzen.zencode.symbolic.scope.IDefinitionScope;
import org.openzen.zencode.symbolic.type.ITypeInstance;
import org.openzen.zencode.util.CodePosition;

/**
 *
 * @author Stan
 * @param <E>
 * @param <T>
 */
public class SymbolicAnnotation<E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
{
	public static <ES extends IPartialExpression<ES, TS>, TS extends ITypeInstance<ES, TS>>
		 List<SymbolicAnnotation<ES, TS>> compileAll(List<ParsedAnnotation> annotations, IDefinitionScope<ES, TS> scope)
	{
		List<SymbolicAnnotation<ES, TS>> result = new ArrayList<SymbolicAnnotation<ES, TS>>();
		for (ParsedAnnotation annotation : annotations) {
			result.add(annotation.compile(scope));
		}
		return result;
	}
	
	private final CodePosition position;
	private T type;
	private IMethod<E, T> constructor;
	private List<E> arguments;
	
	public SymbolicAnnotation(CodePosition position, T type, IMethod<E, T> constructor, List<E> arguments)
	{
		this.position = position;
		this.type = type;
		this.constructor = constructor;
		this.arguments = arguments;
	}
	
	public void validate()
	{
		for (E argument : arguments) {
			argument.validate();
		}
		
		constructor.validateCall(position, type.getScope().getConstantEnvironment(), arguments);
	}
}
