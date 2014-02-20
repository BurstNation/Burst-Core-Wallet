package nxt;

import nxt.util.Listener;
import org.json.simple.JSONObject;

import java.util.Collection;
import java.util.List;

public interface TransactionProcessor {

    public static enum Event {
        REMOVED_UNCONFIRMED_TRANSACTIONS,
        ADDED_UNCONFIRMED_TRANSACTIONS,
        ADDED_CONFIRMED_TRANSACTIONS,
        ADDED_DOUBLESPENDING_TRANSACTIONS
    }

    boolean addTransactionListener(Listener<List<Transaction>> listener, Event eventType);

    boolean removeTransactionListener(Listener<List<Transaction>> listener, Event eventType);

    Collection<? extends Transaction> getAllUnconfirmedTransactions();

    Transaction getUnconfirmedTransaction(Long transactionId);

    void broadcast(Transaction transaction);

    void processTransactions(JSONObject request);

    Transaction parseTransaction(byte[] bytes) throws NxtException.ValidationException;

    Transaction newTransaction(int timestamp, short deadline, byte[] senderPublicKey, Long recipientId,
                               int amount, int fee, Long referencedTransactionId) throws NxtException.ValidationException;

    Transaction newTransaction(int timestamp, short deadline, byte[] senderPublicKey, Long recipientId,
                               int amount, int fee, Long referencedTransactionId, Attachment attachment) throws NxtException.ValidationException;

}
