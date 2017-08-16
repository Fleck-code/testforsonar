package net.jadoth.traversal;

import java.util.function.Predicate;

import net.jadoth.collections.types.XSet;

public final class ReferenceHandlerAccepting extends AbstractReferenceHandler
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final TraversalAcceptor traversalAcceptor;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	ReferenceHandlerAccepting(
		final TypeTraverserProvider traverserProvider,
		final XSet<Object>          alreadyHandled   ,
		final Predicate<Object>     isHandleable     ,
		final Predicate<Object>     isNode           ,
		final Predicate<Object>     isFull           ,
		final TraversalAcceptor     traversalAcceptor
	)
	{
		super(traverserProvider, alreadyHandled, isHandleable, isNode, isFull);
		this.traversalAcceptor = traversalAcceptor;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
											
	@Override
	final <T> void handle(final T instance, final TypeTraverser<T> traverser)
	{
		traverser.traverseReferences(instance, this, this.traversalAcceptor);
	}
}
