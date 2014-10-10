package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Machine;

public class Boat
{
	static BoatGrader bg;
	static int totalKids;
	static int totalAdults;
	static boolean allThreadsIncd;
	static int awakeKidsOahu;
	static int kidsOnOahu;
	static int adultsOnOahu;
	static Condition2 sleepingKidsOahu;
	static Condition2 sleepingKidsMolokai;
	static Condition2 sleepingAdultsOahu;
	static boolean done;

	public static void selfTest()
	{
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		//  	begin(1, 2, b);

		//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		//  	begin(3, 3, b);
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
		Lock boatLock  = new Lock();
		sleepingAdultsOahu = new Condition2(boatLock);
		sleepingKidsMolokai = new Condition2(boatLock);
		sleepingKidsOahu = new Condition2(boatLock);
				
		for(int i = 0; i < children; i++){
			KThread thread = new KThread(new Runnable(){
				public void run(){
					ChildItinerary();
				}
			});
			thread.setName("Child #"+ i);
			thread.fork();
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
		
//		Runnable r = new Runnable() {
//			public void run() {
//				SampleItinerary();
//			}
//		};
//		KThread t = new KThread(r);
//		t.setName("Sample Boat Thread");
//		t.fork();

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
		
		if(!allThreadsIncd){
			totalAdults++;
			adultsOnOahu++;
			sleepingAdultsOahu.sleep();
		}
		bg.AdultRowToMolokai();
		sleepingKidsMolokai.wake();
		
		
	}

	static void ChildItinerary()
	{
		bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE. 
		
		boolean onOahu = true;
		if(!allThreadsIncd){
			totalKids++;
			kidsOnOahu++;
			awakeKidsOahu++;
		}
		Machine.yield();
		allThreadsIncd = true;
		if(awakeKidsOahu > 2){
			awakeKidsOahu--;
			sleepingKidsMolokai.sleep();
		}
		
		while(!done){
			if(onOahu){
				if(awakeKidsOahu == 2){
					bg.ChildRowToMolokai();
					onOahu = false;
					awakeKidsOahu--;
					kidsOnOahu--;
					Machine.yield();
					if(adultsOnOahu == 0 && kidsOnOahu == 0){
						done = true;
					}
					bg.ChildRowToOahu();
					onOahu = true;
					kidsOnOahu++;
					awakeKidsOahu++;
					if(kidsOnOahu > 1){
						sleepingKidsOahu.wake();
						awakeKidsOahu++;
					} else{
						sleepingAdultsOahu.wake();
						sleepingKidsOahu.sleep();
					}
					
				}else{
					bg.ChildRideToMolokai();
					onOahu = false;
					awakeKidsOahu--;
					kidsOnOahu--;
					sleepingKidsMolokai.sleep();
				}
			}else{
				bg.ChildRowToOahu();
				onOahu = true;
				awakeKidsOahu++;
				kidsOnOahu++;
				sleepingKidsOahu.wake();
				awakeKidsOahu++;
			}
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