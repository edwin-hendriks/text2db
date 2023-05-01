# 1. Table of content

- [1. Table of content](#1-table-of-content)
- [2. Introduction](#2-introduction)
- [3. How to create a single executable jar (aka 'fat jar')](#3-how-to-create-a-single-executable-jar-aka-fat-jar)
- [4. How to use](#4-how-to-use)
  - [4.1. When you have created a single executable (fat) jar](#41-when-you-have-created-a-single-executable-fat-jar)
  - [4.2. Directly from VS Code (without having to create fat jar)](#42-directly-from-vs-code-without-having-to-create-fat-jar)
- [5. Performance](#5-performance)
# 2. Introduction

This tool (for now) will do a raw 1-to-1 data migration from one database to another. In the target database the schema is called `old_db`. Currently only developed and tested for a Paradox DB to a Posgres DB but the rough mechanism should work for any DB that has a JDBC driver. 

In the future this tool could be changed to support more DB's.

It may also be changed to do the full migration to the actual target DB (the GEARS generated DB), but for now we will first investigate if that can better be performed inside of the target DB. 

# 3. How to create a single executable jar (aka 'fat jar')

From the working folder run this command:

> mvn clean compile assembly:single

It should result in a file in the `target` folder called something like this:

`db_migration-1.0-SNAPSHOT-jar-with-dependencies.jar`

You can copy this to wherever you like an run it (as described below)

# 4. How to use

## 4.1. When you have created a single executable (fat) jar

Go to where you have copied the jar file and run this command (note that this is an example command, you may have to change it to fit your needs):
```bash
java -jar db_migration-1.0-SNAPSHOT-jar-with-dependencies.jar jdbc:paradox:C:/tmp/2022-09-27_raw_paradox_DB_files/2022 jdbc:postgresql://localhost:5432/wyatt postgres VarC295n9KOW 1000 AAN
```

## 4.2. Directly from VS Code (without having to create fat jar)

How to use from VS Code (that has [Java enabled](https://code.visualstudio.com/docs/java/java-tutorial)):

1. Create a `.vscode\launch.json` with the following content:
```json
{
    "version": "0.2.0",
    "configurations": [
        
        {
            "type": "java",
            "name": "Launch Current File",
            "request": "launch",
            "mainClass": "${file}"
        },
        {
            "type": "java",
            "name": "Launch Migrate",
            "request": "launch",
            "mainClass": "com.xlrit.Migrate",
            "projectName": "db_migration",
            "args": [ "jdbc:paradox:C:/tmp/2022-09-27_raw_paradox_DB_files/2022"
                    , "jdbc:postgresql://localhost:5432/wyatt"
                    , "postgres"
                    , "VarC295n9KOW"
                    , "1000"
                    , "AAN"]
        }
    ]
}
```

2. Change the values in the `"args"` list to fit your environment. Here is a short explanation on these args:

Argument | How to fill
-------- | -----------
1 | JDBC URL to source DB.
2 | JDBC URL to destination DB.
3 | username of the destination DB
4 | password of this user
5 | How many records a commit is executed
6 | Which table to start with

3. Press **CTL+F5**

This will migrate the source DB to the target DB (inside a schema called `old_db`). For now it will only perform a 1-to-1 mapping. So all tables and attributes will stay the same (assuming that a mapping will be done from `old_db` to the actual desired target DB structure using SQL scripts). 

# 5. Performance

No big performance tests were run, but with a commit rate of 1000 records per commit a 1,6 GB DB can be migrated in 74 seconds (yeeeeh).