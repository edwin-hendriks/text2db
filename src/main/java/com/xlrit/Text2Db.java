package com.xlrit;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Perform a basic raw import from text files that contain data to a DB. Currently only text that have headers to a Postgres DB, but this example can be expanded to do more DB's.
 *
 */
public class Text2Db {
    
    private final String sourceDbUrl;
    private final String sourceDb;
    private final String sourceUser = "";
    private final String sourcePass = "";
    private final String destinationDbUrl;
    private final String destinationUser;
    private final String destinationPass;
    private final int    commitRate;
    private final String tableNameToStartWith;

    public Text2Db(String[] args) {
        this.sourceDbUrl          = args[0];                                                            // e.g. "jdbc:paradox:C:/tmp/2022-09-27_raw_paradox_DB_files/2022";
        this.sourceDb             = this.sourceDbUrl.substring(this.sourceDbUrl.lastIndexOf("/") + 1);  // the db name is the part after the last /
        this.destinationDbUrl     = args[1];                                                            // e.g. "jdbc:postgresql://localhost:5432/wyatt";
        this.destinationUser      = args[2];                                                            // e.g. "postgres";
        this.destinationPass      = args[3];                                                            // e.g. "VarC295n9KOW";
        this.commitRate           = Integer.parseInt(args[4]);                                          // e.g. 1000;
        this.tableNameToStartWith = args.length < 6 ? "" : args[5]; 
    }

