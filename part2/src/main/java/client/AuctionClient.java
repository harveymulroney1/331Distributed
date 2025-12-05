package client;

import frontend.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

// A sample client which performs basic checks

public class AuctionClient {
    public static void main(String[] args) {
        ManagedChannel ch = ManagedChannelBuilder.forAddress("localhost", 50055)
                .usePlaintext().build();
        var stub = AuctionServiceGrpc.newBlockingStub(ch);

        // Register three users
        int alice = stub.register(RegisterRequest.newBuilder().setEmail("alice@lancaster.ac.uk").build()).getUserId();
        int bob   = stub.register(RegisterRequest.newBuilder().setEmail("bob@lancaster.ac.uk").build()).getUserId();
        int carol = stub.register(RegisterRequest.newBuilder().setEmail("carol@lancaster.ac.uk").build()).getUserId();
        System.out.printf("Users -> alice=%d bob=%d carol=%d%n", alice, bob, carol);

        // TODO:  make sure to test the functionality before submitting!

        // 1. Start a few auctions using one or more of the registered users.
        //    - Construct and send NewAuctionRequest messages.
        //    - Print returned item IDs.
        int auc1 = stub.newAuction(NewAuctionRequest.newBuilder().setUserId(alice).setName("Monster White").setDescription("Energy Drink").setReservePrice(2).build()).getItemId();
        int auc2 = stub.newAuction(NewAuctionRequest.newBuilder().setUserId(bob).setName("Lenovo Keyboard").setDescription("Qwerty Keys").setReservePrice(45).build()).getItemId();
        int auc3 = stub.newAuction(NewAuctionRequest.newBuilder().setUserId(carol).setName("Mouse").setDescription("Computer Controller").setReservePrice(25).build()).getItemId();  

        //
        // 2. Have multiple users place bids on these items.
        //    - Use BidRequest messages.
        //    - Print whether each bid was accepted or rejected.
        boolean success1 = stub.bid(BidRequest.newBuilder().setItemId(auc1).setUserId(carol).setPrice(5).build()).getSuccess();
        boolean success2 = stub.bid(BidRequest.newBuilder().setItemId(auc2).setUserId(alice).setPrice(56).build()).getSuccess();
        boolean success3 = stub.bid(BidRequest.newBuilder().setItemId(auc3).setUserId(bob).setPrice(28).build()).getSuccess();
        System.out.println("Item1 : " +success1 + "Item2: "+ success2+"Item3: "+success3);
        //
        // 3. Test listing and inspecting items.
        //    - Call listItems() to verify current highest bids and reserve prices.
        //    - Optionally call getSpec() for a specific item.
        //
        ListReply iArr = stub.listItems(Empty.newBuilder().build());
        for (Item i : iArr.getItemsList()) {
            System.out.println(
                "ID=" + i.getItemId() +
                ", Name=" + i.getName() +
                ", HighestBid=" + i.getHighestBid()
            );
        }
        Item i = stub.getSpec(GetSpecRequest.newBuilder().setItemId(auc2).build());
        System.out.println("item2: "+i.getName()+" Price:"+i.getReservePrice());
        // 4. Close an auction.
        //    - Ensure only the creator can close it.
        //    - Print the returned AuctionResult.
        AuctionResult res = stub.closeAuction(CloseRequest.newBuilder().setItemId(auc1).setUserId(alice).build());
        System.out.println(
            "AuctionResult: item=" + res.getItemId() +
            ", winner=" + res.getWinningUser() +
            ", price=" + res.getPrice()
        );
        //
        System.out.println("Trying non existent item now:");

        // 5. Try edge cases:
        //    - Bidding on a non-existent item.
        //    - Bidding below reserve price.
        //    - Closing an auction twice.
        //    - Closing an auction by a non-owner.
        //
        boolean success4 = stub.bid(BidRequest.newBuilder().setItemId(18).setUserId(carol).setPrice(12).build()).getSuccess();
            System.out.println("Non Existent Item: EXP(Null - Failed to Bid) Actual: "+success4);
        //    - Bidding below reserve price.
            System.out.println("Trying below reserve item now:");

            boolean success5 = stub.bid(BidRequest.newBuilder().setItemId(auc2).setUserId(carol).setPrice(5).build()).getSuccess();
            System.out.println("Below Reserve Price: EXP(Error - Failed to Bid - Below Reserve) Actual: "+success5);
        //    - Closing an auction twice.
        AuctionResult res2 = stub.closeAuction(CloseRequest.newBuilder().setItemId(auc1).setUserId(alice).build());
        System.out.println("Close Auction Twice: EXP(Error - Auction already closed) Actual: ");
                System.out.println(
            "AuctionResult: item=" + res2.getItemId() +
            ", winner=" + res2.getWinningUser() +
            ", price=" + res2.getPrice()
            );
        //    - Closing an auction by a non-owner.
        AuctionResult res3 = stub.closeAuction(CloseRequest.newBuilder().setItemId(auc3).setUserId(bob).build());

        //
        // 6. Print a summary of expected vs. actual outcomes for basic validation.
        System.out.println("Close by non owner: EXP(Error - Not owner) Actual: ");
                System.out.println(
            "AuctionResult: item=" + res3.getItemId() +
            ", winner=" + res3.getWinningUser() +
            ", price=" + res3.getPrice()
            );
        // 6. Print a summary of expected vs. actual outcomes for basic validation.

        ch.shutdown();
    }
}
