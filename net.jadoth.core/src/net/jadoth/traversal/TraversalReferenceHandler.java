package net.jadoth.traversal;

public interface TraversalReferenceHandler extends TraversalEnqueuer
{
	public void handleAsFull(Object[] instances);
	
	public void handleAsNode(Object[] instances);
	
	public void handleAsLeaf(Object[] instances);
}
