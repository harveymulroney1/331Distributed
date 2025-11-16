package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AuctionImpl extends UnicastRemoteObject implements Auction {
    // TODO declare (thread-safe) state variables to keep track of users,items,auction owners, etc.
    AtomicInteger itemID;
    AtomicInteger userID;
    ConcurrentHashMap<Integer,String> users;
    ConcurrentHashMap<Integer,AuctionItem> items;
    ConcurrentHashMap<Integer,AuctionItem> closedItems;
    ConcurrentHashMap<Integer,Integer> itemOwners;
    
    public AuctionImpl() throws RemoteException { 
        super();
        // TODO: initialise state variables
        users = new ConcurrentHashMap<Integer,String>();
        itemID = new AtomicInteger();
        userID = new AtomicInteger();
        items = new ConcurrentHashMap<Integer,AuctionItem>();
        itemOwners = new ConcurrentHashMap<Integer,Integer>();
        closedItems = new ConcurrentHashMap<Integer,AuctionItem>();

    }

    @Override
    public synchronized int register(String email) {
        // TODO:
        // - Allocate a new userID
        int UID = userID.incrementAndGet();
        // - Record mapping userID -> email
        users.put(UID,email);
        return UID;
        // - Return the new userID
        
    }

    @Override
    public synchronized int newAuction(int userID, AuctionSaleItem item) {
        // TODO:
        // - If userID not registered, return -1 
        if(!users.containsKey(userID)) { return -1;}
        // - Create a new itemID 
        int ID = itemID.incrementAndGet();
        // - Store AuctionItem with initial highestBid = 0
        AuctionItem a = new AuctionItem(ID,item.name,item.description,item.reservePrice);
        items.put(ID,a);
        // - Record itemOwner[itemID] = userID
        itemOwners.put(ID,userID);
        // - Return itemID
        return ID; 
    }

    @Override
    public synchronized AuctionItem getSpec(int itemID) {
        // TODO:
        // - Return the AuctionItem for itemID, or null if not found
        
        return items.get(itemID);
        
    }

    @Override
    public synchronized AuctionItem[] listItems() {
        // TODO:
        // - Return all currently active items (do not return items from closed auctions).
        return items.values().toArray(new AuctionItem[0]);
        
    }

    @Override
    public synchronized boolean bid(int userID, int itemID, int price) {
        // TODO:
        // - If item missing OR user unknown OR item already closed -> return false.
        if(!items.containsKey(itemID) || !users.containsKey(userID) || closedItems.containsKey(itemID))
        {
            AuctionItem i = items.get(itemID);
            if(price>i.highestBid && price>=i.reservePrice){
                i.highestBid = price;
                i.highestBidder = userID;
                return true;
            }
        }
        // - If price > current highestBid AND price >= reservePrice:
        //     - Update highestBid and return true.
        // - Otherwise, return false to indicate unsuccessful bid.
        return false;
    }

    @Override
    public synchronized AuctionResult closeAuction(int userID, int itemID) {
        // TODO:
        // - Look up item; if missing, return null.
        if(!items.containsKey(itemID)){
            return null;
        }
        // - Check owner: only creator can close; if not owner, return null.
        AuctionItem i = items.get(itemID);
        if(itemOwners.get(itemID)==userID)
        {
            return new AuctionResult(itemID, i.highestBidder, i.highestBid);
        }
        // - Mark item as closed (add to closedItems) and remove from active items map.
        // - Return AuctionResult(itemID, winningUser=userID, price=highestBid).
        return null;
    }
}
