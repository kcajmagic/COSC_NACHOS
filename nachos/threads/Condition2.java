package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
	// New variable waitQ
	private LinkedList<KThread> waitQ;

	/**
	 * Allocate a new condition variable.
	 *
	 * @param	conditionLock	the lock associated with this condition
	 *				variable. The current thread must hold this
	 *				lock whenever it uses <tt>sleep()</tt>,
	 *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		waitQ = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The
	 * current thread must hold the associated lock. The thread will
	 * automatically reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// Disable Interupts
		boolean intStatus = Machine.interrupt().disable();
		
		conditionLock.release();
		
		// Important part
		waitQ.add(KThread.currentThread());
		KThread.sleep();
		
		conditionLock.acquire();
		
		// Reenable Interrupts
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		// Disable Interupts
		boolean intStatus = Machine.interrupt().disable();
		
		//Wake thread
		((KThread) waitQ.removeFirst()).ready();
		
		//Enable Interupts
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		//Wake all Threads
		while(!waitQ.isEmpty()){
			wake();
		}
	}
	
	public static void selfTest() {
		System.out.println("***  Enter Condition2 Self  Test  ***");	

		final Lock lock = new Lock();
		final Condition2 condition = new Condition2(lock); 

		KThread threads[] = new KThread[10];
		for (int i=0; i<10; i++) {
			threads[i] = new KThread(new Runnable() {
				
				public void run() {
					lock.acquire();

			        System.out.print(KThread.currentThread().getName() + " acquired lock\n");	
			        condition.sleep();
		
			        System.out.print(KThread.currentThread().getName() + " acquired lock again\n");	

			        lock.release();
			        System.out.print(KThread.currentThread().getName() + " released lock \n");	
				}
			});
			threads[i].setName("Thread #" + i).fork();
		}
		System.out.println("Starting Threads");
		KThread.yield();

		lock.acquire();

		System.out.println("Called condition.wake();");	
		condition.wake();
		lock.release();
		KThread.yield();
		lock.acquire();
	
		System.out.println("Called condition.wakeAll();");	
		condition.wakeAll();

		lock.release();
		KThread.yield();

		System.out.println("***  Leave Condition2 Self Test ***");	
	}

	private Lock conditionLock;
}