    public void run() {
        long startMillis = System.currentTimeMillis();
        // initialize paradox driver (so we can run it as a fat jar)
        try {
            Class.forName("com.googlecode.paradox.Driver");
        } catch (ClassNotFoundException e1) {
            throw new RuntimeException("Driver class not found", e1);
        }
        try (Connection sourceDbConnection = DriverManager.getConnection(sourceDbUrl, sourceUser, sourcePass);
             Connection destinationDbConnection = DriverManager.getConnection(destinationDbUrl, destinationUser, destinationPass)) {
            
            System.out.println("CREATING SCHEMA IF IT NOT YET EXISTS");
            destinationDbConnection.createStatement().executeUpdate("CREATE SCHEMA IF NOT EXISTS old_db");
            
            System.out.println("FETCHING ALL TABLES FROM SOURCE DB");
            DatabaseMetaData metaData = sourceDbConnection.getMetaData();
            String[] types = { "TABLE" };
            
            try (ResultSet tablesMeta = metaData.getTables(null, sourceDb, "%", types)) {
                boolean startProcessing = false;
                while (tablesMeta.next()) {               
                    String tableName = tablesMeta.getString("TABLE_NAME");
                    if (tableNameToStartWith.equals("") || tableName.equals(tableNameToStartWith)) startProcessing = true;
                    if (!startProcessing) {
                        System.out.println("SKIPPING TABLE: " + tableName );
                    } else {
                        System.out.println("---------------------- PROCESSING TABLE: " + quoted(tableName));
                        try ( Statement statement = sourceDbConnection.createStatement();
                              ResultSet resultSet = statement.executeQuery("select * from " + quoted(tableName)) ) {
                            ResultSetMetaData resultMetaData = resultSet.getMetaData();
                            String insertSql = createTableAndPrepareInsert(tableName, resultMetaData, destinationDbConnection);
                            System.out.println("[INFO] check if target table already has records");
                            ResultSet rsRowCountTargetTable = destinationDbConnection.createStatement().executeQuery("SELECT COUNT(*) FROM old_db." + quoted(tableName));
                            rsRowCountTargetTable.next();
                            if (rsRowCountTargetTable.getInt(1) == 0) {
                                System.out.println("[INFO] 0 records found. So lets insert some records.");
                                insertData(tableName, insertSql, resultSet, resultMetaData, destinationDbConnection);
                            } else {
                                System.out.println("[WARNING] more than 0 records found. To be safe I will skip this table.");
                            }                            
                        } 
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("FINISHED DB MIGRATION (in " + (System.currentTimeMillis() - startMillis)/1000 + " seconds)");
    }

    private String createTableAndPrepareInsert(String tableName, ResultSetMetaData resultSetMetaData, Connection destinationDbConnection) throws SQLException {
        StringBuilder createTableSql = new StringBuilder("create table if not exists old_db.").append(quoted(tableName)).append("(");
        StringBuilder insertSqlColumnPart = new StringBuilder("insert into old_db.").append(quoted(tableName)).append(" (");
        StringBuilder insertSqlValuesPart = new StringBuilder(" values (");

        for (int columnIndex = 1; columnIndex <= resultSetMetaData.getColumnCount(); columnIndex++) {
            createTableSql.append(" " + quoted(resultSetMetaData.getColumnName(columnIndex)) + " ");
            createTableSql.append(paradoxType2PostgresType(resultSetMetaData, columnIndex));
            insertSqlColumnPart.append(quoted(resultSetMetaData.getColumnName(columnIndex))).append(",");
            insertSqlValuesPart.append(" ?,");
        }
        replaceLastCharWith(createTableSql, ')');
        replaceLastCharWith(insertSqlColumnPart, ')');
        replaceLastCharWith(insertSqlValuesPart, ')');

        System.out.println("EXECUTING THE FOLLOWING SQL CREATE STATEMENT: \n" + createTableSql);
        try ( Statement statement = destinationDbConnection.createStatement() ) {
            statement.execute(createTableSql.toString());
        }  
        return insertSqlColumnPart.toString() + insertSqlValuesPart.toString();
    }

    private void replaceLastCharWith(StringBuilder sb, char replacement) {
        sb.setCharAt(sb.length() - 1, replacement);
    }

    private void insertData(String tableName, String insertSql, ResultSet resultSet, ResultSetMetaData resultSetMetaData, Connection destinationDbConnection)
            throws SQLException {
        try ( PreparedStatement insertStatement = destinationDbConnection.prepareStatement(insertSql) ) {
            int i = 0;
            while (resultSet.next()) {
                for (int columnIndex = 1; columnIndex <= resultSetMetaData.getColumnCount(); columnIndex++) {
                    insertStatement.setObject(columnIndex, safeObject(resultSet.getObject(columnIndex)));
                }
                insertStatement.addBatch();
    
                //Commit every commitRate records
                i++;
                if (i % commitRate == 0) {
                    int[] insertedRecord = insertStatement.executeBatch();
                    System.out.println("Inserting " + insertedRecord.length + " records into table: " + tableName);
                }
            }
            // Do a final commit for any remaining records
            int[] insertedRecord = insertStatement.executeBatch();
            System.out.println("Inserting " + insertedRecord.length + " records into table: " + tableName );
        }
        
    }

    private static Object safeObject(Object possiblyUnsafeObject) {
        if (possiblyUnsafeObject instanceof String) {
            String possiblyUnsafeString = (String) possiblyUnsafeObject;
            String safeString = possiblyUnsafeString.replaceAll("[\\u0000]", "\n");
            return safeString;
        }
        return possiblyUnsafeObject;
    }

    private static String paradoxType2PostgresType(ResultSetMetaData columnMetaData, int columnIndex)
            throws SQLException {
        switch (columnMetaData.getColumnTypeName(columnIndex).toLowerCase()) {
            case "varchar":
                return "varchar(" + columnMetaData.getColumnDisplaySize(columnIndex) + ") null,";
            case "memo":
                return "text null,";
            case "formatted_memo":
                return "text null,";
            case "date":
                return "date null,";
            case "long":
                return "numeric null,";
            case "integer":
                return "numeric null,";
            case "auto_increment":
                return "numeric null,";
            case "number":
                return "numeric(20, " + columnMetaData.getScale(columnIndex) + ") null,";
            case "currency":
                return "numeric(20, 2) null,";
            case "boolean":
                return "boolean null,";
            case "blob":
                return "bytea null,";
            default:
                throw new SQLException("NOT YET IMPLEMENTATED DATA TYPE");
        }
    }

    private static String quoted(String stringToQuote) {
        return "\"" + stringToQuote + "\"";
    }

    public static void main(String[] args) {
        Text2Db migrate = new Text2Db(args);
        migrate.run();
    }
}