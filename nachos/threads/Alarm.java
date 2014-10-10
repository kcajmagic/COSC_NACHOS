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
		// 11 because its the default value of regular Priority Queues
		waitingQ = new PriorityQueue<KThread>(11, new SortQueueViaPriority());

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
				thread.ready();
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

		System.out.print("Enter Alarm.selfTest\n");	

		Runnable r = new Runnable() {
			public void run() {
				KThread t[] = new KThread[10];

				for (int i=0; i<10; i++) {
					final int valueR = i;
					t[i] = new KThread(new Runnable() {
						
						public void run() {
							System.out.print(KThread.currentThread().getName() + " alarm\n");
							
					        ThreadedKernel.alarm.waitUntil(160+valueR*20);
					        System.out.print(KThread.currentThread().getName() + " woken up \n");	
							
						}
					});
					t[i].setName("Thread" + i).fork();
				}
				for (int i=0; i<10000; i++) {
					KThread.yield();
				}
			}
		};

		KThread t = new KThread(r);
		t.setName("Alarm SelfTest");
		t.fork();
		KThread.yield();

		t.join();

		System.out.print("Leave Alarm.selfTest\n");	

	}
}

class SortQueueViaPriority implements Comparator<KThread> {
	// For Sorting the Priority Queue
	public int compare(KThread thread1, KThread thread2) {
		if (thread1.alarmPriority == thread2.alarmPriority) return 0;
		return thread1.alarmPriority > thread2.alarmPriority ? 1 : -1;
	}


}
