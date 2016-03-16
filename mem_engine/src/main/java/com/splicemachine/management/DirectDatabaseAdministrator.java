package com.splicemachine.management;

import org.sparkproject.guava.collect.Sets;
import com.splicemachine.EngineDriver;
import com.splicemachine.access.api.DatabaseVersion;
import com.splicemachine.derby.utils.DatabasePropertyManagementImpl;
import com.splicemachine.pipeline.PipelineDriver;
import com.splicemachine.utils.logging.LogManager;
import com.splicemachine.utils.logging.Logging;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 2/17/16
 */
public class DirectDatabaseAdministrator implements DatabaseAdministrator{
    private final Logging logging = new LogManager();

    @Override
    public void setLoggerLevel(String loggerName,String logLevel) throws SQLException{
        logging.setLoggerLevel(loggerName,logLevel);
    }

    @Override
    public List<String> getLoggerLevel(String loggerName) throws SQLException{
        return Collections.singletonList(logging.getLoggerLevel(loggerName));
    }

    @Override
    public Set<String> getLoggers() throws SQLException{
        return Sets.newHashSet(logging.getLoggerNames());
    }

    @Override
    public Map<String, DatabaseVersion> getClusterDatabaseVersions() throws SQLException{
        return Collections.singletonMap("mem",EngineDriver.driver().getVersion());
    }

    @Override
    public void setWritePoolMaxThreadCount(int maxThreadCount) throws SQLException{
        PipelineDriver.driver().writeCoordinator().setMaxAsyncThreads(maxThreadCount);
    }

    @Override
    public Map<String, Integer> getWritePoolMaxThreadCount() throws SQLException{
        return Collections.singletonMap("mem",PipelineDriver.driver().writeCoordinator().getMaxAsyncThreads());
    }

    @Override
    public Map<String, String> getGlobalDatabaseProperty(String key) throws SQLException{
        return Collections.singletonMap("mem",DatabasePropertyManagementImpl.instance().getDatabaseProperty(key));
    }

    @Override
    public void setGlobalDatabaseProperty(String key,String value) throws SQLException{
        DatabasePropertyManagementImpl.instance().setDatabaseProperty(key,value);
    }

    @Override
    public void emptyGlobalStatementCache() throws SQLException{
        //TODO -sf- no-op for now --eventually may need to implement
    }
}
