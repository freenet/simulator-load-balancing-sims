class Sim
{
	private final int NODES = 100; // Number of nodes
	private final int DEGREE = 4; // Average degree
	private final double SPEED = 20000; // Bytes per second
	private final double LATENCY = 0.1; // Seconds
	private Node[] nodes;
	
	public Sim()
	{
		Network.reorder = true;
		Network.lossRate = 0.001;
		
		// Create the nodes
		nodes = new Node[NODES];
		for (int i = 0; i < NODES; i++)
			nodes[i] = new Node (1.0 / NODES * i, SPEED, SPEED);
		// Connect the nodes
		makeKleinbergNetwork();
		
		int key = Node.locationToKey (Math.random());
		Event.schedule (nodes[0], 0.0,
			Node.GENERATE_SSK_INSERT, key);
		Event.schedule (nodes[NODES/4], 30.0,
			Node.GENERATE_SSK_REQUEST, key);
		Event.schedule (nodes[NODES/2], 60.0,
			Node.GENERATE_SSK_COLLISION, key);
		Event.schedule (nodes[3*NODES/4], 90.0,
			Node.GENERATE_SSK_REQUEST, key);
		
		// Run the simulation
		Event.run();
	}
	
	// Return the lattice distance between a and b
	private int latticeDistance (int a, int b)
	{
		if (a > b) return Math.min (a - b, b - a + NODES);
		else return Math.min (b - a, a - b + NODES);
	}
	
	private void makeKleinbergNetwork()
	{
		// Add exactly DEGREE/2 outgoing edges to each node
		for (int i = 0; i < NODES; i++) {
			// Calculate the normalising constant
			double c = 0.0;
			for (int j = 0; j < NODES; j++) {
				if (i == j) continue;
				c += 1.0 / latticeDistance (i, j);
			}
			// Add the outgoing edges
			int deg = 0;
			for (int j = 0; deg < DEGREE/2; j = (j+1) % NODES) {
				if (i == j) continue;
				double prob = 1.0 / latticeDistance (i, j) / c;
				if (Math.random() < prob)
					if (nodes[i].connectBothWays
					(nodes[j], LATENCY)) deg++;
			}
		}
	}
	
	public static void main (String[] args)
	{
		new Sim();
	}
}
