package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer priority from waiting threads
	 *					to the owning thread.
	 * @return	a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum &&
				priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public void selfTest(){
		System.out.println("*** Entering PriorityScheduler Self Test  ***");

		final Lock lock = new Lock();

		KThread threads[] = new KThread[5];
		KThread threadTwo = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println(KThread.currentThread().getName() + " acquired lock");
				KThread.yield();
				lock.release();
				System.out.println(KThread.currentThread().getName() + " released lock");	

			}
		});
		boolean intStatus = Machine.interrupt().disable();
		setPriority(threadTwo,1);
		Machine.interrupt().restore(intStatus);
		threadTwo.setName("Thread Two").fork();
		KThread.yield();
		for (int i=0; i<5; i++) {
			threads[i] = new KThread(new Runnable() {
				public void run() {
					System.out.println(KThread.currentThread().getName() + " before lock acquired");
					lock.acquire();
					System.out.println(KThread.currentThread().getName() + " after acquired lock");	
					KThread.yield();
					lock.release();
					System.out.println(KThread.currentThread().getName() + " released lock");	

				}
			});
			intStatus = Machine.interrupt().disable();
			setPriority(threads[i],i+3);
			Machine.interrupt().restore(intStatus);
			threads[i].setName("Thread #" + i + " Priority: " + (i+3)).fork();
		}
		KThread.yield();
		System.out.println("***  Leaving PriorityScheduler Self Test  ***");
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;    

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param	thread	the thread whose scheduling state to return.
	 * @return	the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		private ThreadState owner = null;
		private LinkedList<KThread> waitQ = new LinkedList<KThread>();
		private boolean altered;
		private int effectivePriority;

		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			ThreadState state = getThreadState(thread);
			if(this.owner != null && this.transferPriority){
				this.owner.resources.remove(this);
			}
			this.owner = state;
			state.acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if(waitQ.isEmpty()){
				return null;
			}

			if(this.owner != null && this.transferPriority){
				this.owner.resources.remove(this);
			}

			KThread firstThread = pickNextThread();
			if(firstThread != null){
				waitQ.remove(firstThread);
				getThreadState(firstThread).acquire(this);
			}

			return firstThread;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected KThread pickNextThread() {
			KThread nextThread = null;
			for(Iterator<KThread> iter = waitQ.iterator(); iter.hasNext();){
				KThread thread = iter.next();
				int priority = getThreadState(thread).getEffectivePriority();
				if(nextThread == null || priority > getThreadState(nextThread).getEffectivePriority())
				{
					nextThread = thread;
				}
			}
			return nextThread;
		}

		public int getEffectivePriority(){

			if(!transferPriority){
				return priorityMinimum;
			}
			if(altered){
				effectivePriority = priorityMinimum;
				for(Iterator<KThread> iter = waitQ.iterator(); iter.hasNext();){
					KThread thread = iter.next();
					int priority = getThreadState(thread).getEffectivePriority();
					if(priority > effectivePriority){
						effectivePriority = priority;
					}
				}
				altered = false;
			}
			return effectivePriority;
		}

		public void setAltered(){
			if(!transferPriority){
				return;
			}
			altered = true;
			if(owner != null){
				owner.setAltered();
			}
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see	nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		protected int effectivePriority;
		protected LinkedList<ThreadQueue> resources = new LinkedList<ThreadQueue>();
		private boolean altered = false;
		protected ThreadQueue waitingQ;

		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			int effectivePriority = this.priority;
			if(altered){
				for(Iterator<ThreadQueue> iter = resources.iterator(); iter.hasNext();){
					PriorityQueue priorityQ = (PriorityQueue) (iter.next());
					int priority = priorityQ.getEffectivePriority();
					if(effectivePriority < priority){
						effectivePriority = priority;
					}
				}
			}
			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;

			setAltered();
		}

		public void setAltered(){
			if(altered){
				return;
			}
			altered = true;
			PriorityQueue priorityQ = (PriorityQueue) waitingQ;
			if(priorityQ != null){
				priorityQ.setAltered();
			}
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());
			Lib.assertTrue(waitQueue.waitQ.indexOf(thread) == -1);

			waitQueue.waitQ.add(thread);
			waitQueue.setAltered();
			waitingQ = waitQueue;
			if (resources.indexOf(waitQueue) != -1) {
				resources.remove(waitQueue);
				waitQueue.owner = null;
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());

			resources.add(waitQueue);
			if (waitQueue == waitingQ) {
				waitingQ = null;
			}
			setAltered();
		}	

		/** The thread with which this object is associated. */	   
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
	}
}
