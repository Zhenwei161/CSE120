package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
  private Lock lock = new Lock();
  private Condition2 speakerCV;
  private Condition2 speakerCV2;
  private Condition2 listenerCV;
  private Condition2 listenerCV2;
  private int listeners;
  private int speakers;
  private boolean read = false;
  private int transferWord;

  public Communicator() {
    speakerCV = new Condition2(lock);
    listenerCV = new Condition2(lock);
    speakerCV2 = new Condition2(lock);
    listenerCV2 = new Condition2(lock);
	}

  /**
   * Wait for a thread to listen through this communicator, and then transfer
   * <i>word</i> to the listener.
   * 
   * <p>
   * Does not return until this thread is paired up with a listening thread.
   * Exactly one listener should receive <i>word</i>.
   * 
   * @param word the integer to transfer.
   */
  public void speak(int word) {
    lock.acquire();

    if(speakers > 0)
    {
      speakerCV.sleep();
    }
    if(listeners > 0)
    {
      if(read == true)
      {
        transferWord = word;
        read = false;
        listenerCV.wake();
      }
      else
      {
        speakerCV.sleep();
        transferWord = word;
        read = false;
        listenerCV.wake();
      }
    }
    else
    {
      speakers++;
      transferWord = word;
      read = false;
      listenerCV.wake();
      speakerCV2.sleep();
      speakers--;
      speakerCV.wake();
    }
    lock.release();
  }

  /**
   * Wait for a thread to speak through this communicator, and then return the
   * <i>word</i> that thread passed to <tt>speak()</tt>.
   * 
   * @return the integer transferred.
   */
  public int listen() {
    lock.acquire();
    int returnWord = 0;
    if(listeners > 0)
    {
      listenerCV2.sleep();
    }

    if(speakers > 0)
    {
      if(read == false)
      {
        returnWord = transferWord;
        read = true;
        speakerCV2.wake();
      }
      else
      {
        speakerCV.wake();
        listenerCV.sleep();
        returnWord = transferWord;
        read = true;
        speakerCV2.wake();
      }
    }
    else
    {
      listeners++;
      read = true;
      speakerCV.wake();
      listenerCV.sleep();
      returnWord = transferWord;
      listenerCV2.wake();
      listeners--;
    }
    lock.release();
    return returnWord;
  }

  public static void selfTest(){
    final Communicator com = new Communicator();
    final long times[] = new long[4];
    final int words[] = new int[2];
    KThread speaker1 = new KThread( new Runnable () {
        public void run() {
            com.speak(4);
            times[0] = Machine.timer().getTime();
        }
    });
    speaker1.setName("S1");
    KThread speaker2 = new KThread( new Runnable () {
        public void run() {
            com.speak(7);
            times[1] = Machine.timer().getTime();
        }
    });
    speaker2.setName("S2");
    KThread listener1 = new KThread( new Runnable () {
        public void run() {
            times[2] = Machine.timer().getTime();
            words[0] = com.listen();
        }
    });
    listener1.setName("L1");
    KThread listener2 = new KThread( new Runnable () {
        public void run() {
            times[3] = Machine.timer().getTime();
            words[1] = com.listen();
        }
    });
    listener2.setName("L2");
    
    speaker1.fork(); speaker2.fork(); listener1.fork(); listener2.fork();
    speaker1.join(); speaker2.join(); listener1.join(); listener2.join();
    
    Lib.assertTrue(words[0] == 4, "Didn't listen back spoken word."); 
    Lib.assertTrue(words[1] == 7, "Didn't listen back spoken word.");
    Lib.assertTrue(times[0] > times[2], "speak() returned before listen() called.");
    Lib.assertTrue(times[1] > times[3], "speak() returned before listen() called.");
  }
}
