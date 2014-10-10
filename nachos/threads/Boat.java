package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Machine;

public class Boat
{
	static BoatGrader bg;
	static int awakeKidsOahu;
	static int kidsOnOahu;
	static int adultsOnOahu;
	static Condition2 sleepingKidsOahu;
	static Condition2 sleepingKidsMolokai;
	static Condition2 sleepingAdultsOahu;
	static Lock boatLock;
	static boolean done;

	public static void selfTest()
	{
		BoatGrader b = new BoatGrader();

//		// Expected Results: YAY
//		System.out.println("\n ***Testing Boats with only 2 children***");
//		begin(0, 2, b);
//
//		// Expected Results: YAY
//		System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
//		begin(1, 2, b);
//
//		// Expected Results: YAY
//		System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
//		begin(3, 3, b);
//				
		// Expected Results: YAY
		System.out.println("\n ***Testing Boats with 50 children, 150 adults***");
		begin(150, 50, b);
	}

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		done = false;
		boatLock  = new Lock();
		sleepingAdultsOahu = new Condition2(boatLock);
		sleepingKidsMolokai = new Condition2(boatLock);
		sleepingKidsOahu = new Condition2(boatLock);
		
		KThread threadR = null;
		for(int i = 0; i < children; i++){
			KThread thread = new KThread(new Runnable(){
				public void run(){
					ChildItinerary();
				}
			});
			thread.setName("Child #"+ i);
			thread.fork();

			if(i==0){
				threadR = thread;
			}
		}
		for(int i = 0; i < adults; i++){
			KThread thread = new KThread(new Runnable(){
				public void run(){
					AdultItinerary();
				}
			});
			thread.setName("Adults #" + i);
			thread.fork();
		}

		System.out.println("Starting the Boat Trip to Molokai");
		boolean intStatus = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(KThread.currentThread(), 0);
		Machine.interrupt().restore(intStatus);
		KThread.yield();
		System.out.println("Finished the Boad Trip to Molokai");
	}

	static void AdultItinerary()
	{
		bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE. 

		/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
		 */

		adultsOnOahu++;
		boatLock.acquire();
		sleepingAdultsOahu.sleep();
		bg.AdultRowToMolokai();
		adultsOnOahu--;
		System.out.println("Adult row to Molokai, woke kid: kidsOnOahu: " + kidsOnOahu + ", awakeKidsOahu: " + awakeKidsOahu + ", adultsOnOahu: " + adultsOnOahu);
		sleepingKidsMolokai.wake();
		boatLock.release();
	}

	static void ChildItinerary()
	{
		bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE. 

		boolean onOahu = true;
		kidsOnOahu++;
		awakeKidsOahu++;
		KThread.yield();
		if(awakeKidsOahu > 2){
			awakeKidsOahu--;
			boatLock.acquire();
			sleepingKidsOahu.sleep();
			boatLock.release();
		}
		while(!done){
			boatLock.acquire();
			if(onOahu){
				if(awakeKidsOahu == 2){
					bg.ChildRowToMolokai();
					onOahu = false;
					awakeKidsOahu--;
					kidsOnOahu--;
					System.out.println("Child Row to Molokai, going to sleep kidsOnOahu: " + kidsOnOahu + ", awakeKidsOahu: " + awakeKidsOahu + ", adultsOnOahu: " + adultsOnOahu);
					sleepingKidsMolokai.sleep();
				} else {
					bg.ChildRideToMolokai();
					onOahu = false;
					awakeKidsOahu--;
					kidsOnOahu--;
					System.out.println("Child Ride to Molokai kidsOnOahu: " + kidsOnOahu + ", awakeKidsOahu: " + awakeKidsOahu + ", adultsOnOahu: " + adultsOnOahu);
				}
			} else {
				if(adultsOnOahu == 0 && kidsOnOahu == 0){
					System.out.println("All Done kidsOnOahu: " + kidsOnOahu + ", awakeKidsOahu: " + awakeKidsOahu + ", adultsOnOahu: " + adultsOnOahu);
					done = true;
					return;
				}
				bg.ChildRowToOahu();
				onOahu = true;
				kidsOnOahu++;
				awakeKidsOahu++;
				System.out.println("Child Row to Oaho kidsOnOahu: " + kidsOnOahu + ", awakeKidsOahu: " + awakeKidsOahu + ", adultsOnOahu: " + adultsOnOahu);
				if(kidsOnOahu > 1){
					sleepingKidsOahu.wake();
					awakeKidsOahu++;
				} else {
					sleepingAdultsOahu.wake();
					awakeKidsOahu--;
					System.out.println("Woke adult, going to sleep kidsOnOahu: " + kidsOnOahu + ", awakeKidsOahu: " + awakeKidsOahu + ", adultsOnOahu: " + adultsOnOahu);
					sleepingKidsOahu.sleep();
				}
			}	
			boatLock.release();
		}
	}

	static void SampleItinerary()
	{
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}