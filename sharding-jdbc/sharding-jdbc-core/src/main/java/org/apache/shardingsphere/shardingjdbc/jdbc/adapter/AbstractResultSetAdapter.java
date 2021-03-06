/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingjdbc.jdbc.adapter;

import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.sharding.execute.context.ShardingExecutionContext;
import org.apache.shardingsphere.shardingjdbc.jdbc.adapter.executor.ForceExecuteCallback;
import org.apache.shardingsphere.shardingjdbc.jdbc.adapter.executor.ForceExecuteTemplate;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.resultset.ShardingResultSetMetaData;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.statement.ShardingPreparedStatement;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.statement.ShardingStatement;
import org.apache.shardingsphere.shardingjdbc.jdbc.unsupported.AbstractUnsupportedOperationResultSet;
import org.apache.shardingsphere.underlying.common.constant.properties.PropertiesConstant;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for {@code ResultSet}.
 */
public abstract class AbstractResultSetAdapter extends AbstractUnsupportedOperationResultSet {
    
    @Getter
    private final List<ResultSet> resultSets;
    
    @Getter
    private final Statement statement;
    
    private boolean closed;
    
    private final ForceExecuteTemplate<ResultSet> forceExecuteTemplate = new ForceExecuteTemplate<>();
    
    @Getter
    private final ShardingExecutionContext shardingExecutionContext;
    
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, String> logicAndActualColumns; 
    
    public AbstractResultSetAdapter(final List<ResultSet> resultSets, final Statement statement, final ShardingExecutionContext shardingExecutionContext) {
        Preconditions.checkArgument(!resultSets.isEmpty());
        this.resultSets = resultSets;
        this.statement = statement;
        this.shardingExecutionContext = shardingExecutionContext;
        logicAndActualColumns = createLogicAndActualColumns();
    }
    
    @Override
    public final ResultSetMetaData getMetaData() throws SQLException {
        return new ShardingResultSetMetaData(resultSets.get(0).getMetaData(), getShardingRule(), shardingExecutionContext.getSqlStatementContext(), logicAndActualColumns);
    }
    
    private Map<String, String> createLogicAndActualColumns() {
        return isQueryWithCipherColumn() ? createLogicAndCipherColumns() : createLogicAndPlainColumns();
    }
    
    private Map<String, String> createLogicAndCipherColumns() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String each : shardingExecutionContext.getSqlStatementContext().getTablesContext().getTableNames()) {
            result.putAll(getShardingRule().getEncryptRule().getLogicAndCipherColumns(each));
        }
        return result;
    }
    
    private Map<String, String> createLogicAndPlainColumns() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String each : shardingExecutionContext.getSqlStatementContext().getTablesContext().getTableNames()) {
            result.putAll(getShardingRule().getEncryptRule().getLogicAndPlainColumns(each));
        }
        return result;
    }
    
    private ShardingRule getShardingRule() {
        return statement instanceof ShardingPreparedStatement 
                ? ((ShardingPreparedStatement) statement).getConnection().getRuntimeContext().getRule() 
                : ((ShardingStatement) statement).getConnection().getRuntimeContext().getRule();
    }
    
    private boolean isQueryWithCipherColumn() {
        return statement instanceof ShardingPreparedStatement
                ? ((ShardingPreparedStatement) statement).getConnection().getRuntimeContext().getProperties().<Boolean>getValue(PropertiesConstant.QUERY_WITH_CIPHER_COLUMN)
                : ((ShardingStatement) statement).getConnection().getRuntimeContext().getProperties().<Boolean>getValue(PropertiesConstant.QUERY_WITH_CIPHER_COLUMN);
    }
    
    @Override
    public final int findColumn(final String columnLabel) throws SQLException {
        return resultSets.get(0).findColumn(getActualColumnLabel(columnLabel));
    }
    
    @Override
    public final void close() throws SQLException {
        closed = true;
        forceExecuteTemplate.execute(resultSets, new ForceExecuteCallback<ResultSet>() {
            
            @Override
            public void execute(final ResultSet resultSet) throws SQLException {
                resultSet.close();
            }
        });
    }
    
    @Override
    public final boolean isClosed() {
        return closed;
    }
    
    @Override
    public final void setFetchDirection(final int direction) throws SQLException {
        forceExecuteTemplate.execute(resultSets, new ForceExecuteCallback<ResultSet>() {
            
            @Override
            public void execute(final ResultSet resultSet) throws SQLException {
                resultSet.setFetchDirection(direction);
            }
        });
    }
    
    @Override
    public final int getFetchDirection() throws SQLException {
        return resultSets.get(0).getFetchDirection();
    }
    
    @Override
    public final void setFetchSize(final int rows) throws SQLException {
        forceExecuteTemplate.execute(resultSets, new ForceExecuteCallback<ResultSet>() {
            
            @Override
            public void execute(final ResultSet resultSet) throws SQLException {
                resultSet.setFetchSize(rows);
            }
        });
    }
    
    @Override
    public final int getFetchSize() throws SQLException {
        return resultSets.get(0).getFetchSize();
    }
    
    @Override
    public final int getType() throws SQLException {
        return resultSets.get(0).getType();
    }
    
    @Override
    public final int getConcurrency() throws SQLException {
        return resultSets.get(0).getConcurrency();
    }
    
    @Override
    public final SQLWarning getWarnings() throws SQLException {
        return resultSets.get(0).getWarnings();
    }
    
    @Override
    public final void clearWarnings() throws SQLException {
        forceExecuteTemplate.execute(resultSets, new ForceExecuteCallback<ResultSet>() {
            
            @Override
            public void execute(final ResultSet resultSet) throws SQLException {
                resultSet.clearWarnings();
            }
        });
    }
    
    protected final String getActualColumnLabel(final String columnLabel) {
        return getLogicAndActualColumns().keySet().contains(columnLabel) ? getLogicAndActualColumns().get(columnLabel) : columnLabel;
    }
}
