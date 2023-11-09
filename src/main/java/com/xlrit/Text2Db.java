package com.xlrit;

import static com.xlrit.Utils.log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

/**
 * Perform a basic raw import from text files that contain data to a DB.
 * Currently only text that have headers to a Postgres DB, but this example can
 * be expanded to do more DB's.
 *
 */
public class Text2Db {

    private String sourceFolder;
    private final String destinationDbUrl;
    private final String destinationUser;
    private final String destinationPass;

    public Text2Db(String[] args) {
        this.sourceFolder = args[0];
        this.destinationDbUrl = args[1]; // e.g. "jdbc:postgresql://localhost:5432/poc_edwin";
        this.destinationUser = System.getenv("PGUSER"); // e.g. "poc_edwin";
        this.destinationPass = System.getenv("PGPASSWORD"); // e.g. "somepasswd";
    }

    public void run() {
        long startMillis = System.currentTimeMillis();

        try {
            Files.walkFileTree(Paths.get(sourceFolder), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws FileNotFoundException, IOException {
                    if (!Files.isDirectory(file)) {

                        log("[INFO] ***************** START IMPORTING FILE: " + file.toString());
                        try (Connection destinationDbConnection = DriverManager.getConnection(destinationDbUrl, destinationUser,
                                        destinationPass)) {

                            log("[INFO] CREATING SCHEMA IF IT NOT YET EXISTS"); // TODO: low prio but this should be done only once per schema and not for each file/table
                            destinationDbConnection.createStatement().executeUpdate("CREATE SCHEMA IF NOT EXISTS old_db");

                            createTable(file, destinationDbConnection);
                            importFileIntoTable(file, destinationDbConnection);

                        } catch (SQLException e) {
                            e.printStackTrace();
                        } catch (CsvValidationException e) {
                            e.printStackTrace();
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        log("[SUCCESS] *************** FINISHED IMPORTING (in " + (System.currentTimeMillis() - startMillis) / 1000 + " seconds)");
    }

    private void createTable(Path textFile, Connection destinationDbConnection) throws SQLException, FileNotFoundException, IOException, CsvValidationException {
        String textFileName = textFile.getFileName().toString();
        String fullPathToFile = textFile.toString(); 
        String tabelName = textFileName.substring(0, textFileName.lastIndexOf(".")); // table name - filename without extension
        StringBuilder createTableSql = new StringBuilder("create table if not exists old_db.").append(quoted(tabelName)).append("(");
        
        log("[INFO] READING FILE: " + fullPathToFile);
        try ( FileReader filereader = new FileReader(fullPathToFile) ) {
            CSVParser parser = new CSVParserBuilder().withSeparator(';').build();
            CSVReader csvReader = new CSVReaderBuilder(filereader).withCSVParser(parser).build();
            String[] headerRecord = csvReader.readNext();
            for (String columnName : headerRecord) {
                String fixedColumnName = columnName.toLowerCase().replace("\"", "");
                createTableSql.append(" " + fixedColumnName + " ");
                createTableSql.append("varchar(255) null,");
            }
            replaceLastCharWith(createTableSql, ')');

            System.out.println("[INFO] EXECUTING THE FOLLOWING SQL CREATE STATEMENT: \n" + createTableSql);
            try (Statement statement = destinationDbConnection.createStatement()) {
                statement.execute(createTableSql.toString());
            }
        }
    }

    protected void importFileIntoTable(Path textFile, Connection destinationDbConnection) throws SQLException, IOException {
        String textFileName = textFile.getFileName().toString();
        String fullPathToFile = textFile.toString(); 
        String tabelName = textFileName.substring(0, textFileName.lastIndexOf(".")); // table name - filename without extension
        StringBuilder truncateSql = new StringBuilder("TRUNCATE old_db.").append(quoted(tabelName));
        StringBuilder psqlCommand = new StringBuilder("psql -c ")
            .append(quoted("\\copy old_db.\\\"" + tabelName + "\\\" FROM '" + fullPathToFile + "' (FORMAT csv, DELIMITER ';', HEADER true)"));
        // TODO: low prio but initially I tried to execute a SQL COPY command (in importTextFileSql) which does not require psql to be installed but that does not seem to work because it does not recognize the files. Not sure why.
        // StringBuilder importTextFileSql = new StringBuilder("COPY old_db.").append(quoted(tabelName)).append(" FROM '")
            // .append("/tmp/" + textFileName).append("' (DELIMITER ';', HEADER true)");
        
        try (Statement statement = destinationDbConnection.createStatement()) {
            log("[INFO] TRUNCATING TABLE WITH THIS QUERY: " + truncateSql);
            statement.execute(truncateSql.toString());
            log("[INFO] IMPORTING FILES USING PSQL COMMAND: " + psqlCommand );
            Process psqlProcess = Runtime.getRuntime().exec(psqlCommand.toString());
            log("[INFO] RESULT OF PSQL COMMAND:");
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(psqlProcess.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(psqlProcess.getErrorStream()));
            String s = null;
            while ((s = stdInput.readLine()) != null) System.out.println(s); 
            while ((s = stdError.readLine()) != null) System.out.println(s);

            // see TODO above.
            // log("[INFO] IMPORTING FILE WITH THIS QUERY: " + importTextFileSql);
            // statement.execute(importTextFileSql.toString());
        }
        
    }

    private void replaceLastCharWith(StringBuilder sb, char replacement) {
        sb.setCharAt(sb.length() - 1, replacement);
    }

    private static String quoted(String stringToQuote) {
        return "\"" + stringToQuote + "\"";
    }

    public static void main(String[] args) {
        Text2Db migrate = new Text2Db(args);
        migrate.run();
    }
}