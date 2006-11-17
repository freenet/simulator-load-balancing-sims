// The state of a CHK insert as stored at each node along the path

package sim.handlers;
import sim.*;
import sim.messages.*;
import java.util.HashSet;

public class ChkInsertHandler extends MessageHandler implements EventTarget
{
	private int inState = STARTED; // State of incoming transfer
	private HashSet<Peer> receivers; // Peers that should receive data
	private Block[] blocks; // Store incoming blocks for forwarding
	private int blocksReceived = 0;
	
	public ChkInsertHandler (ChkInsert i, Node node, Peer prev)
	{
		super (i, node, prev);
		receivers = new HashSet<Peer>();
		blocks = new Block[32];
	}
	
	public void start()
	{
		// Wait 10 seconds for the incoming transfer to start
		Event.schedule (this, 10.0, DATA_TIMEOUT, null);
	}
	
	public void handleMessage (Message m, Peer src)
	{
		if (m instanceof RejectedOverload)
			handleOverload ((RejectedOverload) m, src);
		else if (src == prev) {
			if (m instanceof DataInsert)
				handleDataInsert ((DataInsert) m);
			else if (m instanceof Block)
				handleBlock ((Block) m);
			else node.log ("unexpected type for " + m);
		}
		else if (src == next) {
			if (m instanceof Accepted)
				handleAccepted ((Accepted) m);
			else if (m instanceof RejectedLoop)
				handleRejectedLoop ((RejectedLoop) m);
			else if (m instanceof RouteNotFound)
				handleRouteNotFound ((RouteNotFound) m);
			else if (m instanceof InsertReply)
				handleInsertReply ((InsertReply) m);
			else if (m instanceof TransfersCompleted)
				handleCompleted ((TransfersCompleted) m, src);
			else node.log ("unexpected type for " + m);
		}
		else if (receivers.contains (src)) {
			if (m instanceof TransfersCompleted)
				handleCompleted ((TransfersCompleted) m, src);
			else node.log ("unexpected type for " + m);
		}
		else node.log ("unexpected source for " + m);
	}
	
	private void handleDataInsert (DataInsert di)
	{
		if (inState != STARTED) node.log (di + " out of order");
		inState = TRANSFERRING;
		// Start the search
		forwardSearch();
		// If we have all the blocks and the headers, consider finishing
		if (blocksReceived == 32) {
			inState = COMPLETED;
			considerFinishing();
		}
		// Wait for transfer to complete (FIXME: check real timeout)
		else Event.schedule (this, 120.0, TRANSFER_IN_TIMEOUT, null);
	}
	
	private void handleBlock (Block b)
	{
		if (inState != TRANSFERRING) node.log (b + " out of order");
		if (blocks[b.index] != null) return; // Ignore duplicates
		blocks[b.index] = b;
		blocksReceived++;
		// Forward the block to all receivers
		for (Peer p : receivers) p.sendMessage (b);
		// If we have all the blocks and the headers, consider finishing
		if (blocksReceived == 32 && inState == TRANSFERRING) {
			inState = COMPLETED;
			considerFinishing();
		}
	}
	
	private void handleCompleted (TransfersCompleted tc, Peer src)
	{
		receivers.remove (src);
		considerFinishing();
	}
	
	private void handleAccepted (Accepted a)
	{
		if (searchState != SENT) node.log (a + " out of order");
		searchState = ACCEPTED;
		next.successNotOverload(); // Reset the backoff length
		// Wait 120 seconds for a reply to the search
		Event.schedule (this, 120.0, SEARCH_TIMEOUT, next);
		// Add the next hop to the list of receivers
		receivers.add (next);
		next.sendMessage (new DataInsert (id));
		// Send all previously received blocks
		for (int i = 0; i < 32; i++)
			if (blocks[i] != null) next.sendMessage (blocks[i]);
		// Wait for TransfersCompleted (FIXME: check real timeout)
		Event.schedule (this, 240.0, TRANSFER_OUT_TIMEOUT, next);
	}
	
	private void handleInsertReply (InsertReply ir)
	{
		if (searchState != ACCEPTED) node.log (ir + " out of order");
		if (prev == null) node.searchSucceeded (this);
		else prev.sendMessage (ir); // Forward the message
		finish();
	}

	protected void sendReply()
	{
		if (prev == null) node.searchSucceeded (this);
		else prev.sendMessage (new InsertReply (id));
	}
	
	protected Search makeSearchMessage()
	{
		return new ChkInsert (id, key, closest, htl);
	}
	
	protected void scheduleAcceptedTimeout (Peer next)
	{
		Event.schedule (this, 10.0, ACCEPTED_TIMEOUT, next);
	}
	
	protected void finish()
	{
		// Don't really finish until the incoming transfer
		// and all outgoing transfers are complete
		searchState = COMPLETED;
		if (inState == COMPLETED && receivers.isEmpty()) reallyFinish();
	}
	
	private void reallyFinish()
	{
		inState = COMPLETED;
		searchState = COMPLETED;
		node.cacheChk (key);
		node.storeChk (key);
		if (prev == null) node.log (this + " completed");
		else prev.sendMessage (new TransfersCompleted (id));
		node.removeMessageHandler (id);
	}
	
	private void considerFinishing()
	{
		if (inState == COMPLETED && searchState == COMPLETED
		&& receivers.isEmpty()) reallyFinish();
	}
	
	public String toString()
	{
		return new String ("CHK insert (" + id + "," + key + ")");
	}
	
	// Event callback
	private void dataTimeout()
	{
		if (inState != STARTED) return;
		node.log (this + " data timeout from " + prev);
		if (prev == null) node.log (this + " failed"); // Don't throttle
		else prev.sendMessage (new TransfersCompleted (id));
		reallyFinish();
	}
	
	// Event callback
	private void transferInTimeout()
	{
		if (inState != TRANSFERRING) return;
		node.log (this + " transfer timeout from " + prev);
		if (prev == null) node.log (this + " failed"); // Don't throttle
		else prev.sendMessage (new TransfersCompleted (id));
		reallyFinish();
	}
	
	// Event callback
	private void transferOutTimeout (Peer p)
	{
		if (!receivers.remove (p)) return;
		node.log (this + " transfer timeout to " + p);
		// FIXME: should we back off?
		considerFinishing();
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		switch (type) {
			case ACCEPTED_TIMEOUT:
			acceptedTimeout ((Peer) data);
			break;
			
			case SEARCH_TIMEOUT:
			searchTimeout ((Peer) data);
			break;
			
			case DATA_TIMEOUT:
			dataTimeout();
			break;
			
			case TRANSFER_IN_TIMEOUT:
			transferInTimeout();
			break;
			
			case TRANSFER_OUT_TIMEOUT:
			transferOutTimeout ((Peer) data);
			break;
		}
	}
	
	private final static int ACCEPTED_TIMEOUT = 1;
	private final static int SEARCH_TIMEOUT = 2;
	private final static int DATA_TIMEOUT = 3;
	private final static int TRANSFER_IN_TIMEOUT = 4;
	private final static int TRANSFER_OUT_TIMEOUT = 5;
}
