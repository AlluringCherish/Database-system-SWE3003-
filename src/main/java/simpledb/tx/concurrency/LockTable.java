package simpledb.tx.concurrency;

import java.util.*;
import simpledb.file.BlockId;

/**
 * The lock table, which provides methods to lock and unlock blocks.
 * If a transaction requests a lock that causes a conflict with an
 * existing lock, then that transaction is placed on a wait list.
 * There is only one wait list for all blocks.
 * When the last lock on a block is unlocked, then all transactions
 * are removed from the wait list and rescheduled.
 * If one of those transactions discovers that the lock it is waiting for
 * is still locked, it will place itself back on the wait list.
 * @author Edward Sciore
 */
class LockTable {
   private static final long MAX_TIME = 10000; // 10 seconds
   private Map<BlockId,List<Integer>> locks = new HashMap<BlockId,List<Integer>>();
   
   /**
    * Grant an SLock on the specified block.
    * If an XLock exists when the method is called,
    * then the calling thread will be placed on a wait list
    * until the lock is released.
    * If the thread remains on the wait list for a certain 
    * amount of time (currently 10 seconds),
    * then an exception is thrown.
    * @param blk a reference to the disk block
    */
   public synchronized void sLock(BlockId blk, int txnum) {
      try {
         while(CheckForSLock(blk,txnum)){
            wait(500);
         }
         if (!locks.containsKey(blk)){
            List<Integer> txs = new ArrayList<Integer>();
            txs.add(txnum);
            locks.put(blk, txs);
         }
         else{
            locks.get(blk).add(txnum);
         }

      }
      catch(InterruptedException e) {
         throw new LockAbortException();
      }
   }
   
   /**
    * Grant an XLock on the specified block.
    * If a lock of any type exists when the method is called,
    * then the calling thread will be placed on a wait list
    * until the locks are released.
    * If the thread remains on the wait list for a certain 
    * amount of time (currently 10 seconds),
    * then an exception is thrown.
    * @param blk a reference to the disk block
    */
   synchronized void xLock(BlockId blk, int txnum) {
      try {
         while (CheckForXLock(blk,txnum)){
            wait(500);
         }


         if (!locks.containsKey(blk)){
            List<Integer> txs = new ArrayList<Integer>();
            txs.add(txnum);
            locks.put(blk, txs);
         }
         else{
            locks.get(blk).add(-txnum);
         }

      }
      catch(InterruptedException e) {
         throw new LockAbortException();
      }
   }
   
   /**
    * Release a lock on the specified block.
    * If this lock is the last lock on that block,
    * then the waiting transactions are notified.
    * @param blk a reference to the disk block
    */
   synchronized void unlock(BlockId blk, int txnum) {
      List<Integer> txs = locks.get(blk);
      if (txs != null){
         txs.remove(Integer.valueOf(txnum));
         txs.remove(Integer.valueOf(-txnum));
         if (txs.isEmpty()){
            locks.remove(blk);
            notifyAll();
         }
         
      }
   }
   
   /*
   private boolean hasXlock(BlockId blk) {
      return getLockVal(blk) < 0;
   }
   
   private boolean hasOtherSLocks(BlockId blk) {
      return getLockVal(blk) > 1;
   }
   
   private boolean waitingTooLong(long starttime) {
      return System.currentTimeMillis() - starttime > MAX_TIME;
   }
   

   private int getLockVal(BlockId blk) {
      Integer ival = locks.get(blk);
      return (ival == null) ? 0 : ival.intValue();
   }
   */

   private boolean CheckForSLock (BlockId blk, int txnum){
      List<Integer> txs = locks.get(blk);
      if (txs==null){
         return false;
      }
      for (int tx : txs){
         if (tx<0){
            if (-tx < txnum){ // New is Young && xlock exists -> die.
               throw new LockAbortException();
            }
            return true; // New is old && xlock exists -> wait.
         }

         
      }
      return false;
   }




   private boolean CheckForXLock (BlockId blk, int txnum){
      List<Integer> txs = locks.get(blk);
      if (txs==null){
         return false;
      }
      for (int tx : txs){
         if (tx != txnum){
            if (tx>0){
               if (tx < txnum){ // New is young. -> die
                  throw new LockAbortException();
               }
               return true;
            }

            else {
               if (-tx < txnum){ // similary.
                  throw new LockAbortException();
               }
             
               return true;
            }
         }
      }

      return false;
   }
}
