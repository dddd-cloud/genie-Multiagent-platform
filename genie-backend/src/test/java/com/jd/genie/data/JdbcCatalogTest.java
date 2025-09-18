package com.jd.genie.data;

import com.jd.genie.data.jdbc.JdbcConnectionConfig;
import com.jd.genie.data.jdbc.catalog.JdbcCatalog;
import com.jd.genie.data.jdbc.catalog.JdbcCatalogLoader;
import com.jd.genie.data.jdbc.connection.ConnectionWrapper;
import com.jd.genie.data.jdbc.connection.JdbcConnectionFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JdbcCatalogTest {

    JdbcConnectionConfig mysqlConfig = new JdbcConnectionConfig();
    JdbcConnectionConfig clickhouseConfig = new JdbcConnectionConfig();

    /*@BeforeEach*/
    public void before() {
        mysqlConfig.setKey("mysql-demo");
        mysqlConfig.setUrl("jdbc:mysql://xxx:3306/xxx");
        mysqlConfig.setDriverName("com.mysql.jdbc.Driver");
        mysqlConfig.setUserName("xx");
        mysqlConfig.setPassword("xx");

        clickhouseConfig.setUrl("jdbc:clickhouse://xxx:2000/cho_hr_dw");
        clickhouseConfig.setKey("ck-demo");
        clickhouseConfig.setProxy(true);
        clickhouseConfig.setUserName("");
        clickhouseConfig.setPassword("");

    }

    /*@Test*/
    public void testMysqlListTables() throws Exception {
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(mysqlConfig);

        JdbcCatalog catalog = JdbcCatalogLoader.load(mysqlConfig.getJdbcDialect());
        List<SimpleTable> tables = catalog.listTables(wrapper.getConnection(), "");
        for (SimpleTable table : tables) {
            System.out.println(table);
        }
    }

    /*@Test*/
    public void testMysqlTableColumn() throws Exception {
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(mysqlConfig);

        JdbcCatalog catalog = JdbcCatalogLoader.load(mysqlConfig.getJdbcDialect());
        List<TableColumn> columnList = catalog.getTableColumns(wrapper.getConnection(), "sys_me_send_config", "data_analysis");
        for (TableColumn column : columnList) {
            System.out.println(column);
        }
    }
    /*@Test*/
    public void testClickhouseListTable() throws Exception {
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(clickhouseConfig);
        JdbcCatalog catalog = JdbcCatalogLoader.load(clickhouseConfig.getJdbcDialect());
        List<SimpleTable> tables = catalog.listTables(wrapper.getConnection(), "cho_hr_dw");
        for (SimpleTable table : tables) {
            System.out.println(table);
        }
    }
    /*@Test*/
    public void testClickhouseTableColumns() throws Exception {
        // Setup
        final ConnectionWrapper wrapper = JdbcConnectionFactory.getConnection(clickhouseConfig);
        JdbcCatalog catalog = JdbcCatalogLoader.load(clickhouseConfig.getJdbcDialect());
        List<TableColumn> columnList = catalog.getTableColumns(wrapper.getConnection(), "zhencuicui_1706584056783", "cho_hr_dw");
        for (TableColumn column : columnList) {
            System.out.println(column);
        }
    }
}
