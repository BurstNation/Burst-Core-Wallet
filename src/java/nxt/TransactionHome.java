/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import nxt.db.Table;
import nxt.util.Convert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO: all queries over multiple tables should be replaced with a single id lookup table
final class TransactionHome {

    private static final Map<Chain, TransactionHome> transactionHomeMap = new HashMap<>();

    public static TransactionHome forChain(Chain chain) {
        return transactionHomeMap.get(chain);
    }

    static void init() {}

    static {
        ChildChain.getAll().forEach(childChain -> transactionHomeMap.put(childChain, new TransactionHome(childChain)));
        transactionHomeMap.put(FxtChain.FXT, new TransactionHome(FxtChain.FXT));
    }

    private final Chain chain;
    private final Table transactionTable;

    private TransactionHome(ChildChain chain) {
        this.chain = chain;
        transactionTable = new Table(chain.getSchemaTable("transaction"));
    }

    private TransactionHome(FxtChain chain) {
        this.chain = chain;
        transactionTable = new Table(chain.getSchemaTable("transaction_fxt"));
    }

    static TransactionImpl findTransaction(long transactionId) {
        return TransactionHome.findTransaction(transactionId, Integer.MAX_VALUE);
    }

    static TransactionImpl findTransaction(long transactionId, int height) {
        // Check the block cache
        synchronized (BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return transaction.getHeight() <= height ? transaction : null;
            }
        }
        //TODO: use global id map table
        for (TransactionHome transactionHome : transactionHomeMap.values()) {
            Table transactionTable = transactionHome.transactionTable;
            // Search the database
            try (Connection con = transactionTable.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
                pstmt.setLong(1, transactionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt("height") <= height) {
                        return loadTransaction(transactionHome.chain, con, rs);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            } catch (NxtException.ValidationException e) {
                throw new RuntimeException("Transaction already in database, id = " + transactionId + ", does not pass validation!", e);
            }
        }
        return null;
    }

    TransactionImpl findChainTransaction(long transactionId) {
        return findChainTransaction(transactionId, Integer.MAX_VALUE);
    }

