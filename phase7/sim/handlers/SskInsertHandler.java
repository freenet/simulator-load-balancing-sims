// This software has been placed in the public domain by its author

// The state of an SSK insert as stored at each node along the path

package sim.handlers;
import sim.*;
import sim.messages.*;
import java.util.HashSet;

public class SskInsertHandler extends MessageHandler implements EventTarget
{
	private SskPubKey pubKey = null; 
	private int data; // The data being inserted
	
	public SskInsertHandler (SskInsert i, Node node,
				Peer prev, boolean needPubKey)
	{
		super (i, node, prev);
		data = i.data;
		if (!needPubKey) pubKey = new SskPubKey (id, key);
	}
	
	public void start()
	{
		if (pubKey == null) {
			// Wait 10 seconds for the previous hop to send the key
			Event.schedule (this, 10.0, KEY_TIMEOUT, null);
		}
		else {
			checkCollision();
			forwardSearch();
		}
	}
	
	// Check whether an older version of the data is already stored
	private void checkCollision()
	{
		Integer old = node.fetchSsk (key);
		if (old != null && old.intValue() != data) {
			if (LOG) node.log (this + " collided");
			if (prev == null) {
				if (LOG) node.log (this + " collided locally");
			}
			else prev.sendMessage (new SskDataFound (id, old));
			// Continue inserting the old data
			data = old;
			return;
		}
	}
	
	public void handleMessage (Message m, Peer src)
	{
		if (src == prev) {
			if (m instanceof SskPubKey)
				handleSskPubKey ((SskPubKey) m);
			else if (LOG) node.log ("unexpected type for " + m);
		}
		else if (src == next) {
			if (m instanceof SskAccepted)
				handleSskAccepted ((SskAccepted) m);
			else if (m instanceof RejectedLoop)
				handleRejectedLoop ((RejectedLoop) m);
			else if (m instanceof RejectedOverload)
				handleRejectedOverload ((RejectedOverload) m);
			else if (m instanceof RouteNotFound)
				handleRouteNotFound ((RouteNotFound) m);
			else if (m instanceof SskDataFound)
				handleCollision ((SskDataFound) m);
			else if (m instanceof InsertReply)
				handleInsertReply ((InsertReply) m);
			else if (LOG) node.log ("unexpected type for " + m);
		}
		else if (LOG) node.log ("unexpected source for " + m);
	}
	
	private void handleSskPubKey (SskPubKey pk)
	{
		if (searchState != STARTED && LOG)
			node.log (pk + " out of order");
		pubKey = pk;
		checkCollision();
		forwardSearch();
	}
	
	private void handleSskAccepted (SskAccepted sa)
	{
		if (searchState != SENT && LOG) node.log (sa + " out of order");
		searchState = ACCEPTED;
		next.successNotOverload(); // Reset the backoff length
		// Wait 60 seconds for a reply to the search
		Event.schedule (this, 60.0, SEARCH_TIMEOUT, next);
		// Send the public key if requested
		if (sa.needPubKey) next.sendMessage (pubKey);
	}
	
	private void handleCollision (SskDataFound sdf)
	{
		if (searchState != ACCEPTED && LOG)
			node.log (sdf + " out of order");
		if (prev == null) {
			if (LOG) node.log (this + " collided");
		}
		else prev.sendMessage (sdf); // Forward the message
		data = sdf.data; // Is this safe?
	}
	
	private void handleInsertReply (InsertReply ir)
	{
		if (searchState != ACCEPTED && LOG)
			node.log (ir + " out of order");
		if (prev == null) {
			if (LOG) node.log (this + " succeeded remotely");
			Node.succeededRemotely++;
			node.increaseSearchRate();
		}
		else prev.sendMessage (ir); // Forward the message
		finish();
	}
	
	protected void sendReply()
	{
		// We count this as a remote success because the insert has
		// run out of hops, so it must have left the node at some point
		if (prev == null) {
			if (LOG) node.log (this + " succeeded remotely");
			Node.succeededRemotely++;
			node.increaseSearchRate();
		}
		else prev.sendMessage (new InsertReply (id));
	}
	
	protected Search makeSearchMessage()
	{
		return new SskInsert (id, key, data, closest, htl);
	}
	
	protected void scheduleAcceptedTimeout (Peer next)
	{
		Event.schedule (this, 10.0, ACCEPTED_TIMEOUT, next);
	}
	
	protected void finish()
	{
		searchState = COMPLETED;
		node.cachePubKey (key);
		node.storePubKey (key);
		node.cacheSsk (key, data);
		node.storeSsk (key, data);
		node.removeMessageHandler (id);
	}
	
	public String toString()
	{
		return new String ("SSK insert (" +id+ "," +key+ "," +data+")");
	}
	
	// Event callback
	private void keyTimeout()
	{
		if (searchState != STARTED) return;
		if (LOG) node.log (this + " key timeout for " + prev);
		finish();
	}
	
	// EventTarget interface
	public void handleEvent (int code, Object data)
	{
		if (code == KEY_TIMEOUT)
			keyTimeout();
		else if (code == ACCEPTED_TIMEOUT)
			acceptedTimeout ((Peer) data);
		else if (code == SEARCH_TIMEOUT)
			searchTimeout ((Peer) data);
	}
	
	private final static int KEY_TIMEOUT = Event.code();
	private final static int ACCEPTED_TIMEOUT = Event.code();
	private final static int SEARCH_TIMEOUT = Event.code();
}
