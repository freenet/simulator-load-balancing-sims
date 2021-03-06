// This software has been placed in the public domain by its author

package sim.messages;
import sim.clients.Client;

public class ChkInsert extends Search
{
	// Start a new insert
	public ChkInsert (int key, double location, Client client)
	{
		super (key, location, client);
	}
	
	// Forward an insert
	public ChkInsert (int id, int key, double closest, int htl)
	{
		super (id, key, closest, htl);
	}
	
	public String toString()
	{
		return new String ("CHK insert (" + id + "," + key + ")");
	}
}
