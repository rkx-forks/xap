package com.j_spaces.jdbc.executor;

import com.gigaspaces.async.AsyncFuture;
import com.gigaspaces.internal.client.spaceproxy.ISpaceProxy;
import com.gigaspaces.internal.client.spaceproxy.executors.CollocatedJoinSpaceTask;
import com.gigaspaces.internal.space.responses.CollocatedJoinSpaceResponseInfo;
import com.gigaspaces.internal.transport.IEntryPacket;
import com.j_spaces.jdbc.AbstractDMLQuery;
import com.j_spaces.jdbc.parser.*;
import com.j_spaces.jdbc.query.IQueryResultSet;
import net.jini.core.transaction.Transaction;

import java.sql.SQLException;

/**
 * @author yohanakh
 * @since 15.8.0
 */
@com.gigaspaces.api.InternalApi
public class CollocatedJoinedQueryExecutor extends AbstractQueryExecutor {
    private static final long serialVersionUID = 1L;

    // Required for Externalizable
    public CollocatedJoinedQueryExecutor() {
    }

    public CollocatedJoinedQueryExecutor(AbstractDMLQuery query) {
        super(query);
    }

    public IQueryResultSet<IEntryPacket> execute(ISpaceProxy space, Transaction txn, int readModifier, int max)
            throws SQLException {
        try {
            AsyncFuture<CollocatedJoinSpaceResponseInfo> res = space.execute(new CollocatedJoinSpaceTask(query, txn, readModifier, max), null, null, null);
            return res.get().getResult();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute collocated join task", e);
        }
    }

    @Override
    public void execute(OrNode exp, ISpaceProxy space, Transaction txn, int readModifier, int max) throws SQLException {
        throw new UnsupportedOperationException("execute on OrNode");
    }

    @Override
    public void execute(AndNode exp, ISpaceProxy space, Transaction txn, int readModifier, int max) throws SQLException {
        throw new UnsupportedOperationException("execute on AndNode");

    }

    @Override
    public void execute(InNode exp, ISpaceProxy space, Transaction txn, int readModifier, int max) throws SQLException {
        throw new UnsupportedOperationException("execute on InNode");
    }

    @Override
    public void execute(NotInNode exp, ISpaceProxy space, Transaction txn, int readModifier, int max) throws SQLException {
        throw new UnsupportedOperationException("execute on NotInNode");
    }

    @Override
    public void execute(ExpNode equalNode, ISpaceProxy space, Transaction txn, int readModifier, int max) throws SQLException {
        throw new UnsupportedOperationException("execute on ExpNode");

    }
}
