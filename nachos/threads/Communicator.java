package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
	private int listener = 0;
	private int speaker = 0;
	private int word = 0;
	private boolean isWordReady;


	private Lock lock;
	private Condition2 speakerCond;
	private Condition2 listenerCond;

	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		this.isWordReady = false;
		this.lock = new Lock();

		this.speakerCond = new Condition2(lock);
		this.listenerCond = new Condition2(lock);
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param	word	the integer to transfer.
	 */
	public void speak(int word) {
		lock.acquire();

		speaker++;
		while (isWordReady || listener == 0) {
			speakerCond.sleep();
		}
		this.word = word;
		this.isWordReady = true;

		listenerCond.wakeAll();

		speaker--;

		lock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return
	 * the <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return	the integer transferred.
	 */    
	public int listen() {
		lock.acquire();

		listener++;

		while(isWordReady == false){
			speakerCond.wakeAll();
			listenerCond.sleep();
		}
		int word = this.word;

		isWordReady = false;

		listener--;

		lock.release();

		return word;
	}
}