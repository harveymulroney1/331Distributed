package replica;

import common.*;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicaImpl extends UnicastRemoteObject implements ReplicatedAuction {

    // ----- sequencing & log state (I suggest you keep these and use them) -----
    private boolean isLeader = false;
    private long lastSeqAssigned = 0; // This is used by the leader to assign a seq number to each Operation      
    private long lastCommitted = 0;           
    private long lastApplied = 0;             
    private TreeMap<Long, LogEntry> log = new TreeMap<>(); 

    // ----- state machine (in-memory) -----
    //TODO: declare/initialise state variables 
    ConcurrentHashMap<Integer,String> users = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer,AuctionItem> items = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer,AuctionItem> closedItems = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer,Integer> itemOwners = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer,Integer> itemsHighBidders = new ConcurrentHashMap<>();
    AtomicInteger itemID = new AtomicInteger();
    AtomicInteger userID = new AtomicInteger();
    // ----- replica ID and name (keep these) ----
    private final int id;
    private final String myName;   
    public ReplicaImpl(int id, String rmiName) throws RemoteException { this.id = id; this.myName = rmiName; }

    // ================= Auction (read-only calls are executed locally) =================
    @Override 
    public AuctionItem getSpec(int itemID) { 
        //TODO
        return items.get(itemID);
    }

    @Override 
    public AuctionItem[] listItems() 
    { 
        
        return items.values().toArray(new AuctionItem[0]); 
    }

    // NOTE: this is now a local function that may be used to update local state 
    private int newAuction(int userID, AuctionSaleItem item)
    {
        //TODO
        if(users.containsKey(userID)){return -1;}
        int ID = itemID.incrementAndGet();
        AuctionItem a = new AuctionItem(ID, item.name, item.description,item.reservePrice);
        items.put(ID,a);
        itemOwners.put(ID,userID);
        return ID;
        
        
    }

    // NOTE: this is now a local function that may be used to update local state 
    private AuctionResult closeAuction(int userID, int itemID){
        //TODO
        if(!items.containsKey(itemID)){return null;}
        AuctionItem i = items.get(itemID);
        if(itemOwners.get(itemID) == userID && !closedItems.containsKey(itemID)){
            closedItems.put(itemID,i);
            items.remove(itemID);
            int winUID = itemsHighBidders.get(itemID);
            return new AuctionResult(itemID, winUID, i.highestBid);
        }
        return null;
    }
    // NOTE: this is now a local function that may be used to update local state 
    private boolean bid(int userID, int itemID, int price){
        //TODO
        if(!items.containsKey(itemID) || !users.containsKey(userID) || closedItems.containsKey(itemID))
        {
            //failed
            return false;
        }
        AuctionItem i = items.get(itemID);
        if(price>i.highestBid && price>=i.reservePrice){
            i.highestBid = price;
            itemsHighBidders.put(i.itemID,userID);
            return true;
        }
        return false;
    }
    // NOTE: this is now a local function that may be used to update local state 
    private int register(String email) {
        //TODO
        int UID = userID.incrementAndGet();
        users.put(UID,email);
        return UID;
        
    }
    

    // ================= Replication core =================
    @Override
    public OperationResult handleClientOperation(Operation op, List<String> memberList) throws RemoteException {
        if (!isLeader) {
            //This should not happen
            return OperationResult.fail("Not leader");
        }

        //TODO (suggested high-level steps):
        // Step 1: Add the operation to the local log
        long seq = lastSeqAssigned+=1;
        LogEntry newEntry = new LogEntry(seq, op);
        log.put(seq,newEntry);
        // Step 2: Propose the operation to the rest of the replicas in the memberList (i.e., call propose remote method on members *excluding self* and ignore any unreachable replicas)
        int ackCount =1; // 1 bc leader acks
        for(String rName : memberList){
            if(rName!=this.myName)
            {
                try {
                    ReplicatedAuction repAuc = lookup(rName);
                    boolean ok = repAuc.propose(seq,op);
                    if(ok){ackCount++;}
                } catch (Exception e) {
                    // unreachable
                }

            }
        }
        // Step 3: If majority of replicas acknowledges (assume leader acks), then:
            if(ackCount>=majority(memberList.size()))
            {
                // Step 3.1: Locally execute the operation on self (as the leader) - you may use apply(), 
                apply(op);
                lastApplied = seq;
                //set the operation in the log as committed, and update any other state variables, if needed
                LogEntry entry = log.get(seq);
                entry.committed=true;
                lastCommitted=seq;
                
                //Step 3.2 call commitUpTo on all replicas, again ignoring any unreachable replicas) 
                for(String r : memberList)
                {
                    try {
                        ReplicatedAuction rep = lookup(r);
                        rep.commitUpTo(seq);
                        
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                }
                
                
            }
           else{
            // Step 4: If a majority quorum is not achieved (or in case of other errors), return OperationResult.fail("") and provide a description of the error in the fail() method
            return OperationResult.fail("Error Majority not reached");
           }
        

        return null;
    }

    // Helper method to compute the required number of replicas to achieve majority given the number of the members
    private int majority(int n){ 

        return (n/2)+1; 

    }

    @Override
    public boolean propose(long seqNo, Operation op) {
        // Add the operation to the local Log
        LogEntry existing = log.get(seqNo);
        if (existing == null || !existing.committed) {
            log.put(seqNo, new LogEntry(seqNo, op)); 
        }

        return true;
    }

    @Override
    public boolean commitUpTo(long seqNo) {

        //TODO (suggested high-level steps):
        System.out.println("(DEBUG)Seq Number to commitUpTo"+seqNo);
        // Step 1: Commit the local log entries upto seqNo if there are no missing log entries
        for(long i = this.lastCommitted+1;i<=seqNo;i++)
        {
            LogEntry entry = log.get(i);
            if(entry==null)
            {
                System.out.println("(DEBUG) Entry is Null - fetching missing entries");
                    // Step 2: If there are missing entries before seqNo in the local log, pull them from the leader (using getEntriesAfter)
                try {
                    String leader = findLeaderName();
                    ReplicatedAuction repLeader = lookup(leader);
                    List<LogEntry> missing =  repLeader.getEntriesAfter(i-1);
                    for(LogEntry le : missing)
                    {
                        System.out.println("(DEBUG) In Log Entry Loop - missing");
                        // Step 3: Execute and commit the new operation(s) - you may use apply() on each operation
                        le.committed = true;
                        log.put(le.seqNo,le);
                        apply(le.op);
                        lastApplied=i;
                        lastCommitted=i;
                    }
                    break;
                    
                } catch (Exception e) {
                    // TODO: handle exception
                    return false;
                }
            }
            entry.committed = true;
            lastApplied=i;
            lastCommitted=i;
            System.out.println("(DEBUG) Commited, applied entry.");
        }
        return true;
    }

    // You may use this function to apply operations on the local state
    private OperationResult apply(Operation op){
        switch (op.type) {
            case REGISTER -> {
                int uid = register(op.email);  
                return OperationResult.reg(uid);
            }
            case NEW_AUCTION -> {
                int iid = newAuction(op.userId, new AuctionSaleItem(op.name, op.description, op.reservePrice));
                return (iid > 0) ? OperationResult.newA(iid) : OperationResult.fail("unknown user");
            }
            case BID -> {
                boolean ok = bid(op.userId, op.itemId, op.reservePrice);
                return OperationResult.bid(ok);
            }
            case CLOSE -> {
                AuctionResult ar = closeAuction(op.userId, op.itemId); // null if not owner
                if (ar == null) 
                    return OperationResult.fail("Auction close not permitted!");
                return OperationResult.close(ar.itemID, ar.winningUser, ar.price);
            }
            default -> { return OperationResult.fail("Unknown op"); }
        }
    }

    // ================= Sync & helpers =================

    @Override public List<LogEntry> getEntriesAfter(long fromSeq) {
        List<LogEntry> out = new ArrayList<>();
        for (var e : log.tailMap(fromSeq+1).entrySet()) {
            LogEntry le = e.getValue();
            if (le.committed) 
                out.add(new LogEntry(le.seqNo, le.op)); 
        }
        return out;
    }

    private String findLeaderName() throws Exception {
        Registry reg = LocateRegistry.getRegistry();
        frontend.FrontEndAdmin fe = (frontend.FrontEndAdmin) reg.lookup("FrontEnd");
        return fe.getCurrentSequencerName();
    }

    private ReplicatedAuction lookup(String rmiName) throws Exception {
        Registry reg = LocateRegistry.getRegistry();
        return (ReplicatedAuction) reg.lookup(rmiName);
    }

    @Override public long getLastCommittedSeqNo() { 
        return lastCommitted; 
    }

    // You may not use this method but it is here if you need it
    // Used to check if a replica is the leader
    @Override public boolean isSequencer() { 
        return isLeader; 
    }

    // Set the replica as a leader or not (isLeader is true: you are leader, isLeader is false: you are not)
    @Override public void setSequencer(boolean isLeader){ 
        this.isLeader = isLeader; 
        if (isLeader) {
            this.lastSeqAssigned = this.lastCommitted;
        }
    }
}
