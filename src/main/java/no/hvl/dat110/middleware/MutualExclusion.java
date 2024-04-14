/**
 * 
 */
package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class MutualExclusion {
	
	private static final Logger logger = LogManager.getLogger(MutualExclusion.class);
	/** lock variables */
	private boolean CS_BUSY = false;						// indicate to be in critical section (accessing a shared resource) 
	private boolean WANTS_TO_ENTER_CS = false;				// indicate to want to enter CS
	private List<Message> queueack; 						// queue for acknowledged messages
	private List<Message> mutexqueue;						// queue for storing process that are denied permission. We really don't need this for quorum-protocol
	
	private LamportClock clock;								// lamport clock
	private Node node;
	
	public MutualExclusion(Node node) {
		this.node = node;
		
		clock = new LamportClock();
		queueack = new ArrayList<Message>();
		mutexqueue = new ArrayList<Message>();
	}
	
	public synchronized void acquireLock() {
		CS_BUSY = true;
	}
	
	public void releaseLocks() {
		WANTS_TO_ENTER_CS = false;
		CS_BUSY = false;
	}

	public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {
		
		logger.info(node.nodename + " wants to access CS");
		// clear the queueack before requesting for votes

		queueack.clear();
		
		// clear the mutexqueue

		mutexqueue.clear();

		// increment clock
		clock.increment();
		
		// adjust the clock on the message, by calling the setClock on the message

		message.setClock(clock.getClock());
				
		// wants to access resource - set the appropriate lock variable

		WANTS_TO_ENTER_CS = true;
	
		
		// start MutualExclusion algorithm
		
		// first, call removeDuplicatePeersBeforeVoting. A peer can hold/contain 2 replicas of a file. This peer will appear twice

		List<Message> activeNodes = removeDuplicatePeersBeforeVoting();

		// multicast the message to activenodes (hint: use multicastMessage)

		multicastMessage(message, activeNodes);
		
		// check that all replicas have replied (permission)
		if (areAllMessagesReturned(Util.numReplicas)) {


			// if yes, acquireLock
			acquireLock();

			// node.broadcastUpdatetoPeers

			node.broadcastUpdatetoPeers(updates);

			// clear the mutexqueue
			mutexqueue.clear();

			// return permission

			return true;
		}

		return false;

	}
	
	// multicast message to other processes including self
	private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {
		
		logger.info("Number of peers to vote = "+activenodes.size());

		// iterate over the activenodes

		for (Message activenode: activenodes) {

			// obtain a stub for each node from the registry
			NodeInterface stub = Util.getProcessStub(activenode.getNodeName(), activenode.getPort());


			// call onMutexRequestReceived()
			stub.onMutexRequestReceived(message);

		}
	}
	
	public void onMutexRequestReceived(Message message) throws RemoteException {
		
		// increment the local clock

		clock.increment();
		
		// if message is from self, acknowledge, and call onMutexAcknowledgementReceived()
		int caseid = -1;
		if (message.getNodeID().compareTo(this.node.getNodeID()) == 0){
			message.setAcknowledged(true);
			onMutexAcknowledgementReceived(message);
		}

		/* write if statement to transition to the correct caseid */

		// caseid=0: Receiver is not accessing shared resource and does not want to (send OK to sender)
		if(!CS_BUSY && !WANTS_TO_ENTER_CS){

			caseid = 0;
		}
		// caseid=1: Receiver already has access to the resource (dont reply but queue the request)
		else if(CS_BUSY){
			caseid = 1;
		}
		// caseid=2: Receiver wants to access resource but is yet to - compare own message clock to received message's clock
//WANTS_TO_ENTER_CS && !CS_BUSY
		//WANTS_TO_ENTER_CS && message.getClock() < clock.getClock()
		/*if(WANTS_TO_ENTER_CS && !CS_BUSY) {
			caseid = 2;
		} */

		else {
			caseid = 2;
		}
		// check for decision
		doDecisionAlgorithm(message, mutexqueue, caseid);
	}
	
	public void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {
		
		String procName = message.getNodeName();
		int port = message.getPort();

		NodeInterface stub = null;

		switch(condition) {
		
			/** case 1: Receiver is not accessing shared resource and does not want to (send OK to sender) */
			case 0: {
				
				// get a stub for the sender from the registry

				stub = Util.getProcessStub(procName, port);

				// acknowledge message

				message.setAcknowledged(true);
				
				// send acknowledgement back by calling onMutexAcknowledgementReceived()

				stub.onMutexAcknowledgementReceived(message);
				
				break;
			}
		
			/** case 2: Receiver already has access to the resource (dont reply but queue the request) */
			case 1: {
				
				// queue this message
				//mutexqueue.add(message);
				queue.add(message);
				break;
			}
			
			/**
			 *  case 3: Receiver wants to access resource but is yet to (compare own message clock to received message's clock
			 *  the message with lower timestamp wins) - send OK if received is lower. Queue message if received is higher
			 */
			case 2: {
				
				// check the clock of the sending process (note that the correct clock is in the message)
				int sendingProcessClock =  message.getClock();
				
				// own clock for the multicast message (note that the correct clock is in the message)

				int ownClock =this.node.getMessage().getClock();

				// compare clocks, the lowest wins
				// if sender wins, acknowledge the message, obtain a stub and call onMutexAcknowledgementReceived()
				// if sender looses, queue it

				if(sendingProcessClock < ownClock ){
					message.setAcknowledged(true);
					stub = Util.getProcessStub(procName, port);
					stub.onMutexAcknowledgementReceived(message);
				}

				else if (ownClock == sendingProcessClock){

					// if clocks are the same, compare nodeIDs, the lowest wins
					if (message.getNodeID().compareTo(this.node.getNodeID()) < 0 ){
						message.setAcknowledged(true);
						stub = Util.getProcessStub(procName, port);
						stub.onMutexAcknowledgementReceived(message);
					} else{

						//mutexqueue.add(message);
						queue.add(message);
					}
				} else{
					//mutexqueue.add(message);
					queue.add(message);
				}


				break;
			}
			
			default: break;
		}
		
	}
	
	public void onMutexAcknowledgementReceived(Message message) throws RemoteException {
		
		// add message to queueack
		queueack.add(message);
		
	}
	
	// multicast release locks message to other processes including self
	public void multicastReleaseLocks(Set<Message> activenodes) throws RemoteException {
		logger.info("Releasing locks from = "+activenodes.size());


		// iterate over the activenodes
		for (Message activenode: activenodes) {

			// obtain a stub for each node from the registry
			NodeInterface stub = Util.getProcessStub(this.node.getNodeName(), this.node.getPort());

			// call releaseLocks()

			stub.releaseLocks();

		}
	}
	
	private boolean areAllMessagesReturned(int numvoters) throws RemoteException {
		logger.info(node.getNodeName()+": size of queueack = "+queueack.size());
		
		// check if the size of the queueack is same as the numvoters
		if (queueack.size() == numvoters) {

			// clear the queueack
			queueack.clear();

			// return true if yes and false if no
		return  true;
		} else {

			return false;
		}
	}
	
	private List<Message> removeDuplicatePeersBeforeVoting() {
		
		List<Message> uniquepeer = new ArrayList<Message>();
		for(Message p : node.activenodesforfile) {
			boolean found = false;
			for(Message p1 : uniquepeer) {
				if(p.getNodeName().equals(p1.getNodeName())) {
					found = true;
					break;
				}
			}
			if(!found)
				uniquepeer.add(p);
		}		
		return uniquepeer;
	}
}
