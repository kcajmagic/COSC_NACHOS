package nachos.threads;

import java.util.Comparator;
import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	// WaitingQ Variable
	private PriorityQueue<KThread> waitingQ;
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p><b>Note</b>: Nachos will not function correctly with more than one
	 * alarm.
	 */


	public Alarm() {
		// 250 because thats the maximum possible number of threads
		waitingQ = new PriorityQueue<KThread>(250, new SortQueueViaPriority());

		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() { timerInterrupt(); }
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread
	 * that should be run.
	 */
	public void timerInterrupt() {
		// Quick Return
		if(waitingQ.isEmpty()){
			return;
		}
		// Get the current Time
		long currentTime = Machine.timer().getTime();

		while(!waitingQ.isEmpty()){
			if(waitingQ.element().alarmPriority <= currentTime){
				// Get the element with the highest priority
				KThread thread = waitingQ.element();
				// Remove it from waiting
				waitingQ.remove(thread);
				// Make it ready
				boolean intStatus = Machine.interrupt().disable();
				thread.ready();
				Machine.interrupt().restore(intStatus);
			}else{
				return;
			}
		}
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks,
	 * waking it up in the timer interrupt handler. The thread must be
	 * woken up (placed in the scheduler ready set) during the first timer
	 * interrupt where
	 *
	 * <p><blockquote>
	 * (current time) >= (WaitUntil called time)+(x)
	 * </blockquote>
	 *
	 * @param	x	the minimum number of clock ticks to wait.
	 *
	 * @see	nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// Disable Interrupts
		boolean intStatus = Machine.interrupt().disable();

		long priortyTime = Machine.timer().getTime() + x;
		KThread.currentThread().alarmPriority = priortyTime;
		waitingQ.add(KThread.currentThread());
		KThread.sleep();

		//Enable Interrupts
		Machine.interrupt().restore(intStatus);
	}


	public static void selfTest() {
		System.out.println("***  Enter Alarm Self Test  ***");	

		Runnable rShort = new Runnable() {
			public void run() {
				System.out.println(KThread.currentThread().getName() + " alarm Time: " + Machine.timer().getTime());
				ThreadedKernel.alarm.waitUntil(10);
				System.out.println(KThread.currentThread().getName() + " woken up Time: " + Machine.timer().getTime() + " Should wait: "+ (10));	

			}
		};

		Runnable rLong = new Runnable() {
			public void run() {
				System.out.println(KThread.currentThread().getName() + " alarm Time: " + Machine.timer().getTime());
				ThreadedKernel.alarm.waitUntil(200);
				System.out.println(KThread.currentThread().getName() + " woken up Time: " + Machine.timer().getTime() + " Should wait: "+ (200));	

			}
		};

		KThread threads[] = new KThread[10];
		for (int i=0; i<10; i++) {
			if(i%2==0){
				threads[i] = new KThread(rLong);
			}else{
				threads[i] = new KThread(rShort);
			}
			threads[i].setName("Thread #" + i).fork();
		}

		KThread.yield();
		for(int i = 0; i < 1000; i++){
			ThreadedKernel.alarm.timerInterrupt();
			KThread.yield();
		}
		System.out.println("***  Leave Alarm Self Test  ***");	
	}
}

class SortQueueViaPriority implements Comparator<KThread> {
	// For Sorting the Priority Queue
	public int compare(KThread thread1, KThread thread2) {
		if (thread1.alarmPriority == thread2.alarmPriority) return 0;
		return thread1.alarmPriority > thread2.alarmPriority ? 1 : -1;
	}


}
