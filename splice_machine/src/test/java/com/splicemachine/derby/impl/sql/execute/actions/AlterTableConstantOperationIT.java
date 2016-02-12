package com.splicemachine.derby.impl.sql.execute.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.splicemachine.db.shared.common.reference.SQLState;
import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceTableWatcher;
import com.splicemachine.derby.test.framework.SpliceUnitTest;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.derby.test.framework.TestConnection;
import com.splicemachine.homeless.TestUtils;

import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AlterTableConstantOperationIT extends SpliceUnitTest {
    public static final String SCHEMA = AlterTableConstantOperationIT.class.getSimpleName().toUpperCase();

    protected static SpliceWatcher spliceClassWatcher = new SpliceWatcher();
    public static final String TABLE_NAME_1 = "A";
    public static final String TABLE_NAME_2 = "B";
    public static final String TABLE_NAME_3 = "C";
    public static final String TABLE_NAME_4 = "D";
    public static final String TABLE_NAME_5 = "E";
    public static final String TABLE_NAME_6 = "F";
    public static final String TABLE_NAME_7 = "G";
    public static final String TABLE_NAME_8 = "H";
    public static final String TABLE_NAME_9 = "I";
    public static final String TABLE_NAME_10 = "J";
    public static final String TABLE_NAME_11 = "K";
    public static final String TABLE_NAME_12 = "l";
    protected static SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(SCHEMA);

    private static String tableDef = "(TaskId INT NOT NULL, empId Varchar(3) NOT NULL, StartedAt INT NOT NULL, FinishedAt INT NOT NULL)";
    protected static SpliceTableWatcher spliceTableWatcher1 = new SpliceTableWatcher(TABLE_NAME_1,SCHEMA, tableDef);
    protected static SpliceTableWatcher spliceTableWatcher2 = new SpliceTableWatcher(TABLE_NAME_2,SCHEMA, tableDef);
    protected static SpliceTableWatcher spliceTableWatcher3 = new SpliceTableWatcher(TABLE_NAME_3,SCHEMA, "(i int)");
    protected static SpliceTableWatcher spliceTableWatcher4 = new SpliceTableWatcher(TABLE_NAME_4,SCHEMA, tableDef);
    protected static SpliceTableWatcher spliceTableWatcher5 = new SpliceTableWatcher(TABLE_NAME_5,SCHEMA, "(i int)");
    protected static SpliceTableWatcher spliceTableWatcher6 = new SpliceTableWatcher(TABLE_NAME_6,SCHEMA, "(i int)");
    protected static SpliceTableWatcher spliceTableWatcher7 = new SpliceTableWatcher(TABLE_NAME_7,SCHEMA, "(i int, primary key (i))");
    protected static SpliceTableWatcher spliceTableWatcher8 = new SpliceTableWatcher(TABLE_NAME_8,SCHEMA, "(i int)");
    protected static SpliceTableWatcher spliceTableWatcher9 = new SpliceTableWatcher(TABLE_NAME_9,SCHEMA, "(i int)");
    protected static SpliceTableWatcher spliceTableWatcher10 = new SpliceTableWatcher(TABLE_NAME_10,SCHEMA, "(i int)");
    protected static SpliceTableWatcher spliceTableWatcher11 = new SpliceTableWatcher(TABLE_NAME_11,SCHEMA,
            "(num varchar(4) not null, name char(20), " +
                    "grade decimal(4) not null check (grade in (100,150,200)), city char(15), " +
                    "primary key (grade,num))");
    protected static SpliceTableWatcher spliceTableWatcher12 = new SpliceTableWatcher(TABLE_NAME_12,SCHEMA,
            "(name char(14) not null, age int)");

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
            .around(spliceSchemaWatcher)
            .around(spliceTableWatcher1)
            .around(spliceTableWatcher2)
            .around(spliceTableWatcher3)
                                            .around(spliceTableWatcher4)
            .around(spliceTableWatcher5)
            .around(spliceTableWatcher6)
            .around(spliceTableWatcher7)
            .around(spliceTableWatcher8)
            .around(spliceTableWatcher9)
            .around(spliceTableWatcher10)
            .around(spliceTableWatcher11)
            .around(spliceTableWatcher12);

    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher();

    /**
     * Test basic alter table - insert, read, alter, write, read - make sure rows inserted after
     * alter are visible.
     * @throws Exception
     */
    @Test
    public void testInsertAlterTableInsert() throws Exception{
        Connection connection1 = methodWatcher.createConnection();
        for (int i=0; i<10; i++) {
            connection1.createStatement().execute(
                    String.format("insert into %1$s (TaskId, empId, StartedAt, FinishedAt) values (123%2$d,'JC',05%3$d,06%4$d)",
                            this.getTableReference(TABLE_NAME_1), i, i, i));
        }
        ResultSet resultSet = connection1.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_1)));
        Assert.assertEquals(10, resultSetSize(resultSet));
        connection1.createStatement().execute(String.format("alter table %s add column date timestamp", this.getTableReference(TABLE_NAME_1)));
        connection1.createStatement().execute(
                String.format("insert into %1$s (TaskId, empId, StartedAt, FinishedAt, Date) values (%2$d,'JC',09%3$d,09%4$d, '2013-05-28 13:03:20')",
                        this.getTableReference(TABLE_NAME_1), 1244, 0, 30));
        resultSet = connection1.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_1)));
        Assert.assertEquals("Expected to see another column", 5, columnWidth(resultSet));
        Assert.assertEquals("Expected to see an additional row.",11, resultSetSize(resultSet));
    }

    /**
     * Test basic thread isolation for alter table - 2 connections begin txns;
     * c1 adds a column, c2 should not be able to see it until after both c1 and c2 commit.
     * @throws Exception
     */
    @Test
    public void testAlterTableIsolation() throws Exception{
        Connection connection1 = methodWatcher.createConnection();
        Connection connection2 = methodWatcher.createConnection();
        connection1.setAutoCommit(false);
        connection2.setAutoCommit(false);
        connection1.createStatement().execute(String.format("alter table %s add column date timestamp", this.getTableReference(TABLE_NAME_2)));
        connection1.createStatement().execute(
                String.format("insert into %1$s (TaskId, empId, StartedAt, FinishedAt, Date) values (%2$d,'JC',09%3$d,09%4$d, '2013-05-28 13:03:20')",
                        this.getTableReference(TABLE_NAME_2), 1244, 0, 30));
        ResultSet resultSet = connection1.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_2)));
        Assert.assertEquals("Can't read own write",1, resultSetSize(resultSet));
        resultSet = connection2.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_2)));
        Assert.assertEquals("Read Committed Violated",0, resultSetSize(resultSet));
        Assert.assertEquals("Didn't expect to see another column until after commit.", 4, columnWidth(resultSet));

        connection2.commit();
        resultSet = connection2.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_2)));
        Assert.assertEquals("Read Committed Violated",0, resultSetSize(resultSet));
        Assert.assertEquals("Didn't expect to see another column until after commit.", 4, columnWidth(resultSet));

        connection2.commit();
        resultSet = connection2.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_2)));
        Assert.assertEquals("Read Committed Violated",0, resultSetSize(resultSet));
        Assert.assertEquals("Didn't expect to see another column until after commit.", 4, columnWidth(resultSet));

        connection1.commit();
        connection2.commit();
        resultSet = connection2.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_2)));
        Assert.assertEquals("Expected to see an additional row.",1, resultSetSize(resultSet));
        Assert.assertEquals("Expected to see another column.", 5, columnWidth(resultSet));
    }

    @Test
    public void testUpdatingAlteredColumns() throws Exception {
        Statement s = methodWatcher.getStatement();
        s.executeUpdate(String.format("insert into %s values 1,2,3,4,5",this.getTableReference(TABLE_NAME_3)));
        s.executeUpdate(String.format("alter table %s add column j int",this.getTableReference(TABLE_NAME_3)));
        s.executeUpdate(String.format("update %s set j = 5",this.getTableReference(TABLE_NAME_3)));
        ResultSet rs = methodWatcher.executeQuery(String.format("select j from %s",this.getTableReference(TABLE_NAME_3)));
        int i=0;
        while (rs.next()) {
            i++;
            Assert.assertEquals("Update did not happen", 5, rs.getInt(1));
        }
        Assert.assertEquals("returned values must match",5,i);
    }

    @Test
    public void testAlterTableIsolationInactiveTransaction() throws Exception{
        Connection connection1 = methodWatcher.createConnection();
        Connection connection2 = methodWatcher.createConnection();
        connection1.setAutoCommit(false);
        connection2.setAutoCommit(false);
        ResultSet resultSet = connection2.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_4)));
        Assert.assertEquals("Read Committed Violated",0, resultSetSize(resultSet));
        connection2.commit();
        Assert.assertEquals("Didn't expect to see another column until after commit.", 4, columnWidth(resultSet));
        connection1.createStatement().execute(String.format("alter table %s add column date timestamp", this.getTableReference(TABLE_NAME_4)));
        connection1.createStatement().execute(
                String.format("insert into %1$s (TaskId, empId, StartedAt, FinishedAt, Date) values (%2$d,'JC',09%3$d,09%4$d, '2013-05-28 13:03:20')",
                        this.getTableReference(TABLE_NAME_4), 1244, 0, 30));
        resultSet = connection1.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_4)));
        Assert.assertEquals("Can't read own write", 1, resultSetSize(resultSet));
        connection1.commit();
        resultSet = connection2.createStatement().executeQuery(String.format("select * from %s", this.getTableReference(TABLE_NAME_4)));
        Assert.assertEquals("Expected to see an additional row.",1, resultSetSize(resultSet));
        Assert.assertEquals("Expected to see another column.", 5, columnWidth(resultSet));
        connection2.commit();
    }

    @Test
    @Ignore("DB-4442 Add column, default value")
    public void testAddColumnDefaultIsReadable() throws Exception {
        Connection conn = methodWatcher.createConnection();
        try(Statement statement=conn.createStatement()){
            statement.execute(String.format("insert into %s values 1,2,3",spliceTableWatcher5));

            try(ResultSet resultSet=statement.executeQuery("select * from "+ spliceTableWatcher5)){
                Assert.assertEquals("Should have only 1 column.",1,columnWidth(resultSet));
            }
            statement.execute(String.format("alter table %s add column j int default 5",spliceTableWatcher5));

            try(ResultSet resultSet=statement.executeQuery("select * from "+ spliceTableWatcher5)){
                Assert.assertEquals("Should have 2 columns.",2,columnWidth(resultSet));
                int count=0;
                while(resultSet.next()){
                    Assert.assertEquals("Second column should have the default value",5,resultSet.getInt(2));
                    count++;
                }
                Assert.assertEquals("Wrong number of results",3,count);
            }
        }finally{
            conn.close();
        }
    }

    @Test
    public void testTruncate() throws Exception {
        Connection connection1 = methodWatcher.createConnection();
        connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_6)));

        PreparedStatement ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_6)));
        ResultSet resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet));

        connection1.createStatement().execute(String.format("truncate table %s", this.getTableReference(TABLE_NAME_6)));

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_6)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 0 rows.", 0, resultSetSize(resultSet));

        connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_6)));

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_6)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet));
    }

    @Test
    public void testTruncatePK() throws Exception {
        Connection connection1 = methodWatcher.createConnection();
        connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_7)));

        PreparedStatement ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_7)));
        ResultSet resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet));

        connection1.createStatement().execute(String.format("truncate table %s", this.getTableReference(TABLE_NAME_7)));

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_7)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 0 rows.", 0, resultSetSize(resultSet));

        connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_7)));

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_7)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 3 rows.",3,resultSetSize(resultSet));
    }


    @Test
    public void testTruncateWithIndex() throws Exception {
        Connection connection1 = methodWatcher.createConnection();
        connection1.createStatement().execute(String.format("create index trunc_idx on %s (i)", this
            .getTableReference(TABLE_NAME_8)));
        try {
            connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_8)));

            PreparedStatement ps = connection1.prepareStatement(String.format("select i from %s --splice-properties index=trunc_idx", this.getTableReference(TABLE_NAME_8)));
            ResultSet resultSet = ps.executeQuery();
            Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet));

            connection1.createStatement().execute(String.format("truncate table %s", this.getTableReference(TABLE_NAME_8)));

            ps = connection1.prepareStatement(String.format("select i from %s --splice-properties index=trunc_idx", this.getTableReference(TABLE_NAME_8)));
            resultSet = ps.executeQuery();

            Assert.assertEquals("Should have 0 rows.", 0, resultSetSize(resultSet));

            connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_8)));

            ps = connection1.prepareStatement(String.format("select i from %s --splice-properties index=trunc_idx", this.getTableReference(TABLE_NAME_8)));
            resultSet = ps.executeQuery();
            Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet));
        } finally {
            connection1.createStatement().execute(String.format("drop index %s", this.getTableReference("trunc_idx")));
        }
    }


    @Test
    public void testTruncateIsolation() throws Exception {
        Connection connection1 = methodWatcher.createConnection();
        connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_9)));

        PreparedStatement ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_9)));
        ResultSet resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet));

        Connection connection2 = methodWatcher.createConnection();
        connection1.setAutoCommit(false);
        connection1.createStatement().execute(String.format("truncate table %s", this.getTableReference(TABLE_NAME_9)));

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_9)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 0 rows.", 0, resultSetSize(resultSet));

        PreparedStatement ps2 = connection2.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_9)));
        ResultSet resultSet2 = ps2.executeQuery();
        Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet2));

        connection2.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_9)));
        ps2 = connection2.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_9)));
        resultSet2 = ps2.executeQuery();
        Assert.assertEquals("Should have 3 rows.", 6, resultSetSize(resultSet2));

        connection1.commit();
        connection2.rollback();

        ps2 = connection2.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_9)));
        resultSet2 = ps2.executeQuery();
        Assert.assertEquals("Should have 0 rows.", 0, resultSetSize(resultSet2));

        connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_9)));

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_9)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet));
        connection1.commit();
    }



    @Test
    public void testTruncateRollback() throws Exception {
        Connection connection1 = methodWatcher.createConnection();
        connection1.createStatement().execute(String.format("insert into %s values 1,2,3", this.getTableReference(TABLE_NAME_10)));

        PreparedStatement ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_10)));
        ResultSet resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 3 rows.", 3, resultSetSize(resultSet));

        connection1.setAutoCommit(false);
        connection1.createStatement().execute(String.format("truncate table %s", this.getTableReference(TABLE_NAME_10)));

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_10)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 0 rows.", 0, resultSetSize(resultSet));

        connection1.rollback();

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_10)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 3 rows.",3,resultSetSize(resultSet));
        connection1.commit();
    }

    @Test
    public void testAddCheckConstraint() throws Exception {
        // test for DB-2705: NPE when altering table adding check constraint.
        // Does not check that check constraint works - they don't - just tests the NPE is fixed.
        TestConnection connection = methodWatcher.createConnection();

        connection.createStatement().execute(String.format("alter table %s add constraint numck check (num < '999')",
                spliceTableWatcher11));
        connection.commit();

        connection.createStatement().execute(String.format("insert into %s values ('01', 'Jeff', 100, " +
                                                               "'St. Louis')",
                                                           spliceTableWatcher11));

        long count = connection.count(String.format("select * from %s", spliceTableWatcher11));
        Assert.assertEquals("incorrect row count!", 1, count);

    }

    @Test
    @Ignore("DB-4004 Alter table keyd column problem. Isolation not working as it should.")
    public void testAddPrimaryKeyIsolation() throws Exception {
        Connection connection1 = methodWatcher.createConnection();
        connection1.createStatement().execute(String.format("insert into %s values ('Bob',20)",
                                                            this.getTableReference(TABLE_NAME_12)));
        connection1.createStatement().execute(String.format("insert into %s values ('Mary',21)",
                                                            this.getTableReference(TABLE_NAME_12)));

        PreparedStatement ps = connection1.prepareStatement(String.format("select * from %s",
                                                                          this.getTableReference(TABLE_NAME_12)));
        ResultSet resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 2 rows.", 2, resultSetSize(resultSet));

        Connection connection2 = methodWatcher.createConnection();
        connection2.setAutoCommit(false);
        connection2.createStatement().execute(String.format("alter table %s add constraint name_pk_t1  primary key " +
                                                                "(name)",
                                                            this.getTableReference(TABLE_NAME_12)));

        connection1.createStatement().execute(String.format("insert into %s values ('Joe',22)",
                                                            this.getTableReference(TABLE_NAME_12)));
        connection1.createStatement().execute(String.format("insert into %s values ('Fred',23)",
                                                            this.getTableReference(TABLE_NAME_12)));

        ps = connection1.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_12)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 4 rows.", 4, resultSetSize(resultSet));

        ps = connection2.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_12)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 2 rows.",2,resultSetSize(resultSet));

        connection2.commit();

        ps = connection2.prepareStatement(String.format("select * from %s", this.getTableReference(TABLE_NAME_12)));
        resultSet = ps.executeQuery();
        Assert.assertEquals("Should have 4 rows.",4,resultSetSize(resultSet));

        ps = connection1.prepareStatement(String.format("select * from %s where age = 21",this.getTableReference
                (TABLE_NAME_12)));
        resultSet = ps.executeQuery();
        while (resultSet.next()) {
            Assert.assertEquals("Expecting Mary.", "Mary", resultSet.getString(1).trim());
        }
    }

    @Test
    @Ignore("DB-4004 Alter table keyd column problem. Isolation not working as it should.")
    public void testAddPrimaryKeyEnforcementBefore() throws Exception {
        String tableRef = this.getTableReference("before");
        methodWatcher.executeUpdate(String.format("create table %s (name char(14) not null, age int)",tableRef));

        TestConnection connection1 = methodWatcher.createConnection();
        connection1.createStatement().execute(String.format("insert into %s values ('Bob',20)",tableRef));
        connection1.createStatement().execute(String.format("insert into %s values ('Bob',19)", tableRef));
        connection1.createStatement().execute(String.format("insert into %s values ('Mary',21)", tableRef));

        long count = connection1.count(String.format("select * from %s where name = 'Bob'",tableRef));
        Assert.assertEquals("incorrect row count!", 2, count);


        Connection connection2 = methodWatcher.createConnection();
        connection2.setAutoCommit(false);
        // should fail
        assertSqlFails(String.format("alter table %s add constraint name_pk_t1_before  primary key (name)", tableRef),
                       "The statement was aborted because it would have caused a duplicate key value in a unique or " +
                           "primary key constraint or unique index identified by 'NAME_PK_T1_BEFORE' defined on " +
                           "'BEFORE'.",
                       "before");

        count = connection1.count(String.format("select * from %s where name = 'Bob'", tableRef));
        Assert.assertEquals("incorrect row count!", 2, count);
    }

    @Test
    public void testAddPrimaryKeyEnforcementAfter() throws Exception {
        String tableRef = this.getTableReference("after");
        methodWatcher.executeUpdate(String.format("create table %s (name char(14) not null, age int)",tableRef));

        TestConnection connection1 = methodWatcher.createConnection();
        connection1.createStatement().execute(String.format("insert into %s values ('Bob',20)", tableRef));
        connection1.createStatement().execute(String.format("insert into %s values ('Mary',21)", tableRef));

        long count = connection1.count(String.format("select * from %s where name = 'Mary'", tableRef));
        Assert.assertEquals("incorrect row count!", 1, count);

        Connection connection2 = methodWatcher.createConnection();
        connection2.setAutoCommit(false);
        connection2.createStatement().execute(String.format("alter table %s add constraint name_pk_t1_after primary " +
                                                                "key (name)", tableRef));

        count = connection1.count(String.format("select * from %s where name = 'Mary'",tableRef));
        Assert.assertEquals("incorrect row count!", 1, count);

        assertSqlFails(String.format("insert into %s values ('Mary',21)",tableRef),
                "The statement was aborted because it would have caused a duplicate key value in a unique or "+
                        "primary key constraint or unique index identified by 'NAME_PK_T1_AFTER' defined on 'AFTER'.",
                "after");

        count = connection1.count(String.format("select * from %s where name = 'Mary'",tableRef));
        Assert.assertEquals("incorrect row count!", 1, count);
    }

    @Test
    public void testUniqueConstraint() throws Exception {
        String tableName = "fred".toUpperCase();
        String tableRef = this.getTableReference(tableName);
        methodWatcher.executeUpdate(String.format("create table %s (name char(14) not null constraint NAME_PK1 " +
                                                      "primary key, age int)", tableRef));
        methodWatcher.getStatement().execute(String.format("alter table %s add column id int constraint uid unique",
                                                           tableRef));

        // Prints the index (unique constraint) info
        ResultSet rs = methodWatcher.getOrCreateConnection().getMetaData().getIndexInfo(null, SCHEMA, tableName, false, false);
        TestUtils.FormattedResult fr = TestUtils.FormattedResult.ResultFactory.convert("get table metadata", rs);
        System.out.println(fr.toString());

        methodWatcher.getStatement().execute(String.format("insert into %s values ('Bob',20,1)", tableRef));
        methodWatcher.getStatement().execute(String.format("insert into %s values ('Mary',22,2)",tableRef));

        rs = methodWatcher.getStatement().executeQuery(String.format("select * from %s",tableRef));
        while (rs.next()) {
            Assert.assertNotNull("ID is null", rs.getObject("id"));
        }
    }

    @Test(expected=SQLException.class)
    public void testAlterTableXml() throws Exception {
        Connection conn = methodWatcher.createConnection();
        conn.setAutoCommit(false);
        conn.createStatement().execute("create table testAlterTableXml (i int)");

        try {
            conn.createStatement().execute("alter table testAlterTableXml add column x xml");
        } catch (SQLException se) {
            /*
             * The ErrorState.NOT_IMPLEMENTED ends with a .S, which won't be printed in the
             * error message, so we need to be sure that we strip it if it ends that way
             */
            String sqlState=SQLState.NOT_IMPLEMENTED;
            int dotIdx = sqlState.indexOf(".");
            if(dotIdx>0)
                sqlState = sqlState.substring(0,dotIdx);
            Assert.assertEquals(sqlState,se.getSQLState());
            throw se;
        } finally {
            conn.rollback();
        }
    }

    @Test
    public void testAlterTableResizeVarchar() throws Exception {
        // DB-3790: alter table resize column
        String tableName = "resize".toUpperCase();
        String tableRef = this.getTableReference(tableName);
        methodWatcher.executeUpdate(String.format("create table %s (age int)", tableRef));
        methodWatcher.getStatement().execute(String.format("alter table %s add column id varchar(10)", tableRef));

        ResultSet rs = methodWatcher.getOrCreateConnection().getMetaData().getColumns(null, SCHEMA, tableName, "%");
        while (rs.next()) {
            if (rs.getString(4).equals("ID")) {
                assertEquals(10, rs.getInt(7));
                break;
            }
        }

        methodWatcher.getStatement().execute(String.format("alter table %s alter column id set data type varchar(20)", tableRef));

        rs = methodWatcher.getOrCreateConnection().getMetaData().getColumns(null, SCHEMA, tableName, "%");
        while (rs.next()) {
            if (rs.getString(4).equals("ID")) {
                assertEquals(20, rs.getInt(7));
                break;
            }
        }

    }

    private void assertSqlFails(String sql, String expectedException, String tableName) {
        tableName = tableName.toUpperCase();
        try {
            methodWatcher.executeUpdate(sql);
            fail("expected this sql to fail: " + sql);
        } catch (SQLException e) {
            assertEquals("Expected exception. Got: " + e.getLocalizedMessage(), expectedException, e.getLocalizedMessage());
            assertTrue("Expected "+ tableName +" exception. Got: " + e.getLocalizedMessage(), e.getLocalizedMessage().contains(tableName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
