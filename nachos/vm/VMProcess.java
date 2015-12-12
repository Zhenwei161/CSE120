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
      Machine.processor().writeTLBEntry(i, tE);
      //savedState[i] = tE.vpn;
    }

	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
    /*for(int i = 0; i < Machine.processor().getTLBSize(); i++)
    {
      
      TranslationEntry tE = Machine.processor().readTLBEntry(i);
      tE.valid = false;
      Machine.processor().writeTLBEntry(i, tE);
    }
    for(int i = 0; i < Machine.processor().getTLBSize(); i++)
    {
      if(pageTable[savedState[i]].valid)
      {
        Machine.processor().writeTLBEntry(i, pageTable[savedState[i]]);
      }
    }*/
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
    for(int vpn = 0; vpn < pageTable.length; vpn++)
    {
      if(pageTable[vpn].valid)
      {
        UserKernel.freePages.add(new Integer(pageTable[vpn].ppn));
        //IPT[pageTable[vpn].ppn] = null;
      }
    }

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

  protected int pinVirtualPage(int vpn, boolean isUserWrite)
  {
    int ppn = super.pinVirtualPage(vpn, isUserWrite);
    if(ppn >= 0)
    {
      if(isUserWrite)
      {
        IPT[ppn].pinCount = true;
      }
    }
    else
    {
      if (vpn < 0 || vpn >= pageTable.length)
	      return -1;

      if(!pageTable[vpn].valid)
      {
        allocatePage(vpn);
      }  
      ppn = super.pinVirtualPage(vpn, isUserWrite);
      if(isUserWrite)
      {
        IPT[ppn].pinCount = true;
      }
    }
    return ppn;
  }

  protected void unpinVirtualPage(int vpn)
  {
    int ppn = super.pinVirtualPage(vpn, false);
    if(ppn >= 0)
    {
      IPT[ppn].pinCount = false;
      unpinnedPageLock.acquire();
      unpinnedPage.wake();
      unpinnedPageLock.release();
    }
  }

  public void handleTLBMiss()
  {
    int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
    int vpn =  Processor.pageFromAddress(vaddr);
    allocatePage(vpn);
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

  private void allocatePage(int vpn)
  {
    //System.out.println("TLBMiss: VPN:" + vpn + " of Process " + processID);
    TranslationEntry t = pageTable[vpn];
    if(!t.valid)
    {
      //System.out.println("Allocating VPN: " + t.vpn);
      lock.acquire();
      if(UserKernel.freePages.size() > 0)
      {
  	    int ppn = ((Integer)UserKernel.freePages.removeFirst()).intValue();
        t.valid = true;    
        if(!t.dirty)
        {
          t.ppn = ppn;
          IPT[ppn] = new IPTEntry();
          IPT[ppn].process = this;
          IPT[ppn].tE = t;
  
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
//                System.out.println("VPN \"" + t.vpn + "\" is a COFF section");
                //System.out.println("COFF section loaded into VPN \"" + t.vpn + "\" PPN \"" + t.ppn + "\"");
                isCoffSection = true;
                break;
              }
            }
          }

          if(!isCoffSection)
          {
//            System.out.println("VPN \"" + t.vpn + "\" is NOT a COFF section");
            //System.out.println("PPN \"" + t.ppn + "\" Zero'd Out");
            byte[] data = new byte[Processor.pageSize];
            //writeVirtualMemory(t.vpn, data, 0, Processor.pageSize);
            byte[] memory = Machine.processor().getMemory();
            System.arraycopy(data, 0, memory, 
                  t.ppn*Processor.pageSize, Processor.pageSize);
          }
        }
        else //Dirty
        {
//          System.out.println("VPN \"" + t.vpn + "\" has been swapped out, swaping into PPN \"" + ppn + "\"");
          int spn = t.ppn;
          byte[] data = new byte[pageSize];
          byte[] memory = Machine.processor().getMemory();
          OpenFile swap = ThreadedKernel.fileSystem.open(".Nachos.swp", false);
          swap.read(spn * pageSize, memory, ppn * pageSize, pageSize);
          swap.close();
          t.ppn = ppn;
          //System.out.println(spn);
          freeSwapPages.set(spn, true);
          IPT[ppn] = new IPTEntry();
          IPT[ppn].process = this;
          IPT[ppn].tE = t;
//          System.out.println("SPN \"" + spn + "\" has been swapped in.");
        }
      }
      else
      {
        syncTLB();

        //Clock Algorithm
        unpinnedPageLock.acquire();
        int numPinnedPages = 0;
        while(IPT[victim] == null || IPT[victim].tE.used || 
          IPT[victim].pinCount)
        {
          if(IPT[victim] != null)
          {
            IPT[victim].tE.used = false;
            //System.out.println("PPN " + victim + " is used.");
          }
          if(IPT[victim].pinCount)
          {
            numPinnedPages++;
            //System.out.println("PPN " + victim + " is pinned.");
          }
          if(numPinnedPages == IPT.length)
            unpinnedPage.sleep();
//          System.out.println("This Process is " + this.processID + " and the IPT's process is " + IPT[victim].processID);
          victim = (victim + 1) % Machine.processor().getNumPhysPages();
        }
        //System.out.println("PPN " + victim + " will be evicted.");
        unpinnedPageLock.release();
        int evict = IPT[victim].tE.vpn;
        VMProcess evictedOwner = IPT[victim].process;
        int evictedPPN = victim;
        victim = (victim + 1) % Machine.processor().getNumPhysPages();

        //System.out.println("Not enough pysical memory, evicting VPN \"" + evict + "\" of process " + evictedOwner.processID); 
        //Swap Files
        if(evictedOwner.isDirty(evict))
        {
          //System.out.println("VPN \"" + evict + "\" is dirty, swapping out.");
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
          UserKernel.freePages.add(evictedPPN);
          byte[] memory = Machine.processor().getMemory();
          swap.write(spn * pageSize, memory, 
                       evictedPPN * pageSize, pageSize);
          swap.close();
          if(evictedOwner.processID != this.processID)
          {
            evictedOwner.evict(evict, spn);
          }
          else
          {
            pageTable[evict].ppn = spn;
            pageTable[evict].valid = false;
          } 
          for(int i = 0; i < Machine.processor().getTLBSize(); i++)
          {
            TranslationEntry tE = Machine.processor().readTLBEntry(i);
            if(tE.vpn == evict && tE.ppn == evictedPPN)
            {
//              System.out.println("TLBEntry " + i + " invalid");
              tE.valid = false;
              Machine.processor().writeTLBEntry(i, tE);
            }
          }
          lock.release();
          
//          System.out.println("VPN \"" + evict + "\" of Process " + this.processID + " swapped out to SPN \"" + spn + "\".");
          //Machine.processor().readTLBEntry(evict).valid = false;
          allocatePage(vpn);
          return;
        }
        else
        {
          if(evictedOwner.processID != this.processID)
          {
            //System.out.println("Evicted VPN " + evict + " of Process " + this.processID);
            evictedOwner.evict(evict);
          }
          else
          {
            pageTable[evict].valid = false;
          }
          UserKernel.freePages.add(evictedPPN);
          for(int i = 0; i < Machine.processor().getTLBSize(); i++)
          {
            TranslationEntry tE = Machine.processor().readTLBEntry(i);
            if(tE.vpn == evict && tE.ppn == evictedPPN)
            {
//              System.out.println("TLBEntry " + i + " invalid");
              tE.valid = false;
              Machine.processor().writeTLBEntry(i, tE);
            }
          }
          lock.release();
          allocatePage(vpn);
          return;
        }
      }
      lock.release();
    }
    for(int i = 0; i < Machine.processor().getTLBSize(); i++)
    {
      TranslationEntry tE = Machine.processor().readTLBEntry(i);
      if(!tE.valid)
      {
        //System.out.println("Evicted TLBEntry " + i);
        Machine.processor().writeTLBEntry(i, t);
        //System.out.println(t.valid);
        return;
      }
    }
    syncTLB();
    int evictedEntry = (int)(Math.random() * Machine.processor().getTLBSize());
    TranslationEntry tE = Machine.processor().readTLBEntry(evictedEntry);
    pageTable[tE.vpn] = tE;
    Machine.processor().writeTLBEntry(evictedEntry, t);
    //System.out.println("Evicted TLBEntry " + evictedEntry + " randomly");
    //System.out.println("TE entered is VPN \"" + t.vpn + "\" PPN \"" + t.ppn + "\"");

  }

  public void evict(int vpn, int spn)
  {
    pageTable[vpn].ppn = spn;
    pageTable[vpn].valid = false;
  }

  public void evict(int vpn)
  {
    pageTable[vpn].valid = false;
  }

  public boolean isDirty(int vpn)
  {
    return pageTable[vpn].dirty;
  }

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

  private static int victim = 0;

  private static LinkedList<Boolean> freeSwapPages = new LinkedList<Boolean>();

  private static IPTEntry[] IPT = new IPTEntry[Machine.processor().getNumPhysPages()];

  private class IPTEntry
  {
    public VMProcess process;
    public TranslationEntry tE;
    public boolean pinCount;
  }
  public static Lock unpinnedPageLock = new Lock();
  public static Condition unpinnedPage = new Condition(unpinnedPageLock);

  public static Lock lock = new Lock();

  private int[] savedState = new int[Machine.processor().getTLBSize()];

}

