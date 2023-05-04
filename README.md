# 1. Introduction

This tool (for now) will do a raw 1-to-1 data migration from a folder containing csv files to a destination database. In the destination database the schema is called `old_db`. Currently only developed and tested for a Posgres DB but the rough mechanism should work for any DB that has a JDBC driver. 
# 2. Table of content

- [1. Introduction](#1-introduction)
- [2. Table of content](#2-table-of-content)
- [Pre-requisites](#pre-requisites)
- [3. How to create a single executable jar (aka 'fat jar')](#3-how-to-create-a-single-executable-jar-aka-fat-jar)
- [4. How to use](#4-how-to-use)
  - [4.1. When you have created a single executable (fat) jar](#41-when-you-have-created-a-single-executable-fat-jar)
  - [4.2. Directly from VS Code (without having to create fat jar)](#42-directly-from-vs-code-without-having-to-create-fat-jar)
- [5. Performance](#5-performance)

# Pre-requisites

* A postgres db running. I did that with docker: `docker run -p 5432:5432 --name poc_edwin -e POSTGRES_USER=poc_edwin -e POSTGRES_PASSWORD=somepasswd -e POSTGRES_DB=poc_edwin -d postgres` (and later restarts can be done by `docker start poc_edwin`)
* Java 11 (but to be honest I tested this with a Java 17 installation and only java 11 in the pom.xml)
* Maven installed
* Postgres (15) but only the command line utility psql

# 3. How to create a single executable jar (aka 'fat jar')

From the working folder run this command:

> mvn clean compile assembly:single

It should result in a file in the `target` folder called something like this:

`text2db-1.0-SNAPSHOT-jar-with-dependencies.jar`

You can copy this to wherever you like an run it (as described below)

# 4. How to use

## 4.1. When you have created a single executable (fat) jar

See `run.bat` for an example.

## 4.2. Directly from VS Code (without having to create fat jar)

How to use from VS Code (that has [Java enabled](https://code.visualstudio.com/docs/java/java-tutorial)):

1. Create a `.vscode\launch.json` with the following content:
```json
{
    "version": "0.2.0",
    "configurations": [
        
        {
            "type": "java",
            "name": "Launch Text2Db",
            "request": "launch",
            "mainClass": "com.xlrit.Text2Db",
            "projectName": "text_to_db",
            "env": {"PGUSER": "poc_edwin",
                    "PGPASSWORD": "somepasswd" },
            "args": [ "C:/tmp/csv"
                    , "jdbc:postgresql://localhost:5432/poc_edwin" ]
        }
    ]
}
```

2. Change the values in the `"args"` list to fit your environment. Here is a short explanation on these args:

Argument | How to fill
-------- | -----------
1 | The location of the csv files.
2 | JDBC URL to destination DB.

3. Press **CTL+F5**

# 5. Performance

No big performance tests were run, but 27 MB of CSV files in 9 seconds is okay I would say.