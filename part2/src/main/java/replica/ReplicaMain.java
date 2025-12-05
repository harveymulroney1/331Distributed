package replica;

import common.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import frontend.FrontEndAdmin;

public class ReplicaMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ReplicaMain <id>");
            System.exit(1);
        }
        int id = Integer.parseInt(args[0]);
        String name = "replica" + id;

        // Register the replica with rmiregistry
        ReplicatedAuction r = new ReplicaImpl(id, name);
        Registry reg = LocateRegistry.getRegistry();
        reg.rebind(name, r);
        System.out.println("Replica " + id + " bound as " + name);
        FrontEndAdmin fe = (FrontEndAdmin) reg.lookup("FrontEnd");
        String leaderStr = fe.getCurrentSequencerName(); // leader
        //TODO (suggested high-level steps):
        // Step 1: Ask the front-end to find out who the current sequencer (leader) is. If the answer is null (no leader), then register with the front-end (registerReplica)
        
        //String leaderStr = fe.getCurrentSequencerName();
        if(leaderStr == null)
        {
            fe.registerReplica(id,name);
            System.out.println("Registered as first replica (leader)");
        }
        else{
            System.out.println("Leader looking up:"+leaderStr);
            ReplicatedAuction leader = (ReplicatedAuction) reg.lookup(leaderStr); // RepAuc to use it.
            // Step 2: Retrieve any missing committed log entries from the leader 
            System.out.println("Looked up leader.");
            //ReplicatedAuction sequencer = r.lookup(leaderStr);
            
            //List<LogEntry> missingEntries = leader.getEntriesAfter(r.getLastCommittedSeqNo()); // gets missing entries
            long leaderSeqNo = leader.getLastCommittedSeqNo();
            System.out.println("(DEBUG) Leader Seq Number Fetched");
            System.out.println(leaderSeqNo);
            System.out.println("(DEBUG) Calling commit up to now");

            r.commitUpTo(leaderSeqNo); // collect, execute & commit missing operations -> up to leaders highest commited seq. S2 and S3
            // Step 3: Locally execute any new committed (previously missing) operations that were added to the log in the previous step
           /*  for(LogEntry le : missingEntries){
                r.apply(le.op);
                r.lastApplied=le.seqNo;
                r.lastCommitted=le.seqNo;
            } */
            // Step 4: Now that the replica is ready to serve requests, register with the front-end (front-end maintains replica membership) 
            System.out.println("Registering Replica after committing");
            fe.registerReplica(id,name);
        }
        
        
        //NOTE: you may skip steps 1--3 and only do step 4 in which case criterion 2.3 will not be satisfied

    }
}
