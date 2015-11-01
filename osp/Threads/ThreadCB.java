package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

import java.util.*;

/**
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
	private static ReadyQueue readyQueue = new ReadyQueue();
	
	
    /**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
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
    	// First condition: Check to make sure the task is not at maximum threads per task
    	if (task.getThreadCount() >= MaxThreadsPerTask) {
    		return null; // If so, return null
    	}
    	else {
    		ThreadCB newThread = new ThreadCB();
    		
    		// Add the new thread to task, make sure it doesn't fail
    		if (task.addThread(newThread) == FAILURE) {
    			return null; // if new thread fails to add, return null
    		}
    		else {
    			newThread.setTask(task);
    		}
    		
    		newThread.setPriority(1); // We're not using a priority queue so choose arbitrary number
    		newThread.setStatus(ThreadReady);
    		readyQueue.append(newThread);
    		
    		dispatch();
    		
    		return newThread;		
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
    	int status = this.getStatus();
    	this.setStatus(ThreadKill);
    	
    	TaskCB task = this.getTask();
    	task.removeThread(this); // Disassociate killed thread from task
    	
    	if (status == ThreadReady) {
    		readyQueue.removeAThread(this);
    	}
    	else if (status == ThreadRunning) {
    		MMU.setPTBR(null);
    		task.setCurrentThread(null);
    	}
    	else if (status >= ThreadWaiting) {
    		for (int i = 0; i < Device.getTableSize(); i++) {
    			Device.get(i).cancelPendingIO(this);
    		}
    	}
    	
    	// Release any resources this thread claimed
    	ResourceCB.giveupResources(this);
    	
    	// Always dispatch() regardless
    	dispatch();
    	
    	// Lastly, check to see if task has no threads, if so, then kill task
    	if (task.getThreadCount() == 0) {
    		task.kill();
    	}
    }

    /** Suspends the thread that is currently on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a page fault
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
        // your code goes here
    	if (this.getStatus() == ThreadRunning) {
    		this.setStatus(ThreadWaiting);
    		MMU.setPTBR(null);
    		this.getTask().setCurrentThread(null);
    	}
    	else if (this.getStatus() >= ThreadWaiting) {
    		this.setStatus(this.getStatus()+1);
    	}
    	// Add to event queue
    	event.addThread(this);
    	
    	// Always dispatch() regardless
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
    	if (this.getStatus() < ThreadWaiting) {
    		MyOut.print(this, "Attempt to resume " + this + ", which wasn't waiting");
    		return;
    	}
    	
    	MyOut.print(this,  "Resuming " + this);
    	
    	// Set thread's status.
    	if (this.getStatus() == ThreadWaiting) {
    		this.setStatus(ThreadReady);
    	}
    	else if (this.getStatus() > ThreadWaiting) {
    		this.setStatus(this.getStatus()-1);
    	}
    	
    	// Put the thread on the ready queue, if appropriate
    	if (this.getStatus() == ThreadReady) {
    		readyQueue.append(this);
    	}
    	
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
        // your code goes here
    	if ((MMU.getPTBR() != null) && (MMU.getPTBR().getTask().getCurrentThread() != null)) {
    		return SUCCESS; // There is a thread currently running, we aren't preemptive
    	}
    	else if (readyQueue.peek() == null) {
    		return FAILURE; // There is no thread running and there is NO ready thread
    	}
    	else {
    		// There is no thread running, but we have a thread ready to be scheduled
    		ThreadCB thread = readyQueue.remove();
    		MMU.setPTBR(thread.getTask().getPageTable());
    		thread.getTask().setCurrentThread(thread);
    		thread.setStatus(ThreadRunning);
    		return SUCCESS;
    	}
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

}

/*
      Feel free to add local classes to improve the readability of your code
*/

class ReadyQueue {
	
	
	private Queue<ThreadCB> readyQueue;
	
	
	public ReadyQueue() {
		this.readyQueue = new LinkedList<ThreadCB>();
	}
	
	public void append(ThreadCB thread) {
		this.readyQueue.add(thread);
	}
	
	public ThreadCB remove() {
		return this.readyQueue.remove();
	}
	
	public void removeAThread(ThreadCB thread) {
		this.readyQueue.remove(thread);
	}
	
	public ThreadCB peek() {
		return this.readyQueue.peek();
	}
}
