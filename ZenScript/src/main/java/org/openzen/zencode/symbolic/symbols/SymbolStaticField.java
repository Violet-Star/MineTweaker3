/*
 * This file is part of MineTweaker API, licensed under the MIT License (MIT).
 * 
 * Copyright (c) 2014 MineTweaker <http://minetweaker3.powerofbytes.com>
 */
package org.openzen.zencode.symbolic.symbols;

import org.openzen.zencode.symbolic.scope.IMethodScope;
import org.openzen.zencode.symbolic.expression.IPartialExpression;
import org.openzen.zencode.symbolic.expression.partial.PartialStaticField;
import org.openzen.zencode.symbolic.field.IField;
import org.openzen.zencode.symbolic.type.ITypeInstance;
import org.openzen.zencode.util.CodePosition;

/**
 *
 * @author Stan
 * @param <E>
 * @param <T>
 */
public class SymbolStaticField<E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		implements IZenSymbol<E, T>
{
	private final IField<E, T> field;

	public SymbolStaticField(IField<E, T> field)
	{
		this.field = field;
	}

	@Override
	public IPartialExpression<E, T> instance(CodePosition position, IMethodScope<E, T> scope)
	{
		return new PartialStaticField<E, T>(position, scope, field);
	}
}
