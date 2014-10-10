package nachos.threads;

import java.util.ArrayList;
import java.util.Collections;

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
		while(!isWordReady){
			speakerCond.wakeAll();
			listenerCond.sleep();
		}
		int word = this.word;
		isWordReady = false;
		listener--;
		
		lock.release();

		return word;
	}


	private static class Speaker implements Runnable {
		Speaker(Communicator comm, int word) {
			this.comm = comm; 
			this.word = word;
		}

		public void run() {
			System.out.println(KThread.currentThread().getName() + " will speak ");	
			comm.speak(this.word);
			System.out.println(KThread.currentThread().getName() + " spoke word " + this.word);
		}

		private int word = 0;
		private Communicator comm; 
	}

	private static class Listener implements Runnable {
		Listener(Communicator comm) {
			this.comm = comm; 
		}

		public void run() {
			System.out.println(KThread.currentThread().getName() + " will listen ");	

			int word = comm.listen();

			System.out.println(KThread.currentThread().getName()  + " Listened to word: " + word + " "); 
		}

		private Communicator comm; 
	}

	public static void selfTest() {
		System.out.println("***  Enter Communicator Self Test  ***");	

		System.out.println("** TEST 1: Test for one speaker, one listener, speaker waits for listener");	
		Communicator comm1 = new Communicator();
		KThread threadSpeaker1 =  new KThread(new Speaker(comm1, 100));
		threadSpeaker1.setName("Thread Speaker").fork();

		KThread.yield();

		KThread threadListener1 = new KThread(new Listener(comm1));
		threadListener1.setName("Thread Listener").fork();

		KThread.yield();

		threadListener1.join();
		threadSpeaker1.join();


		System.out.println("** TEST 2: Test for one speaker, one listener, listener waits for speaker");	
		Communicator comm2 = new Communicator();

		KThread threadListener2 = new KThread(new Listener(comm2));
		threadListener2.setName("Thread Listener").fork();

		KThread.yield();

		KThread threadSpeaker2 =  new KThread(new Speaker(comm2, 100));
		threadSpeaker2.setName("Thread Speaker").fork();

		KThread.yield();

		threadListener2.join();
		threadSpeaker2.join();

		
		System.out.println("** TEST 3:  Test for more speakers, more listeners, listeners waits for speakers");	
		Communicator comm3 = new Communicator();

		KThread threadsSpeaker3[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			threadsSpeaker3[i] = new KThread(new Speaker(comm3, (i+1)*100));
			threadsSpeaker3[i].setName("Speaker #" + i).fork();
		}

		KThread.yield();

		KThread threadsListener3[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			threadsListener3[i] = new KThread(new Listener(comm3));
			threadsListener3[i].setName("Listener #" + i).fork();
		}

		KThread.yield();

		for (int i = 0; i < 10; i++) {
			threadsListener3[i].join();
		}

		
		System.out.println("** TEST 4:  Test for more speakers, more listeners, speaker waits for listeners");	
		Communicator comm4 = new Communicator();

		int num  = 10;
		ArrayList<KThread> threads4 = new ArrayList<KThread>();

		for (int i = 0; i < num; i++) {
			KThread tmp = new KThread(new Speaker(comm4, (i+1)*10));
			tmp.setName("Speaker #" + i);
			threads4.add(tmp);
		}

		for (int i = 0; i < num; i++) {
			KThread tmp = new KThread(new Listener(comm4));
			tmp.setName("Listener #" + i);
			threads4.add(tmp);
		}

		Collections.shuffle(threads4);

		for (int i = 0; i < num * 2; i++) {
			threads4.get(i).fork();
		}

		KThread.yield();

		for (int i = 0; i < num * 2; i++) {
			threads4.get(i).join();
		}
		System.out.println("***  Leave Communicator Self Test  ***");	
	}
}