package frontend;

import io.grpc.stub.StreamObserver;
import server.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;

public class FrontEndImpl extends AuctionServiceGrpc.AuctionServiceImplBase {
    private final Auction auction;

    public FrontEndImpl() throws Exception {
        Registry reg = LocateRegistry.getRegistry();
        this.auction = (Auction) reg.lookup("AuctionServer");
    }

    @Override
    public void register(RegisterRequest req, StreamObserver<RegisterReply> resp) {
        try {
            int id = auction.register(req.getEmail());
            resp.onNext(RegisterReply.newBuilder().setUserId(id).build());
            resp.onCompleted();
        } catch (Exception e) { resp.onError(e); }
    }

    @Override
    public void newAuction(NewAuctionRequest req, StreamObserver<NewAuctionReply> resp) {

        // Construct an AuctionSaleItem from the gRPC request fields.
        try {
            AuctionSaleItem i = new AuctionSaleItem(req.getName(), req.getDescription(), req.getReservePrice());
            // Forward newAuction(userId, item) to the RMI Auction server.
            int itemID = auction.newAuction(req.getUserId(), i);
            // Build and return a NewAuctionReply with the created itemId.
            resp.onNext(NewAuctionReply.newBuilder().setItemId(itemID).build());
            resp.onCompleted();
        } catch (Exception e) {
            resp.onError(e);
        }

    }

    @Override
    public void bid(BidRequest req, StreamObserver<BidReply> resp) {
        // TODO:
        // Forward bid(userId, itemId, price) to the RMI Auction server.
        try {
            boolean success = auction.bid(req.getUserId(), req.getItemId(), req.getPrice());
            resp.onNext(BidReply.newBuilder().setSuccess(success).build());
            resp.onCompleted();
        } catch (Exception e) {
            resp.onNext(BidReply.newBuilder().setSuccess(false).build());
            resp.onCompleted();
            
        }
        // Build and return a BidReply with success=true/false.
    }

    @Override
    public void listItems(Empty req, StreamObserver<ListReply> resp) {
        // Call auction.listItems() on the RMI server.
        try {
            AuctionItem[] list = auction.listItems();
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
        }
    }

    @Override
    public void getSpec(GetSpecRequest req, StreamObserver<Item> resp) {
        // TODO:
        // Call auction.getSpec(itemId) on the RMI server.
        try {
            AuctionItem item = auction.getSpec(req.getItemId());
            // If null, return an empty Item message.
            if(item==null)
            {
                // probs doesnt work
                resp.onNext(Item.newBuilder().build());
                resp.onCompleted();
            }
            else{
                // valid
                resp.onNext(Item.newBuilder()
                .setItemId(item.itemID)
                .setName(item.name)
                .setDescription(item.description)
                .setReservePrice(item.reservePrice)
                .setHighestBid(item.highestBid)
                .build());
                resp.onCompleted();
            }
            // Otherwise, map fields to a gRPC Item and return it.
        } catch (Exception e) {
            resp.onNext(Item.newBuilder().setItemId(0).setName("").setDescription("").setReservePrice(0).setHighestBid(0).build())
            resp.onCompleted();
            
        }


    }

    @Override
    public void closeAuction(CloseRequest req, StreamObserver<AuctionResult> resp) {
        // Forward closeAuction(userId, itemId) to the RMI Auction server.
        try {
            server.AuctionResult res = auction.closeAuction(req.getUserId(), req.getItemId());
            if(res==null)
            {
                resp.onNext(AuctionResult.newBuilder().setItemId(0).setWinningUser(0).setPrice(0).build());
                resp.onCompleted();
            }          
            resp.onNext(AuctionResult.newBuilder().setItemId(res.itemID).setWinningUser(res.winningUser).setPrice(res.price).build()); 
            resp.onCompleted();
        } catch (Exception e) {
            resp.onNext(AuctionResult.newBuilder().setItemId(0).setWinningUser(0).setPrice(0).build());
            resp.onCompleted();
        }
        
        // If the result is null (e.g., wrong owner or already closed), return
        // an AuctionResult with zeroed fields.
        // Otherwise, map the AuctionResult fields and return them.
    }
}