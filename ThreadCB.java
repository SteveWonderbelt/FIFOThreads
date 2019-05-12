package osp.Threads;
import java.util.Vector;
import java.util.Queue; 
import java.util.LinkedList;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**

	Name: Steven Childs
	Email: schilds@email.sc.edu
	
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
	//static GenericList readyQueue;
	static  GenericList[] activeArray = new GenericList[5];
	static GenericList[] expiredArray = new GenericList[5];
	
	

	
    /**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
        // your code goes here
		super();

    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
        // your code goes here
		//readyQueue = new GenericList();

		//activeArray = new GenericList[5];
		
		//expiredArray = new GenericList[5];
		for(int i = 0; i<activeArray.length; i++){
			activeArray[i] = new GenericList();
		}
		for(int i = 0; i<expiredArray.length; i++){
			expiredArray[i] = new GenericList();
		}
		//System.out.println(expiredArray.length);
		//System.out.println(activeArray.length);
    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.

	@return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
        // your code goes here
		if(task==null){
			dispatch();
			return null;
		}else if(task.getThreadCount() == MaxThreadsPerTask){
			dispatch();
			return null;
		}else{
			
			//create the thread here
			//if the task is not null and we are not at the Max Threads limit, go ahead and creat the thread. 
			ThreadCB ourThread = new ThreadCB();
			

			
			
			ourThread.setTask(task);
			ourThread.setStatus(ThreadReady);
			ourThread.setPriority(2); //set priority to 2 by default
			//readyQueue.append(ourThread);
			if(task.addThread(ourThread)==0){
				dispatch();
				return null; 
			}
			expiredArray[2].remove(ourThread);
			expiredArray[2].append(ourThread); 
			//expiredArray[2].add(ourThread); 
			dispatch();
			//System.out.println("new thread was created");
			return ourThread; 
			
		}
    }

    /** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
        // your code goes here
		
		TaskCB tempTask = null;
		//code for ThreadReady status
		if(this.getStatus()==ThreadReady){
			//readyQueue.remove(this);
			activeArray[getPriority()].remove(this);
			this.setStatus(ThreadKill);
			
		//code for ThreadRunning status
		}
		if(this.getStatus()==ThreadRunning){
			if(MMU.getPTBR().getTask().getCurrentThread()==this){
				MMU.setPTBR(null);
				getTask().setCurrentThread(null);
			}
		}
		//code for the ThreadWaiting status
		if(this.getStatus() >= ThreadWaiting){
			this.setStatus(ThreadKill);
		}
		tempTask = this.getTask();
		tempTask.removeThread(this); //remove that task from the thread
		this.setStatus(ThreadKill); //set the current thread to ThreadKill
		
		//cycle through list of devices to release resources
		for(int i = 0; i<Device.getTableSize(); i++){
			Device.get(i).cancelPendingIO(this);
		}
		ResourceCB.giveupResources(this);

		
		//check to see if the task has any more threads and kill them if there are. 
		if(this.getTask().getThreadCount()==0){
			this.getTask().kill();
		}
		//System.out.println("Thread was killed");
		dispatch();
		
		
    }

    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
        // check to see if the thread is running
		// if so, check again by checking what the system thinks is the running thread
		// if that checks, set the thread to waiting
		if(this.getStatus()==ThreadRunning){
			if(MMU.getPTBR().getTask().getCurrentThread() == this){
				MMU.setPTBR(null);
				this.getTask().setCurrentThread(null);
				this.setStatus(ThreadWaiting);
			}
		//IF the thread is waiting, just increment its status by one. 
		}else if(this.getStatus()>=ThreadWaiting){
			this.setStatus(this.getStatus()+1);
		}
		//make sure the thread isn't in the ready queue.
		//if it's not, add the event to the thread
		//if(!readyQueue.contains(this)){
		//	event.addThread(this);
		//}
		for(int i = 0; i < expiredArray.length; i++){
			if(!expiredArray[i].contains(this)){
				event.addThread(this);
			}
		}
		dispatch();
    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public void do_resume()
    {
        // your code goes here
		if(getStatus() < ThreadWaiting){
			MyOut.print(this,
				"Attempt to resume "
				+this+", which wasn't waiting");
			return; 
		}
		MyOut.print(this, "Resuming "+this);
		
		//set thread status
		if(getStatus() == ThreadWaiting){
			setStatus(ThreadReady);
		}else if(getStatus() > ThreadWaiting){
			setStatus(getStatus()-1);
		}
		//ThreadCB dumThread = (ThreadCB)expiredArray[getPriority()].remove(this); //remove the thread from expired arary and save it
		if(getPriority() != 0){
			setPriority(getPriority() -1); //adjust the thread's priority
		}
		//Put the thread on onthe ready queue, if appropriate
		if(this.getStatus() == ThreadReady){
			//readyQueue.append(this);
			expiredArray[getPriority()].remove(this);
			expiredArray[getPriority()].append(this); //put the thread back in the array but with its new priority
			
		}
		//System.out.println("Done with resume");
		dispatch();
    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one thread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
		ThreadCB tempThread = null;
		try{
			//System.out.println("try loop");
			tempThread = MMU.getPTBR().getTask().getCurrentThread(); //need to make sure we don't break the program with an exception
			//System.out.println("attempted to get currentThread");
		}catch(NullPointerException e){}
		//System.out.println("attempted to get currentThread");		
		
		//Preempted section that adjusts priorities
		if(tempThread != null){ //check if there is currently running thread
			//System.out.println("Thread is currently running");
			if(HTimer.get() < 1){//test if the thread was preempted because its quantum ran out
				//System.out.println("Thread was preempted because of quantum");
				tempThread.getTask().setCurrentThread(null); //task is informed that its not he current thread
				MMU.setPTBR(null); //take away CPU control
				tempThread.setPriority(tempThread.getPriority()+1); //if so, decrease its priority
				expiredArray[tempThread.getPriority()-1].remove(tempThread);
				expiredArray[tempThread.getPriority()].append(tempThread); //then add it to expired array
				tempThread.setStatus(ThreadReady); //set the threads status to ThreadReady
			}else if(HTimer.get() >= 1){
				//System.out.println("Thread was not preempted because of quantum");
				tempThread.getTask().setCurrentThread(null); //task is informed that its not he current thread
				MMU.setPTBR(null); //take away CPU control
				expiredArray[tempThread.getPriority()].remove(tempThread);
				expiredArray[tempThread.getPriority()].append(tempThread); //if not, just add to expired array
				tempThread.setStatus(ThreadReady); //set the threads status to ThreadReady
			}

			//readyQueue.append(tempThread);//add the thread to the ready queue
		}else{//System.out.println("Thread was not running");
		}

		
		//quantum adjustment section
		//Actual dispatch. 

		ThreadCB newThread = nextThreadFromActive(); //will now either be a new thread to dispatch or null
		if(newThread == null){//if there is no activeArray available we will switch all values of expiredArray over to activeArray
			swapArrays();
			newThread = nextThreadFromActive();
			if(newThread == null){//indicates that both arrays are empty
				MMU.setPTBR(null);
				return FAILURE; 
			}else {//otherwise, activeArray still has threads to give
				//tempThread = (ThreadCB) readyQueue.removeHead();
				newThread = nextThreadFromActive();
				MMU.setPTBR(newThread.getTask().getPageTable());
				newThread.getTask().setCurrentThread(newThread);
				newThread.setStatus(ThreadRunning);
				if((newThread.getPriority() == 0) || (newThread.getPriority() == 1)|| (newThread.getPriority() == 2)){//test if thread's priority is 0,1, or 2. 
					HTimer.set(40); //if so, set quantum to 40
				}else{
					HTimer.set(20); //if not priority must be 3-4, so set quantum to 20
				}
			}	
			//System.out.println("do_dispatch finished");
		}
		else if(newThread != null) {//otherwise, activeArray still has threads to give
			//tempThread = (ThreadCB) readyQueue.removeHead();
			newThread = nextThreadFromActive();
			MMU.setPTBR(newThread.getTask().getPageTable());
			newThread.getTask().setCurrentThread(newThread);
			newThread.setStatus(ThreadRunning);
			if((newThread.getPriority() == 0) || (newThread.getPriority() == 1)|| (newThread.getPriority() == 2)){//test if thread's priority is 0,1, or 2. 
				HTimer.set(40); //if so, set quantum to 40
			}else{
				HTimer.set(20); //if not priority must be 3-4, so set quantum to 20
			}
		}	
		
		//System.out.println("do_dispatch finished");
		return SUCCESS;
		
		
		
		
		
    }

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */



/*
      Feel free to add local classes to improve the readability of your code
*/

/**

	This method looks in the activeArray for an avaiable thread. It it finds it one it will rreturn it. 
	If no thread is found it will return null. 
*/
	private static ThreadCB nextThreadFromActive(){
		//System.out.println("Entered nextThreadFormActive");
		ThreadCB dumThread = null;
		//System.out.println("Dummythread made");
		//System.out.println(activeArray.length);
		for(int i = 0; i < activeArray.length; i++){
			//System.out.println("Entered for loop");
			if(activeArray[i].isEmpty()){
				dumThread = null;
			}else{
				dumThread = (ThreadCB)activeArray[i].removeHead();
			}
		}
		return dumThread;
	}
	private static void swapArrays(){
		//System.out.println("Swap entered");
		ThreadCB dummyThread = null;
		GenericList dumList;
		for(int i = 0; i < expiredArray.length; i++){
					//System.out.println("swap for loop entered");
			dumList = (GenericList)expiredArray[i];
			if(dumList != null){
			for(int j = 0; j <expiredArray[i].length(); j++){
				dummyThread = (ThreadCB)expiredArray[i].removeHead();
				activeArray[i].append(dummyThread);
				}
			}
		}
	}
	
}