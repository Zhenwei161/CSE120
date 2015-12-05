package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.LinkedList;

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
        pageTable[tE.vpn].dirty = tE.dirty;
        pageTable[tE.vpn].used = tE.used;
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
    System.out.println("TLBMiss: VPN:" + vpn);
    if(!t.valid)
    {
      System.out.println("Allocating VPN: " + t.vpn);
      if(UserKernel.freePages.size() > 0)
      {
  	    int ppn = ((Integer)UserKernel.freePages.removeFirst()).intValue();
        t.valid = true;
        if(!t.dirty)
        {
          t.ppn = ppn;
  
          boolean isCoffSection = false;
          for (int s=0; s<coff.getNumSections(); s++) {
            if(isCoffSection)
            {
              break;
            }
            CoffSection section = coff.getSection(s);
            if(section.getFirstVPN() > t.vpn)
            {
              continue;
            }
            for (int i=0; i<section.getLength(); i++) {
              int svpn = section.getFirstVPN()+i;
              if(svpn == t.vpn)
              {
                section.loadPage(i, pinVirtualPage(t.vpn, false));
                System.out.println("VPN \"" + t.vpn + "\" is a COFF section");
                System.out.println("COFF section loaded into VPN \"" + t.vpn + "\" PPN \"" + t.ppn + "\"");
                isCoffSection = true;
                break;
              }
            }
          }

          if(!isCoffSection)
          {
            System.out.println("VPN \"" + t.vpn + "\" is NOT a COFF section");
            System.out.println("PPN \"" + t.ppn + "\" Zero'd Out");
            byte[] data = new byte[Processor.pageSize];
            //writeVirtualMemory(t.vpn, data, 0, Processor.pageSize);
            byte[] memory = Machine.processor().getMemory();
            System.arraycopy(data, 0, memory, 
                  t.ppn*Processor.pageSize, Processor.pageSize);
          }
        }
        else //Dirty
        {
          int spn = t.ppn;
          byte[] data = new byte[pageSize];
          byte[] memory = Machine.processor().getMemory();
          OpenFile swap = ThreadedKernel.fileSystem.open(".Nachos.swp", false);
          swap.read(spn * pageSize, memory, ppn * pageSize, pageSize);
          t.ppn = ppn;
          freeSwapPages.set(spn, true);
        }
      }
      else
      {
        syncTLB();
        //Clock Algorithm
        while(pageTable[victim].used)
        {
          pageTable[victim].used = false;
          victim = (victim + 1) % pageTable.length;
        }

        int evict = victim;
        victim = (victim + 1) % pageTable.length;

        //Swap Files
        if(pageTable[evict].dirty)
        {
          OpenFile swap = ThreadedKernel.fileSystem.open(".Nachos.swp", false);
          int spn = 0;
          for(spn = 0; spn < freeSwapPages.size(); spn++)
          {
            if(freeSwapPages.get(spn))
            {
              break;
            }
          }
          if(spn == freeSwapPages.size())
          {
            freeSwapPages.add(false);
          }
          else
          {
            freeSwapPages.set(spn, false);
          }
          pageTable[evict].ppn = spn;
          byte[] memory = Machine.processor().getMemory();
          swap.write(spn * pageSize, memory, 
                       pageTable[evict].ppn * pageSize, pageSize);
          pageTable[evict].valid = false;
          Machine.processor().readTLBEntry(evict).valid = false;
          UserKernel.freePages.add(pageTable[evict].ppn);
          return;
        }

      }
    }
    for(int i = 0; i < Machine.processor().getTLBSize(); i++)
    {
      TranslationEntry tE = Machine.processor().readTLBEntry(i);
      if(!tE.valid)
      {
        System.out.println("Evicted TLBEntry " + i);
        Machine.processor().writeTLBEntry(i, t);
        return;
      }
    }
    int evictedEntry = (int)(Math.random() * Machine.processor().getTLBSize());
    TranslationEntry tE = Machine.processor().readTLBEntry(evictedEntry);
    pageTable[tE.vpn] = tE;
    Machine.processor().writeTLBEntry(evictedEntry, t);

  }

  private void syncTLB()
  {
    for(int i = 0; i < Machine.processor().getTLBSize(); i++)
    {
      
      TranslationEntry tE = Machine.processor().readTLBEntry(i);
      if(tE.valid)
      {
        pageTable[tE.vpn].dirty = tE.dirty;
        pageTable[tE.vpn].used = tE.used;
      }
    }
  }

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

  private int victim = 0;

  private static LinkedList<Boolean> freeSwapPages = new LinkedList<Boolean>();
}
