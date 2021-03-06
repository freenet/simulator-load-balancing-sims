// Limited-capacity LRU cache

import java.util.LinkedHashSet;

class LruCache<Key>
{
	public int capacity;
	public LinkedHashSet<Key> set;
	
	public LruCache (int capacity)
	{
		this.capacity = capacity;
		set = new LinkedHashSet<Key> (capacity);
	}
	
	public boolean get (Key key)
	{
		log ("searching cache for key " + key);
		if (set.remove (key)) {
			set.add (key); // Move the key to the fresh end
			return true;
		}
		return false;
	}
	
	public void put (Key key)
	{
		if (set.remove (key))
			log ("key " + key + " already in cache");
		else {
			log ("adding key " + key + " to cache");
			if (set.size() == capacity) {
				// Discard the oldest element
				Key oldest = set.iterator().next();
				log ("discarding key " + oldest);
				set.remove (oldest);
			}
		}
		set.add (key); // Add or move the key to the fresh end
	}
	
	private void log (String message)
	{
		Event.log (message);
	}
}
