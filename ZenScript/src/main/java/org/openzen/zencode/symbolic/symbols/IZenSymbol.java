/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.openzen.zencode.symbolic.symbols;

import org.openzen.zencode.symbolic.scope.IMethodScope;
import org.openzen.zencode.symbolic.expression.IPartialExpression;
import org.openzen.zencode.symbolic.type.ITypeInstance;
import org.openzen.zencode.util.CodePosition;

/**
 *
 * @author Stan Hebben
 * @param <E>
 * @param <T>
 */
public interface IZenSymbol<E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
{
	public IPartialExpression<E, T> instance(CodePosition position, IMethodScope<E, T> scope);
}
