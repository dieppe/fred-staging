package freenet.client.async;

import freenet.crypt.RandomSource;
import freenet.keys.ClientKeyBlock;
import freenet.keys.KeyVerifyException;
import freenet.node.LowLevelGetException;
import freenet.node.Node;
import freenet.node.RequestStarter;
import freenet.support.Logger;
import freenet.support.RandomGrabArrayWithInt;
import freenet.support.SortedVectorByNumber;

/**
 * Every X seconds, the RequestSender calls the ClientRequestScheduler to
 * ask for a request to start. A request is then started, in its own 
 * thread. It is removed at that point.
 */
public class ClientRequestScheduler implements RequestScheduler {

	/**
	 * Structure:
	 * array (by priority) -> // one element per possible priority
	 * SortedVectorByNumber (by # retries) -> // contains each current #retries
	 * RandomGrabArray // contains each element, allows fast fetch-and-drop-a-random-element
	 * 
	 * To speed up fetching, a RGA or SVBN must only exist if it is non-empty.
	 */
	final SortedVectorByNumber[] priorities;
	// we have one for inserts and one for requests
	final boolean isInsertScheduler;
	final RandomSource random;
	private final RequestStarter starter;
	private final Node node;
	
	public ClientRequestScheduler(boolean forInserts, RandomSource random, RequestStarter starter, Node node) {
		this.starter = starter;
		this.random = random;
		this.node = node;
		this.isInsertScheduler = forInserts;
		priorities = new SortedVectorByNumber[RequestStarter.NUMBER_OF_PRIORITY_CLASSES];
	}
	
	public void register(SendableRequest req) {
		Logger.minor(this, "Registering "+req, new Exception("debug"));
		if((!isInsertScheduler) && req instanceof ClientPutter)
			throw new IllegalArgumentException("Expected a ClientPut: "+req);
		if(req instanceof SendableGet) {
			SendableGet getter = (SendableGet)req;
			ClientKeyBlock block;
			try {
				block = node.fetchKey(getter.getKey());
			} catch (KeyVerifyException e) {
				// Verify exception, probably bogus at source;
				// verifies at low-level, but not at decode.
				getter.onFailure(new LowLevelGetException(LowLevelGetException.DECODE_FAILED));
				return;
			}
			if(block != null) {
				Logger.minor(this, "Can fulfill immediately from store");
				getter.onSuccess(block, true);
				return;
			}
		}
		synchronized(this) {
			RandomGrabArrayWithInt grabber = 
				makeGrabArray(req.getPriorityClass(), req.getRetryCount());
			grabber.add(req);
			Logger.minor(this, "Registered "+req+" on prioclass="+req.getPriorityClass()+", retrycount="+req.getRetryCount());
		}
		synchronized(starter) {
			starter.notifyAll();
		}
	}
	
	private synchronized RandomGrabArrayWithInt makeGrabArray(short priorityClass, int retryCount) {
		SortedVectorByNumber prio = priorities[priorityClass];
		if(prio == null) {
			prio = new SortedVectorByNumber();
			priorities[priorityClass] = prio;
		}
		RandomGrabArrayWithInt grabber = (RandomGrabArrayWithInt) prio.get(retryCount);
		if(grabber == null) {
			grabber = new RandomGrabArrayWithInt(random, retryCount);
			prio.add(grabber);
			Logger.minor(this, "Registering retry count "+retryCount+" with prioclass "+priorityClass);
		}
		return grabber;
	}

	/**
	 * Should not be called often as can be slow if there are many requests of the same
	 * priority and retry count. Priority and retry count must be the same as they were
	 * when it was added.
	 */
	public synchronized void remove(SendableRequest req) {
		Logger.minor(this, "Removing "+req);
		// Should not be called often.
		int prio = req.getPriorityClass();
		int retryCount = req.getRetryCount();
		SortedVectorByNumber s = priorities[prio];
		if(s == null) return;
		if(s.isEmpty()) return;
		RandomGrabArrayWithInt grabber = 
			(RandomGrabArrayWithInt) s.get(retryCount);
		if(grabber == null) return;
		grabber.remove(req);
		if(grabber.isEmpty()) {
			s.remove(retryCount);
			if(s.isEmpty())
				priorities[prio] = null;
		}
	}
	
	public synchronized SendableRequest removeFirst() {
		// Priorities start at 0
		Logger.minor(this, "removeFirst()");
		for(int i=0;i<RequestStarter.MINIMUM_PRIORITY_CLASS;i++) {
			SortedVectorByNumber s = priorities[i];
			if(s == null) {
				Logger.minor(this, "Priority "+i+" is null");
				continue;
			}
			while(true) {
				RandomGrabArrayWithInt rga = (RandomGrabArrayWithInt) s.getFirst(); // will discard finished items
				if(rga == null) {
					Logger.minor(this, "No retrycount's in priority "+i);
					priorities[i] = null;
					break;
				}
				SendableRequest req = (SendableRequest) rga.removeRandom();
				if(rga.isEmpty()) {
					Logger.minor(this, "Removing retrycount "+rga.getNumber());
					s.remove(rga.getNumber());
					if(s.isEmpty()) {
						Logger.minor(this, "Removing priority "+i);
						priorities[i] = null;
					}
				}
				if(req == null) {
					Logger.minor(this, "No requests in priority "+i+", retrycount "+rga.getNumber()+" ("+rga+")");
					continue;
				}
				Logger.minor(this, "removeFirst() returning "+req+" ("+rga.getNumber()+")");
				return req;
			}
		}
		Logger.minor(this, "No requests to run");
		return null;
	}
}
