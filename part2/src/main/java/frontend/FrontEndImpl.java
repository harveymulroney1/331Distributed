package frontend;

import io.grpc.stub.StreamObserver;
import common.*;
import replica.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.rmi.RemoteException;

public class FrontEndImpl extends AuctionServiceGrpc.AuctionServiceImplBase implements FrontEndAdmin {


    //TODO:
    // Add state variables
    private volatile String sequencerName = null;
    //private List<String> members;
    private List<String> members = new ArrayList<>();
    private ReplicatedAuction sequencer;
    // === FrontEndAdmin ===
    @Override 
    public String getCurrentSequencerName() throws RemoteException 
    { 
        return sequencerName; 
    }

    @Override 
    public void registerReplica(int id, String rmiName) throws RemoteException {
        //TODO:
        // Add the new member to the list of members
        // If no sequencer (leader) assigned, make the first one to register the sequencer by calling setSequencer(true) on the replica
        try {
            members.add(rmiName);
            if(sequencerName==null){
                sequencerName = rmiName;
                sequencer = lookup(rmiName);
                sequencer.setSequencer(true);
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
        System.out.println("Registered replica " + rmiName + "; leader=" + sequencerName);
    }

    @Override
    public void getSpec(GetSpecRequest req, StreamObserver<Item> resp) {
        //TODO:
        // Call getSpec directly on the current sequencer
        try {
            AuctionItem item = sequencer.getSpec(req.getItemId());
            if(item==null)
            {
                resp.onNext(Item.newBuilder().build());
                resp.onCompleted();
            }
            else{
                resp.onNext(Item.newBuilder()
                .setItemId(item.itemID)
                .setName(item.name)
                .setDescription(item.description)
                .setReservePrice(item.reservePrice)
                .setHighestBid(item.highestBid)
                .build());
                resp.onCompleted();
            }
        } catch (Exception e) {
            // TODO: handle exception
            resp.onError(e);
            // if crashed
            electNewLeader();
            getSpec(req, resp); // to call on new leader
        }
        // Handle any errors (you may need to elect a new leader if the current one has crashed) 
        // I suggest you implement leader election in the skeleton method provided below (electNewLeader)
        // NOTE: if you elect a new leader, you have to call getSpec on the new leader
    }


    // ===== gRPC: READS (direct to leader's Auction API) =====
    @Override
    public void listItems(Empty req, StreamObserver<ListReply> resp) {
        //TODO:
        //Call listItems on the current sequencer
        try {
            AuctionItem[] list = sequencer.listItems();
            // Map each AuctionItem to the gRPC Item message.
            ListReply.Builder reply = ListReply.newBuilder();
            for(AuctionItem item : list)
            {
                Item i = Item.newBuilder()
                .setItemId(item.itemID)
                .setName(item.name)
                .setDescription(item.description)
                .setReservePrice(item.reservePrice)
                .setHighestBid(item.highestBid)
                .build();
                reply.addItems(i);
            }
            resp.onNext(reply.build());
            resp.onCompleted();
            // Build and return a ListReply containing all items.
        } catch (Exception e) {
            resp.onError(e);
            electNewLeader();
            listItems(req, resp);
        }
        // Handle any errors (you may need to elect a new leader if the current one has crashed) 
        // I suggest you implement leader election in the skeleton method provided below (electNewLeader)
        // NOTE: if you elect a new leader, you have to call listItems on the new leader
    }

    @Override
    public void register(RegisterRequest req, StreamObserver<RegisterReply> resp) {

        // Step 1: Lookup the current sequencer (leader)req
        try {
            ReplicatedAuction leader = lookupLeader();
            Operation op = Operation.register(req.getEmail());
            OperationResult res = leader.handleClientOperation(op,members);
            resp.onNext(RegisterReply.newBuilder().setUserId(res.userId).build());
            resp.onCompleted();
        } catch (Exception e) {
            // TODO: handle exception
            
            electNewLeader();
            register(req,resp);
        }
        // Step 2: Create an Operation object (you can do: op = Operation.register(req.getEmail()))
        // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
        // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
        // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
    }

    // ===== gRPC: State-mutating calls =====
    @Override
    public void newAuction(NewAuctionRequest req, StreamObserver<NewAuctionReply> resp) {
        // Step 1: Lookup the current sequencer (leader)
        try {
            ReplicatedAuction leader = lookupLeader();
            Operation op = Operation.newAuction(req.getUserId(), req.getName(), req.getDescription(), req.getReservePrice());
            OperationResult res = leader.handleClientOperation(op, members);
            resp.onNext(NewAuctionReply.newBuilder().setItemId(res.itemId).build());
            resp.onCompleted();
        } catch (Exception e) {
            // TODO: handle exception
            electNewLeader();
            newAuction(req,resp);
        }
        // Step 2: Create an Operation object (you can do: op = Operation.newAuction(...))
        // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
        // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
        // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
    }

    @Override
    public void bid(BidRequest req, StreamObserver<BidReply> resp) {
        try {
            // Step 1: Lookup the current sequencer (leader)
        ReplicatedAuction leader = lookupLeader();
        // Step 2: Create an Operation object (you can do: op = Operation.bid(req.getUserId(), ...))
        Operation op = Operation.bid(req.getUserId(),req.getItemId(),req.getPrice());
        // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
        OperationResult res = leader.handleClientOperation(op, members);
        resp.onNext(BidReply.newBuilder().setSuccess(res.bidOk).build());
        resp.onCompleted();
        // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
        } catch (Exception e) {
            electNewLeader();
            bid(req,resp);
        }
        // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
    }

    @Override
    public void closeAuction(CloseRequest req, StreamObserver<AuctionResult> resp) {
        try {
            // Step 1: Lookup the current sequencer (leader)
            ReplicatedAuction leader = lookupLeader();
            // Step 2: Create an Operation object (you can do: op = Operation.close(req.getUserId(), ...))
            Operation op = Operation.close(req.getUserId(),req.getItemId());
            // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult res = leader.handleClientOperation(op, members);
            resp.onNext(AuctionResult.newBuilder()
            .setItemId(res.closeItem)
            .setWinningUser(res.closeWinner)
            .setPrice(res.closePrice)
            .build());
            resp.onCompleted();
            // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC   
            
        } catch (Exception e) {
            // TODO: handle exception
            electNewLeader();
            closeAuction(req, resp);
        }
       
        // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)

    }

    // I suggest implementing leader election in this method and calling from other methods when needed
    private synchronized void electNewLeader() {
        //TODO:
        // probe all members, pick the replica that reports the highest lastCommitted (if tie → pick any)

        try {
            long highest = 0; // last commited
            String highestUser = "";
            for(String user : members)
            {
                ReplicatedAuction auc = lookup(user);
                if(auc.getLastCommittedSeqNo()>highest){
                    highestUser = user;
                    highest = auc.getLastCommittedSeqNo();
                }
                auc.setSequencer(false);
            }
            ReplicatedAuction newLeader = lookup(highestUser);
            newLeader.setSequencer(true);
            sequencer = newLeader;
            sequencerName = highestUser;
            
        } catch (Exception e) {
            // TODO: handle exception
        }

        // Call setSequencer(true) on the selected replica (optionally call setSequencer(false) on the others)

        System.out.println("Elected new sequencer: " + sequencerName);
    }
    
    // ===== Helpers that may be useful =====
    
    // Looks up and returns a remote reference to the specified replica in the local RMI registry.
    private ReplicatedAuction lookup(String rmiName) throws Exception {
        Registry reg = LocateRegistry.getRegistry();
        return (ReplicatedAuction) reg.lookup(rmiName);
    }

    // Looks up and returns a remote reference to the current sequencer
    private ReplicatedAuction lookupLeader() throws Exception {
        if (sequencerName == null) throw new IllegalStateException("No sequencer set");
        return lookup(sequencerName);
    }

}
