// Limited-capacity LRU cache that stores a value for each key

import java.util.LinkedHashSet;
import java.util.HashMap;

class LruMap<Key,Value>
{
	public final int capacity;
	private LinkedHashSet<Key> set;
	private HashMap<Key,Value> map;
	
	public LruMap (int capacity)
	{
		this.capacity = capacity;
		set = new LinkedHashSet<Key> (capacity);
		map = new HashMap<Key,Value> (capacity);
	}
	
	public Value get (Key key)
	{
		log ("searching cache for key " + key);
		Value value = map.get (key);
		if (value != null) {
			// Move the key to the fresh end
			set.remove (key);
			set.add (key);
		}
		return value;
	}
	
	// Return the existing value (which is not replaced), or null
	public Value put (Key key, Value value)
	{
		Value existing = map.get (key);
		if (existing == null) {
			log ("adding key " + key + " to cache");
			if (set.size() == capacity) {
				// Discard the oldest element
				Key oldest = set.iterator().next();
				log ("discarding key " + oldest);
				set.remove (oldest);
			}
			map.put (key, value);
			return value;
		}
		else {
			log ("key " + key + " already in cache");
			// Move the key to the fresh end
			set.remove (key);
			set.add (key);
			return existing;
		}
	}
	
	private void log (String message)
	{
		// Event.log (message);
	}
}
