package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		conditionLock.release();
    Machine.interrupt().disable();
    waitQueue.waitForAccess(KThread.currentThread());
    KThread.sleep();
    Machine.interrupt().enable();
		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    Machine.interrupt().disable();
    KThread thread = waitQueue.nextThread();
    if(thread != null)
    {
      thread.ready();
    }
    Machine.interrupt().enable();
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
    KThread thread;

    Machine.interrupt().disable();
    while((thread = waitQueue.nextThread()) != null)
    {
      thread.ready();
    }
    Machine.interrupt().enable();
	}


public static void selfTest(){
      final Lock lock = new Lock();
      final Condition2 empty = new Condition2(lock);
      final LinkedList<Integer> list = new LinkedList<>();
          
      KThread consumer = new KThread( new Runnable () {
        public void run() {
          lock.acquire();
          while(list.isEmpty()){
            empty.sleep();
          }
          Lib.assertTrue(list.size() == 5, "List should have 5 values.");
          while(!list.isEmpty()) {
            System.out.println("Removed " + list.removeFirst());
          }
          lock.release();
        }
     });

     KThread producer = new KThread( new Runnable () {
       public void run() {
         lock.acquire();
         for (int i = 0; i < 5; i++) {
           list.add(i);
           System.out.println("Added " + i);
         }
         empty.wake();
         lock.release();
       }
     });
                              
     consumer.setName("Consumer");
     producer.setName("Producer");
     consumer.fork();
     producer.fork();
     consumer.join();
     producer.join();
}

	private Lock conditionLock;
  private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
}
