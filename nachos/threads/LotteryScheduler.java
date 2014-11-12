package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;

import java.awt.datatransfer.Transferable;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}
	
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 1;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE; 

	/**
	 * Allocate a new lottery thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer tickets from waiting threads
	 *					to the owning thread.
	 * @return	a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryPriorityQueue(transferPriority);
	}

	protected class LotteryPriorityQueue extends PriorityQueue{

		LotteryPriorityQueue(boolean transferPriority) {
			super(transferPriority);
			// TODO Auto-generated constructor stub
		}
		

		protected KThread pickNextThread() {
			int tickets[] = new int[250];
			KThread threads[] = new KThread[250];
			int count = 0;
			int sum = 0;
			for(Iterator<KThread> iter = waitQ.iterator(); iter.hasNext();count++){
				KThread thread = iter.next();
				tickets[count] = getThreadState(thread).getEffectivePriority();
				sum += getThreadState(thread).getEffectivePriority();
				threads[count] = thread;
			}
			int r = (int)(Math.random()*sum)+1;
			for(int i=0; i < tickets.length; i++){
				if(r <= tickets[i]){
					return threads[i];
				} else {
					r -= tickets[i];
				}
			}
			return null; // TO Fix error of not returning anything
		}

		public int getEffectivePriority() {
			if(transferPriority == false){
				return priorityMinimum;
			}
			if(altered){
				effectivePriority = priorityMinimum;
				for(Iterator<KThread> iter = waitQ.iterator(); iter.hasNext();){
					KThread thread = iter.next();
					effectivePriority += getThreadState(thread).getEffectivePriority();
				}
				altered = false;
			}
			return effectivePriority;
		}

	}

	protected class LotteryState extends ThreadState{

		public LotteryState(KThread thread) {
			super(thread);
			// TODO Auto-generated constructor stub
		}
		
		public int getEffectivePriority() {
			// TODO: Update this method
			int effectivePriority = this.priority;
			if(altered){
				for(Iterator<ThreadQueue> iter = resources.iterator(); iter.hasNext();){
					LotteryPriorityQueue priorityQ = (LotteryPriorityQueue) (iter.next());
					effectivePriority += priorityQ.getEffectivePriority();
				}
			}
			return effectivePriority;
		}

	}
}
