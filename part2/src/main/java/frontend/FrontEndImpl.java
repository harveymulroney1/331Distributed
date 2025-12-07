package frontend;

import io.grpc.Status;
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
            if(!members.contains(rmiName))
            {
                members.add(rmiName);
            }
            for(String m : members){
                System.out.println("Member: "+m); // needs to already stay in there.
            }
            if(sequencerName==null){
                sequencerName = rmiName;
                sequencer = lookup(rmiName);
                sequencer.setSequencer(true);
            }
        } catch (Exception e) {
            System.out.println("Caught an Error on Register Replica");
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
            resp.onError(Status.UNAVAILABLE.withDescription("Error on Closing Auction").asRuntimeException());
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
            if(reply!=null)
            {
                resp.onNext(reply.build());
                resp.onCompleted();
            }
            else{
                resp.onError(Status.UNAVAILABLE.withDescription("Error on List Items").asRuntimeException());
            }
            // Build and return a ListReply containing all items.
        } catch (Exception e) {
            //resp.onError(Status.UNAVAILABLE.withDescription("Error on List Items").asRuntimeException());
            electNewLeader();
            
        }
        // Handle any errors (you may need to elect a new leader if the current one has crashed) 
        // I suggest you implement leader election in the skeleton method provided below (electNewLeader)
        // NOTE: if you elect a new leader, you have to call listItems on the new leader
    }

    @Override
    public void register(RegisterRequest req, StreamObserver<RegisterReply> resp) {

        // Step 1: Lookup the current sequencer (leader)req
        System.out.println("Registering");
        Operation op = Operation.register(req.getEmail());
        try {
            //ReplicatedAuction leader = sequencer();
            OperationResult res = sequencer.handleClientOperation(op,members);
            if(res.error==null)
            {
                resp.onNext(RegisterReply.newBuilder().setUserId(res.userId).build());
                resp.onCompleted();
            }
            else{
                resp.onError(Status.UNAVAILABLE.withDescription("Error on Registering").asRuntimeException());
            }

        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("Error on registering");
            //resp.onError(Status.UNAVAILABLE.withDescription("Error on Registering").asRuntimeException());
            e.printStackTrace();
            electNewLeader();
            try {
                OperationResult res = sequencer.handleClientOperation(op, members);
                if(res.error==null)
                {
                    resp.onNext(RegisterReply.newBuilder().setUserId(res.userId).build());
                    resp.onCompleted();
                }
                else{
                    resp.onError(Status.UNAVAILABLE.withDescription(res.error).asRuntimeException());
                }
            } catch (Exception ex) {
                // TODO: handle exception
                System.out.println("Retry failed on new leader");
                resp.onError(Status.UNAVAILABLE.withDescription("Error on Registering").asRuntimeException());
            }
            //register(req,resp);
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
        Operation op = Operation.newAuction(req.getUserId(), req.getName(), req.getDescription(), req.getReservePrice());
        try {
            //ReplicatedAuction leader = lookupLeader();
            OperationResult res = sequencer.handleClientOperation(op, members);
            if(res.error==null)
            {
                resp.onNext(NewAuctionReply.newBuilder().setItemId(res.itemId).build());
                resp.onCompleted();
            }
            else{
                resp.onError(Status.UNAVAILABLE.withDescription("Error on Opening Auction").asRuntimeException());
            }
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("Error on new Auction");
            
            e.printStackTrace();
            electNewLeader();
            try {
                OperationResult res = sequencer.handleClientOperation(op, members);
                if(res.error==null)
                {
                    resp.onNext(NewAuctionReply.newBuilder().setItemId(res.itemId).build());
                    resp.onCompleted();
                }
                else{
                    resp.onError(Status.UNAVAILABLE.withDescription(res.error).asRuntimeException());
                }
            } catch (Exception ex) {
                // TODO: handle exception
                System.out.println("Retry failed on new leader");
                resp.onError(Status.UNAVAILABLE.withDescription("Error on Opening Auction").asRuntimeException());
            }
            //newAuction(req,resp);
        }
        // Step 2: Create an Operation object (you can do: op = Operation.newAuction(...))
        // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
        // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
        // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
    }

    @Override
    public void bid(BidRequest req, StreamObserver<BidReply> resp) {
        Operation op = Operation.bid(req.getUserId(),req.getItemId(),req.getPrice());
        try {
            // Step 1: Lookup the current sequencer (leader)
        //ReplicatedAuction leader = lookupLeader();
        // Step 2: Create an Operation object (you can do: op = Operation.bid(req.getUserId(), ...))
        // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
        OperationResult res = sequencer.handleClientOperation(op, members);
        if(res.error==null)
        {
            resp.onNext(BidReply.newBuilder().setSuccess(res.bidOk).build());
            resp.onCompleted();
        }
        else{
            resp.onError(Status.UNAVAILABLE.withDescription("Error on Bidding").asRuntimeException());
        }
        // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
        } catch (Exception e) {
            System.out.println("Error on Bid");
            e.printStackTrace();
            electNewLeader();
            //bid(req,resp);
            try {
                OperationResult res = sequencer.handleClientOperation(op, members);
                if(res.error==null)
                {
                    resp.onNext(BidReply.newBuilder().setSuccess(res.bidOk).build());
                    resp.onCompleted();
                }
            } catch (Exception ex) {
                // TODO: handle exception
                System.out.println("Retry failed on new leader");
                resp.onError(Status.UNAVAILABLE.withDescription("Error on Bidding").asRuntimeException());
            }
        }
        // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
    }

    @Override
    public void closeAuction(CloseRequest req, StreamObserver<AuctionResult> resp) {
        Operation op = Operation.close(req.getUserId(),req.getItemId());
        try {
            // Step 1: Lookup the current sequencer (leader)
            //ReplicatedAuction leader = lookupLeader();
            // Step 2: Create an Operation object (you can do: op = Operation.close(req.getUserId(), ...))
            // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult res = sequencer.handleClientOperation(op, members);
            if(res.error==null)
            {
                resp.onNext(AuctionResult.newBuilder()
                    .setItemId(res.closeItem)
                    .setWinningUser(res.closeWinner)
                    .setPrice(res.closePrice)
                    .build());
                resp.onCompleted();
            }
            else{
                resp.onError(Status.UNAVAILABLE.withDescription("Error on Closing Auction").asRuntimeException());
            }
            // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC   
        } catch (Exception e) {
            // TODO: handle exception
            electNewLeader();
            System.out.println("Error on Close Auction");
            e.printStackTrace();
            try {
                OperationResult res= sequencer.handleClientOperation(op, members);
                if(res.error==null){
                    resp.onNext(AuctionResult.newBuilder()
                        .setItemId(res.closeItem)
                        .setWinningUser(res.closeWinner)
                        .setPrice(res.closePrice)
                        .build());
                    resp.onCompleted();
                }
                else{
                    resp.onError(Status.UNAVAILABLE.withDescription(res.error).asRuntimeException());
                }
            } catch (Exception ex) {
                // TODO: handle exception
                System.out.println("Retry failed on new leader");
                resp.onError(Status.UNAVAILABLE.withDescription("Error on Closing Auction").asRuntimeException());

            }
            //closeAuction(req, resp);
        }
       
        // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)

    }

    // I suggest implementing leader election in this method and calling from other methods when needed
    private synchronized void electNewLeader() {
        //TODO:
        // probe all members, pick the replica that reports the highest lastCommitted (if tie → pick any)

       
            long highest = -1; // last commited
            String highestUser = "";
            for(String user : members)
            {
                try {
                    ReplicatedAuction auc = lookup(user);
                    if(auc.getLastCommittedSeqNo()>highest){
                        highestUser = user;
                        highest = auc.getLastCommittedSeqNo();
                    }
                    auc.setSequencer(false);
                } catch (Exception e) {
                    System.out.println("Replica " + user + " unreachable during election");
                    continue;
                }
            }
            if (highestUser == "" || highestUser == null) {
                System.out.println("No reachable replicas cant elect leader");
            }
            try {
                ReplicatedAuction newLeader = lookup(highestUser);
                newLeader.setSequencer(true);
                sequencer = newLeader;
                sequencerName = highestUser;
                System.out.println("Elected new sequencer: " + sequencerName);
            } catch (Exception e) {
                System.out.println("Failed to promote: "+highestUser);
                e.printStackTrace();
                // TODO: handle exception
            }


        // Call setSequencer(true) on the selected replica (optionally call setSequencer(false) on the others)

        
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