    TransactionImpl findChainTransaction(long transactionId, int height) {
        // Check the block cache
        synchronized (BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return transaction.getHeight() <= height ? transaction : null;
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && rs.getInt("height") <= height) {
                    return loadTransaction(chain, con, rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } catch (NxtException.ValidationException e) {
            throw new RuntimeException("Transaction already in database, id = " + transactionId + ", does not pass validation!", e);
        }
    }

    static TransactionImpl findTransactionByFullHash(byte[] fullHash) {
        return TransactionHome.findTransactionByFullHash(fullHash, Integer.MAX_VALUE);
    }

    static TransactionImpl findTransactionByFullHash(byte[] fullHash, int height) {
        long transactionId = Convert.fullHashToId(fullHash);
        // Check the cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height &&
                        Arrays.equals(transaction.fullHash(), fullHash) ? transaction : null);
            }
        }
        //TODO: use global id map table
        for (TransactionHome transactionHome : transactionHomeMap.values()) {
            Table transactionTable = transactionHome.transactionTable;
            // Search the database
            try (Connection con = transactionTable.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
                pstmt.setLong(1, transactionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && Arrays.equals(rs.getBytes("full_hash"), fullHash) && rs.getInt("height") <= height) {
                        return loadTransaction(transactionHome.chain, con, rs);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            } catch (NxtException.ValidationException e) {
                throw new RuntimeException("Transaction already in database, full_hash = " + Convert.toHexString(fullHash)
                        + ", does not pass validation!", e);
            }
        }
        return null;
    }

    TransactionImpl findChainTransactionByFullHash(byte[] fullHash) {
        return findChainTransactionByFullHash(fullHash, Integer.MAX_VALUE);
    }

    TransactionImpl findChainTransactionByFullHash(byte[] fullHash, int height) {
        long transactionId = Convert.fullHashToId(fullHash);
        // Check the cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height &&
                        Arrays.equals(transaction.fullHash(), fullHash) ? transaction : null);
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && Arrays.equals(rs.getBytes("full_hash"), fullHash) && rs.getInt("height") <= height) {
                    return loadTransaction(chain, con, rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } catch (NxtException.ValidationException e) {
            throw new RuntimeException("Transaction already in database, full_hash = " + Convert.toHexString(fullHash)
                    + ", does not pass validation!", e);
        }
    }

    static boolean hasTransaction(long transactionId) {
        return TransactionHome.hasTransaction(transactionId, Integer.MAX_VALUE);
    }

    static boolean hasTransaction(long transactionId, int height) {
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height);
            }
        }
        //TODO: use global id map table
        for (TransactionHome transactionHome : transactionHomeMap.values()) {
            Table transactionTable = transactionHome.transactionTable;
            // Search the database
            try (Connection con = transactionTable.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT height FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
                pstmt.setLong(1, transactionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt("height") <= height) {
                        return true;
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
        return false;
    }

    boolean hasChainTransaction(long transactionId) {
        return hasChainTransaction(transactionId, Integer.MAX_VALUE);
    }

    boolean hasChainTransaction(long transactionId, int height) {
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height);
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT height FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt("height") <= height;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static boolean hasTransactionByFullHash(byte[] fullHash) {
        return hasTransactionByFullHash(fullHash, Integer.MAX_VALUE);
    }

    static boolean hasTransactionByFullHash(byte[] fullHash, int height) {
        long transactionId = Convert.fullHashToId(fullHash);
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height &&
                        Arrays.equals(transaction.fullHash(), fullHash));
            }
        }
        //TODO: use global id map table
        for (TransactionHome transactionHome : transactionHomeMap.values()) {
            Table transactionTable = transactionHome.transactionTable;
            // Search the database
            try (Connection con = transactionTable.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT full_hash, height FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
                pstmt.setLong(1, transactionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && Arrays.equals(rs.getBytes("full_hash"), fullHash) && rs.getInt("height") <= height) {
                        return true;
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
        return false;
    }

    boolean hasChainTransactionByFullHash(byte[] fullHash) {
        return hasChainTransactionByFullHash(fullHash, Integer.MAX_VALUE);
    }

    boolean hasChainTransactionByFullHash(byte[] fullHash, int height) {
        long transactionId = Convert.fullHashToId(fullHash);
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return (transaction.getHeight() <= height &&
                        Arrays.equals(transaction.fullHash(), fullHash));
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT full_hash, height FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && Arrays.equals(rs.getBytes("full_hash"), fullHash) && rs.getInt("height") <= height;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static byte[] getTransactionFullHash(long transactionId) {
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return transaction.fullHash();
            }
        }
        for (TransactionHome transactionHome : transactionHomeMap.values()) {
            Table transactionTable = transactionHome.transactionTable;
            // Search the database
            try (Connection con = transactionTable.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT full_hash FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
                pstmt.setLong(1, transactionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBytes("full_hash");
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
        return null;
    }

    byte[] getChainTransactionFullHash(long transactionId) {
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            TransactionImpl transaction = BlockDb.transactionCache.get(transactionId);
            if (transaction != null) {
                return transaction.fullHash();
            }
        }
        // Search the database
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT full_hash FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getBytes("full_hash") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static TransactionImpl loadTransaction(Chain chain, Connection con, ResultSet rs) throws NxtException.NotValidException {
        if (chain == FxtChain.FXT) {
            return FxtTransactionImpl.loadTransaction(con, rs);
        } else {
            return ChildTransactionImpl.loadTransaction((ChildChain)chain, con, rs);
        }
    }

    static List<TransactionImpl> findBlockTransactions(long blockId) {
        // Check the block cache
        synchronized(BlockDb.blockCache) {
            BlockImpl block = BlockDb.blockCache.get(blockId);
            if (block != null) {
                return block.getTransactions();
            }
        }
        // Search the database
        try (Connection con = Db.getConnection()) {
            return findBlockTransactions(con, blockId);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static List<TransactionImpl> findBlockTransactions(Connection con, long blockId) {
        //TODO: after implementing ChildchainBlock transactions, get FXT transactions from fxt chain,
        // then for each ChildchainBlock attachment look for its transactions in its child chain only
        List<TransactionImpl> list = new ArrayList<>();
        for (TransactionHome transactionHome : transactionHomeMap.values()) {
            Table transactionTable = transactionHome.transactionTable;
            try (PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + transactionTable.getSchemaTable()
                    + " WHERE block_id = ? ORDER BY transaction_index")) {
                pstmt.setLong(1, blockId);
                pstmt.setFetchSize(50);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(loadTransaction(transactionHome.chain, con, rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            } catch (NxtException.ValidationException e) {
                throw new RuntimeException("Transaction already in database for block_id = " + Long.toUnsignedString(blockId)
                        + " does not pass validation!", e);
            }
        }
        Collections.sort(list, Comparator.comparingInt(TransactionImpl::getIndex));
        return list;
    }

    List<PrunableTransaction> findPrunableTransactions(Connection con, int minTimestamp, int maxTimestamp) {
        List<PrunableTransaction> result = new ArrayList<>();
        try (PreparedStatement pstmt = con.prepareStatement("SELECT id, type, subtype, "
                + "has_prunable_attachment AS prunable_attachment, "
                + "has_prunable_message AS prunable_plain_message, "
                + "has_prunable_encrypted_message AS prunable_encrypted_message "
                + "FROM " + transactionTable.getSchemaTable() + " WHERE (timestamp BETWEEN ? AND ?) AND "
                + "(has_prunable_attachment = TRUE OR has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE)")) {
            pstmt.setInt(1, minTimestamp);
            pstmt.setInt(2, maxTimestamp);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    byte type = rs.getByte("type");
                    byte subtype = rs.getByte("subtype");
                    TransactionType transactionType = ChildTransactionType.findTransactionType(type, subtype);
                    result.add(new PrunableTransaction(id, transactionType,
                            rs.getBoolean("prunable_attachment"),
                            rs.getBoolean("prunable_plain_message"),
                            rs.getBoolean("prunable_encrypted_message")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    static void saveTransactions(Connection con, List<TransactionImpl> transactions) {
        try {
            short index = 0;
            for (TransactionImpl transaction : transactions) {
                transaction.setIndex(index++);
                TransactionHome transactionHome = transactionHomeMap.get(transaction.getChain());
                transaction.save(con, transactionHome.transactionTable.getSchemaTable());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static class PrunableTransaction {
        private final long id;
        private final TransactionType transactionType;
        private final boolean prunableAttachment;
        private final boolean prunablePlainMessage;
        private final boolean prunableEncryptedMessage;

        public PrunableTransaction(long id, TransactionType transactionType, boolean prunableAttachment,
                                   boolean prunablePlainMessage, boolean prunableEncryptedMessage) {
            this.id = id;
            this.transactionType = transactionType;
            this.prunableAttachment = prunableAttachment;
            this.prunablePlainMessage = prunablePlainMessage;
            this.prunableEncryptedMessage = prunableEncryptedMessage;
        }

        public long getId() {
            return id;
        }

        public TransactionType getTransactionType() {
            return transactionType;
        }

        public boolean hasPrunableAttachment() {
            return prunableAttachment;
        }

        public boolean hasPrunablePlainMessage() {
            return prunablePlainMessage;
        }

        public boolean hasPrunableEncryptedMessage() {
            return prunableEncryptedMessage;
        }
    }

}
