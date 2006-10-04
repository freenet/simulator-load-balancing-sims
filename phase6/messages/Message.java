// A high-level message (as opposed to a low-level packet)

package messages;

public class Message
{
	public final static int HEADER_SIZE = 12; // Bytes, including unique ID
	public final static int KEY_SIZE = 32; // Size of a routing key, bytes
	public final static int PUB_KEY_SIZE = 1024; // Size of a pub key, bytes
	public final static int DATA_SIZE = 1024; // Size of a data block, bytes
	
	public static int nextId = 0; // Each request and insert has a unique ID
	
	public int size; // Size in bytes
	public int id; // Unique request ID
}
