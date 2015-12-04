package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
    
    for(int i = 0; i < Machine.processor().getTLBSize(); i++)
    {
      
      TranslationEntry tE = Machine.processor().readTLBEntry(i);
      if(tE.valid)
      {
        pageTable[tE.vpn] = tE;
      }
      tE.valid = false;
    }
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
	  UserKernel.memoryLock.acquire();
  	pageTable = new TranslationEntry[numPages];

	  for (int vpn=0; vpn<numPages; vpn++) {
	    pageTable[vpn] = new TranslationEntry(vpn, -1,
						  false, false, false, false);
	  }
	
	  UserKernel.memoryLock.release();

	  // load sections
    
	  for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		    int vpn = section.getFirstVPN()+i;

		    pageTable[vpn].readOnly = section.isReadOnly();
		    //section.loadPage(i, pinVirtualPage(vpn, false));
	    }
	  }

    return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
      case Processor.exceptionTLBMiss:
        handleTLBMiss();
        break;
	  	default:
		  	super.handleException(cause);
			  break;
		}
	}

  public void handleTLBMiss()
  {
    int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
    int vpn = Processor.pageFromAddress(vaddr);
    TranslationEntry t = pageTable[vpn];
    if(!t.valid)
    {
      if(UserKernel.freePages.size() > 0)
      {
	      int ppn = ((Integer)UserKernel.freePages.removeFirst()).intValue();
        t.ppn = ppn;
        t.valid = true;
        if(t.readOnly)
        {
	        for (int s=0; s<coff.getNumSections(); s++) {
	          CoffSection section = coff.getSection(s);
            if(section.getFirstVPN() > t.vpn)
            {
              continue;
            }
	          for (int i=0; i<section.getLength(); i++) {
		          int svpn = section.getFirstVPN()+i;
              if(svpn == t.vpn)
              {
		            section.loadPage(i, pinVirtualPage(svpn, false));
                break;
              }
	          }
 	        }

        }
        else
        {
          byte[] data = new byte[Processor.pageSize];
          writeVirtualMemory(t.vpn, data, 0, Processor.pageSize);
        }
      }
      else
      {
        //SwapFile
      }
    }
    for(int i = 0; i < Machine.processor().getTLBSize(); i++)
    {
      TranslationEntry tE = Machine.processor().readTLBEntry(i);
      if(!tE.valid)
      {
        Machine.processor().writeTLBEntry(i, t);
        return;
      }
    }
    int evictedEntry = (int)(Math.random() * Machine.processor().getTLBSize());
    TranslationEntry tE = Machine.processor().readTLBEntry(evictedEntry);
    pageTable[tE.vpn] = tE;
    Machine.processor().writeTLBEntry(evictedEntry, t);

  }

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
