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

package org.apache.shardingsphere.sharding.route.engine.type.broadcast;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.rule.DataNode;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.core.rule.TableRule;
import org.apache.shardingsphere.sharding.route.engine.type.ShardingRouteEngine;
import org.apache.shardingsphere.sql.parser.relation.statement.SQLStatementContext;
import org.apache.shardingsphere.sql.parser.sql.segment.ddl.index.IndexSegment;
import org.apache.shardingsphere.sql.parser.sql.statement.ddl.DropIndexStatement;
import org.apache.shardingsphere.underlying.common.metadata.table.TableMetas;
import org.apache.shardingsphere.underlying.route.context.RouteResult;
import org.apache.shardingsphere.underlying.route.context.RouteUnit;
import org.apache.shardingsphere.underlying.route.context.TableUnit;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Sharding broadcast routing engine for tables.
 */
@RequiredArgsConstructor
public final class ShardingTableBroadcastRoutingEngine implements ShardingRouteEngine {
    
    private final TableMetas tableMetas;
    
    private final SQLStatementContext sqlStatementContext;
    
    @Override
    public RouteResult route(final ShardingRule shardingRule) {
        RouteResult result = new RouteResult();
        for (String each : getLogicTableNames()) {
            result.getRouteUnits().addAll(getAllRouteUnits(shardingRule, each));
        }
        return result;
    }
    
    private Collection<String> getLogicTableNames() {
        return sqlStatementContext.getSqlStatement() instanceof DropIndexStatement && !((DropIndexStatement) sqlStatementContext.getSqlStatement()).getIndexes().isEmpty()
                ? getTableNamesFromMetaData((DropIndexStatement) sqlStatementContext.getSqlStatement()) : sqlStatementContext.getTablesContext().getTableNames();
    }
    
    private Collection<String> getTableNamesFromMetaData(final DropIndexStatement dropIndexStatement) {
        Collection<String> result = new LinkedList<>();
        for (IndexSegment each : dropIndexStatement.getIndexes()) {
            Optional<String> tableName = findLogicTableNameFromMetaData(each.getIdentifier().getValue());
            Preconditions.checkState(tableName.isPresent(), "Cannot find index name `%s`.", each.getIdentifier().getValue());
            result.add(tableName.get());
        }
        return result;
    }
    
    private Optional<String> findLogicTableNameFromMetaData(final String logicIndexName) {
        for (String each : tableMetas.getAllTableNames()) {
            if (tableMetas.get(each).containsIndex(logicIndexName)) {
                return Optional.of(each);
            }
        }
        return Optional.absent();
    }
    
    private Collection<RouteUnit> getAllRouteUnits(final ShardingRule shardingRule, final String logicTableName) {
        Collection<RouteUnit> result = new LinkedList<>();
        TableRule tableRule = shardingRule.getTableRule(logicTableName);
        for (DataNode each : tableRule.getActualDataNodes()) {
            RouteUnit routeUnit = new RouteUnit(each.getDataSourceName());
            routeUnit.getTableUnits().add(new TableUnit(logicTableName, each.getTableName()));
            result.add(routeUnit);
        }
        return result;
    }
}
