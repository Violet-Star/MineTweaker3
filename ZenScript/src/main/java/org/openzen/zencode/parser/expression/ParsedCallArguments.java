/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openzen.zencode.parser.expression;

import java.util.ArrayList;
import java.util.List;
import org.openzen.zencode.IZenCompileEnvironment;
import org.openzen.zencode.symbolic.scope.IMethodScope;
import org.openzen.zencode.symbolic.method.IMethod;
import org.openzen.zencode.symbolic.method.MethodParameter;
import org.openzen.zencode.lexer.ZenLexer;
import static org.openzen.zencode.lexer.ZenLexer.*;
import org.openzen.zencode.runtime.IAny;
import org.openzen.zencode.symbolic.expression.IPartialExpression;
import org.openzen.zencode.symbolic.method.MethodHeader;
import org.openzen.zencode.symbolic.type.ITypeInstance;

/**
 *
 * @author Stan
 */
public class ParsedCallArguments
{
	public static ParsedCallArguments parse(ZenLexer lexer)
	{
		lexer.required(T_BROPEN, "( expected");

		List<ParsedCallArgument> arguments = new ArrayList<ParsedCallArgument>();

		boolean canHaveMore = true;

		while (canHaveMore) {
			arguments.add(ParsedCallArgument.parse(lexer));

			if (lexer.optional(T_COMMA) == null)
				canHaveMore = false;
		}

		lexer.required(T_BRCLOSE, ") expected");
		return new ParsedCallArguments(arguments);
	}

	private final List<ParsedCallArgument> arguments;
	private final int numUnkeyedValues;

	public ParsedCallArguments(List<ParsedCallArgument> arguments)
	{
		this.arguments = arguments;

		int numberOfUnkeyedValues = arguments.size();
		while (numberOfUnkeyedValues > 0 && arguments.get(numberOfUnkeyedValues - 1).getKey() != null)
			numberOfUnkeyedValues--;

		numUnkeyedValues = numberOfUnkeyedValues;
	}

	public <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 MatchedArguments<E, T> compile(List<IMethod<E, T>> methods, IMethodScope<E, T> environment)
	{
		List<T> predictedTypes = predictArgumentTypes(methods);
		List<E> compiledArguments = compileArguments(environment, predictedTypes);

		MatchedArguments<E, T> matchedExactly = matchExactly(methods, environment, compiledArguments);
		if (matchedExactly != null)
			return matchedExactly;

		return matchWithImplicitConversion(methods, environment, compiledArguments);
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 List<E> compileArguments(IMethodScope<E, T> environment, List<T> predictedTypes)
	{
		List<E> compiled = new ArrayList<E>();
		for (int i = 0; i < arguments.size(); i++) {
			compiled.add(arguments.get(i).getValue().compile(environment, predictedTypes.get(i)));
		}

		return compiled;
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 MatchedArguments<E, T> matchExactly(List<IMethod<E, T>> methods, IMethodScope<E, T> environment, List<E> compiled)
	{
		for (IMethod<E, T> method : methods) {
			List<E> matched = matchArgumentsExactly(method.getMethodHeader(), environment, compiled);
			if (matched != null)
				return new MatchedArguments<E, T>(method, matched);
		}

		return null;
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 MatchedArguments<E, T> matchWithImplicitConversion(List<IMethod<E, T>> methods, IMethodScope<E, T> environment, List<E> compiled)
	{
		for (IMethod<E, T> method : methods) {
			List<E> matched = matchArgumentsWithImplicitConversion(method.getMethodHeader(), environment, compiled);
			if (matched != null)
				return new MatchedArguments<E, T>(method, matched);
		}

		return null;
	}

	public IAny[] compileConstants(IZenCompileEnvironment<?, ?> environment)
	{
		if (hasKeyedArguments())
			return null;

		IAny[] results = new IAny[arguments.size()];
		for (int i = 0; i < results.length; i++) {
			IAny value = arguments.get(i).getValue().eval(environment);
			if (value == null)
				return null;

			results[i] = value;
		}

		return results;
	}

	// #######################
	// ### Private methods ###
	// #######################
	private boolean hasKeyedArguments()
	{
		for (ParsedCallArgument argument : arguments) {
			if (argument.getKey() != null)
				return true;
		}

		return false;
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 List<T> predictArgumentTypes(List<IMethod<E, T>> methods)
	{
		List<T> predictedTypes = new ArrayList<T>();
		boolean[] ambiguous = new boolean[arguments.size()];

		for (IMethod<E, T> method : methods) {
			MethodHeader<E, T> header = method.getMethodHeader();

			if (!header.accepts(arguments.size()))
				continue;

			if (!checkUnusedArgumentPositions(header))
				continue;

			predictArgumentTypesForMethod(header, predictedTypes, ambiguous);
		}

		return predictedTypes;
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 boolean[] getUsedKeyedArgumentPositions(MethodHeader<E, T> method)
	{
		boolean[] isUsed = new boolean[method.getParameters().size() - numUnkeyedValues];
		for (int i = numUnkeyedValues; i < arguments.size(); i++) {
			int parameterIndex = method.getParameterIndex(getArgumentKey(i));
			if (parameterIndex < numUnkeyedValues)
				return null;
			else
				isUsed[parameterIndex - numUnkeyedValues] = true;
		}

		return isUsed;
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 boolean checkUnusedArgumentPositions(MethodHeader<E, T> method)
	{
		// this method assumes that number of arguments has already been checked
		// then only needs to check for unused keyed argument positions
		boolean[] isUsed = getUsedKeyedArgumentPositions(method);

		List<MethodParameter<E, T>> methodArguments = method.getParameters();
		for (int i = 0; i < isUsed.length; i++) {
			if (!isUsed[i] && methodArguments.get(numUnkeyedValues + i).getDefaultValue() == null)
				return false;
		}

		return true;
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 void predictArgumentTypesForMethod(MethodHeader<E, T> method, List<T> predictedTypes, boolean[] ambiguous)
	{
		List<MethodParameter<E, T>> methodArguments = method.getParameters();

		for (int i = 0; i < numUnkeyedValues; i++) {
			if (ambiguous[i])
				continue;

			if (predictedTypes.get(i) == null)
				predictedTypes.set(i, methodArguments.get(i).getType());
			else if (!predictedTypes.get(i).equals(methodArguments.get(i).getType())) {
				predictedTypes.set(i, null);
				ambiguous[i] = true;
			}
		}

		for (int i = numUnkeyedValues; i < arguments.size(); i++) {
			if (ambiguous[i])
				continue;

			T argumentType = methodArguments.get(method.getParameterIndex(getArgumentKey(i))).getType();

			if (predictedTypes.get(i) == null)
				predictedTypes.set(i, argumentType);
			else if (!predictedTypes.get(i).equals(argumentType)) {
				predictedTypes.set(i, null);
				ambiguous[i] = true;
			}
		}
	}

	private String getArgumentKey(int index)
	{
		return arguments.get(index).getKey();
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 List<E> matchArgumentsExactly(MethodHeader<E, T> method, IMethodScope<E, T> environment, List<E> compiled)
	{
		return matchArguments(method, environment, compiled, true);
	}

	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 List<E> matchArgumentsWithImplicitConversion(MethodHeader<E, T> method, IMethodScope<E, T> environment, List<E> compiled)
	{
		return matchArguments(method, environment, compiled, false);
	}

	/**
	 * Performs a matching of compiled expressions to the method arguments.
	 * Performs type casting where necessary.
	 *
	 * @param method
	 * @param compiled
	 * @param exactly
	 * @return
	 */
	@SuppressWarnings({"unchecked"})
	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 List<E> matchArguments(MethodHeader<E, T> method, IMethodScope<E, T> environment, List<E> compiled, boolean exactly)
	{
		int numUnkeyed = numUnkeyedValues;

		List<MethodParameter<E, T>> methodArguments = method.getParameters();

		// little optimization
		if (!method.accepts(numUnkeyed))
			return null;

		boolean[] isUsed = new boolean[methodArguments.size() - numUnkeyed];
		boolean isVarargCall = false;
		T varargBaseType = null;

		if (method.isVarargs())
			varargBaseType = method.getVarArgBaseType();

		// check parameters without names
		for (int i = 0; i < numUnkeyed; i++) {
			if (method.isVarargs() && i >= methodArguments.size() - 1)
				if (matches(compiled.get(i), varargBaseType, exactly)) {
					isVarargCall = true;
					continue;
				}

			if (i >= methodArguments.size())
				return null;

			if (!matches(compiled.get(i), methodArguments.get(i).getType(), exactly))
				return null;
		}

		// is this a vararg callStaticWithConstants with an empty array?
		if (method.isVarargs() && numUnkeyed == compiled.size() && numUnkeyed == methodArguments.size() - 1)
			isVarargCall = true;

		// check parameters with names
		for (int i = numUnkeyed; i < this.arguments.size(); i++) {
			int parameterIndex = method.getParameterIndex(arguments.get(i).getKey());
			if (parameterIndex < numUnkeyed)
				return null;
			else if (exactly && !methodArguments.get(parameterIndex).getType().equals(compiled.get(i).getType()))
				return null;
			else if (!exactly && !methodArguments.get(parameterIndex).getType().equals(compiled.get(i).getType()))
				return null;
			else
				isUsed[parameterIndex - numUnkeyed] = true;
		}

		// check if all non-optional arguments are filled
		if (!checkUnusedArgumentPositions(method))
			return null;

		List<E> result = new ArrayList<E>();

		// fill arguments without name
		if (isVarargCall)
			numUnkeyed = methodArguments.size() - 1;

		for (int i = 0; i < numUnkeyed; i++) {
			result.add(compiled.get(i).cast(compiled.get(i).getPosition(), methodArguments.get(i).getType()));
		}

		if (isVarargCall)
			result.add(assembleVararg(methodArguments.get(methodArguments.size() - 1), environment, compiled, numUnkeyed));
		else {
			// fill keyed arguments
			for (int i = numUnkeyed; i < arguments.size(); i++) {
				int parameterIndex = method.getParameterIndex(arguments.get(i).getKey());
				result.add(compiled.get(i).cast(compiled.get(i).getPosition(), methodArguments.get(parameterIndex).getType()));
			}

			// fill default values
			for (int i = 0; i < isUsed.length; i++) {
				if (!isUsed[i])
					result.add(methodArguments.get(i + numUnkeyed).getDefaultValue());
			}
		}
		
		return result;
	}

	/**
	 * Check if this type matches another type. If exactly is true, the types
	 * must be equal. Otherwise, the expression type must be implicitly castable
	 * to the argument type.
	 *
	 * @param expression expression to check
	 * @param type argument type
	 * @param exactly
	 * @return
	 */
	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 boolean matches(E expression, T type, boolean exactly)
	{
		if (exactly)
			return expression.getType().equals(type);
		else
			return expression.getType().canCastImplicit(type);
	}

	/**
	 * Assembles remaining arguments into a single array. Used to assemble
	 * varargs values. Also works if from == compiled.length .
	 *
	 * @param argument argument to assemble for
	 * @param compiled compiled expressions array
	 * @param fromIndex index in the expressions array to assemble from
	 * @return array expression with vararg values
	 */
	private <E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
		 E assembleVararg(MethodParameter<E, T> argument, IMethodScope<E, T> scope, List<E> compiled, int fromIndex)
	{
		T varargBaseType = argument.getType().getArrayBaseType();

		// combine varargs into an array expression
		List<E> arrayMembers = new ArrayList<E>();
		for (int i = fromIndex; i < compiled.size(); i++) {
			arrayMembers.add(compiled.get(i).cast(
					compiled.get(i).getPosition(),
					varargBaseType));
		}
		
		return scope.getExpressionCompiler().array(
				compiled.get(fromIndex).getPosition(),
				scope,
				argument.getType(),
				arrayMembers);
	}

	public class MatchedArguments<E extends IPartialExpression<E, T>, T extends ITypeInstance<E, T>>
	{
		public final IMethod<E, T> method;
		public final List<E> arguments;

		public MatchedArguments(IMethod<E, T> method, List<E> arguments)
		{
			this.method = method;
			this.arguments = arguments;
		}
	}
}
