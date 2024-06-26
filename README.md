# `iort` - Interoperable Outcomes Research Tools


[![Scc Count Badge](https://sloc.xyz/github/wardle/iort)](https://github.com/wardle/iort/)
[![Scc Cocomo Badge](https://sloc.xyz/github/wardle/iort?category=cocomo&avg-wage=100000)](https://github.com/wardle/iort/)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/wardle/iort)](https://github.com/wardle/iort/releases/latest)
[![Clojars Project](https://img.shields.io/clojars/v/com.eldrix/iort.svg)](https://clojars.org/com.eldrix/iort)
[![cljdoc badge](https://cljdoc.org/badge/com.eldrix/iort)](https://cljdoc.org/d/com.eldrix/iort)

> The Observational Medical Outcomes Partnership (OMOP) Common Data Model (CDM) is an open community data standard, designed to standardize the structure and content of observational data and to enable efficient analyses that can produce reliable evidence.
>
> See [https://www.ohdsi.org/data-standardization](https://www.ohdsi.org/data-standardization/) 

`iort` is a library and command-line utility to make use of the OMOP CDM.

You can think of `iort` as a swiss-army knife for the OMOP CDM.

Most current user-facing OMOP CDM tools depend upon `R`, but `iort` is written in Clojure and runs on the JVM, and so is also usable from other JVM languages such as Java. `iort` can be run from the command-line as a runnable 'uberjar', or from directly source code if Clojure is installed.

As such, `iort` uses a simpler approach than the OHDSI tools, generating DDL statements directly from the canonical CSV specifications.

`iort` is designed to be composable with a number of other healthcare related libraries and tools:

* [hermes](https://github.com/wardle/hermes) - a SNOMED CT terminology server
* [hades](https://github.com/wardle/hades) - a HL7 FHIR facade for hermes providing FHIR terminology services
* [dmd](https://github.com/wardle/dmd) - an implementation of the UK dictionary of medicines and devices
* [clods](https://github.com/wardle/clods) - UK organisational data services - a directory of healthcare providers and sites in the UK
* [ods-weekly](https://github.com/wardle/ods-weekly) - UK general practitioners and surgeries
* [nhspd](https://github.com/wardle/nhspd) - NHS postcode directory - mapping every postal code in the UK to other geographies such as LSOA
* [deprivare](https://github.com/wardle/deprivare) - library and tools providing access to socioeconomic deprivation data across the UK
* [trud](https://github.com/wardle/trud) - library to consume healthcarre data from the NHS England technology reference update distribution (TRUD) 
* [concierge](https://github.com/wardle/concierge) - integration with NHS Wales (mostly proprietary) services providing standards-based facades
* [codelists](https://github.com/wardle/codelists) - declarative codelists for defining cohorts based on SNOMED CT, ECL, ATC codes and ICD-10.

These tools follow a similar pattern in that they provide:
  
* a suite of functions that can be used as a library within a larger application
* command-line accessible tools
* a graph API that allows traversal across and between each independent service

I need to take healthcare data from multiple sources, transform and normalise those data, and aggregate to support direct care and analytics. As the CDM creates a 'standard' schema for healthcare data, we can use CDM as an intermediary data format. This would not work unless you also standardise the vocabularies in use; having ready access to advanced SNOMED CT tools such as `hermes` in conjunction with other sources of reference data (e.g. the UK dictionary of medicines and devices, the UK's organisational data for healthcare sites/locations, as well as the CDM vocabularies facilitates creating ['pluripotent data'](https://wardle.org/strategy/2023/10/03/pluripotent-data.html). You can, of course, use `iort` without using `hermes` or `dmd`. 

For example, I supplement the CDM standard vocabulary with other tooling so that I can make sense of the latest data. For example, there will be SNOMED concepts in the UK extension that are in the standard vocabulary, and I define cohorts using an expressive mix of ICD-10, OPCS, ATC and SNOMED CT, and I need to make use of historical associations. As such, only using the OMOP CDM vocabularies available from Athena is insufficient. Composing different data-orientated tools is important and useful.


# Intended functionality

`iort` will provide both a library and a command-line tool to support interoperable outcomes research:

* Generate and execute Data Definition Language (DDL) statements to initialise a database with the OMOP CDM with 
on-demand addition and removal of database constraints and indexes
* Import OMOP vocabularies downloaded from the [OHDSI Athena service](https://athena.ohdsi.org).
* Provides a JVM hosted library and server for making use of OMOP data, including vocabularies
* Provides a JVM hosted library for simplifying data pipelines that extract, transform and load data into a database based on the OMOP CDN.
* Provides a FHIR terminology facade around OMOP vocabularies


It will therefore possible to build an `iort` pipeline that will initialise and populate a database with the OMOP CDM, and execute your own custom logic to extract and transform data from potentially multiple source systems, and potentially making use of the tools above for that process of normalisation, and write into a CDM. Likewise, one might instead use `iort` as part of a real-time analytics pipeline to take a feed from, for example, Apache Kafka, to transform and insert into a CDM-based database.

# Current development roadmap

`iort` is a new project and under active development. It is now partly functional and is being developed in the open.

Here are the items from the roadmap already completed:

- [x] Generate DDL statements to create database schema
- [x] Generate DDL statements to add and remove database constraints
- [x] Generate DDL statements to add and remove database indices
- [x] Add optional dependencies for different JDBC drivers
- [x] Add code to read and parse the CDM v5 vocabulary definitions that can be downloaded from the OHDSI Athena service.
- [x] Add options to select and create based on schema e.g. create tables only for 'CDM' or 'VOCAB' schema 
- [x] Build CLI entry point with options to generate or execute SQL
- [x] Add ability to build an uberjar with all necessary database drivers for a 'swiss-army knife' approach

Here are the items still pending:

- [ ] Set up GitHub actions to test against a matrix of versions and databases
- [ ] Provide a Clojure API to aid in transforming arbitrary source data into the OMOP CDM
- [ ] Add a CDM HTTP server API to allow clients to consume CDM data if direct SQL access insufficient
- [ ] Add a Clojure API to provide a FHIR facade around the core CDM vocabularies, potentially usable by [https://github.com/wardle/hades](https://github.com/wardle/hades) - requiring a trivial implementation
- [ ] Add automation to copy CDM data from one database to another, and make available via CLI
- [ ] Tweak handling of schema in databases that support schema

# Getting started

`iort` is only in the early stages of development, but it is already usable. You will need to [install Clojure](https://clojure.org/guides/install_clojure). Once `iort` is ready for a more formal release, I will provide an executable 'uberjar' that will contain multiple database drivers. 

##### Directly create database tables using a JDBC URL:

e.g. to create CDM version 5.4 database tables, indexes and constraints in a SQLite database called my-omop-cdm.db
```bash
clj -M:sqlite:run --cdm-version 5.4 --create --jdbc-url jdbc:sqlite:my-omop-cdm.db
```

e.g. to create CDM version 5.4 database tables, indexes and constraints in a PostgreSQL database, omop_cdm

```bash
clj -M:postgresql:run --cdm-version 5.4 --create --jdbc-url jdbc:postgresql:omop_cdm
```

###### Generate SQL DDL statements that you can execute manually, or via another means (such as psql)

```bash
clj -M:run --create --dialect postgresql
```

```bash
clj -M:run --create --dialect sqlite
```
Databases such as SQLite cannot add foreign key constraints after database tables have been created, so you can give hints to `iort`
so it generates the correct statements for the database type you are using.

###### Generate SQL DDL statements but only for selected schema(s)

```bash
clj -M:run --create-tables --dialect sqlite --schema VOCAB
```

This is ideal if you are creating multiple SQLite databases and will join them only later during
your analytics step.


You can choose multiple schema, either by using `--schema VOCAB --schema CDM` or using comma-delimited values:

```bash
clj -M:run --create-tables --dialect sqlite --schema VOCAB,CDM
```



##### Create and import the OMOP CDM vocabulary 

e.g. you have downloaded the latest CDM vocabulary from Athena, and want to initialise a new CDM database:

```bash
clj -M:postgresql:run -u jdbc:postgresql:omop_cdm --create --vocab ~/Downloads/vocabulary_download_v5
```

This will connect to the PostgreSQL database omop_cdm, create all of the tables, import the specified
vocabulary files, and then add constraints and indexes. 

If you want to use SQLite:

```bash
clj -M:sqlite:run -u jdbc:sqlite:omop_cdm.db --create --vocab ~/Downloads/vocabulary_download_v5
```

For example, if you want to create a SQLite database with only the VOCAB CDM tables and populate them from data downloaded from Athena:

```bash
clj -M:sqlite:run --create-tables --jdbc-url jdbc:sqlite:cdm54.db --schema VOCAB --vocab ~/Downloads/vocabulary_download_v5
```
=>
```bash
% sqlite3 cdm54.db
SQLite version 3.43.2 2023-10-10 13:08:14
Enter ".help" for usage hints.
sqlite> .tables
concept                concept_synonym        source_to_concept_map
concept_ancestor       domain                 vocabulary
concept_class          drug_strength
concept_relationship   relationship
```

SQLite allows you to create to multiple databases and perform joins across them, so this is a useful way
to combine the standard CDM vocabulary with your clinical data derived from one or more of your 
operational clinical data sources.

With databases other than SQLite, you are more likely to store the CDM vocabulary within the same database as your CDM data.

##### Remove indexes and turn off constraints

```
clj -M:postgresql:run -u jdbc:postgresql:omop_cdm --drop-constraints --drop-indexes
```

##### Add indexes and turn on constraints

```
clj -M:postgresql:run -u jdbc:postgresql:omop_cdm --add-constraints --add-indexes
```



# Why not use the `R` based OHDSI toolchain?

The current OMOP toolchain has a variety of steps. For example, the initialisation of database tables, indexes and constraints is generated using `R` in the open-source repository [https://github.com/OHDSI/CommonDataModel](https://github.com/OHDSI/CommonDataModel), but the SQL statements cannot be readily executed independently as they include placeholders for the `R` toolchain to complete. The specifications for the CDM are actually recorded in CSV files, but these are processed to generate markdown and the markdown processed into parameterised SQL DDL statements, which are processed by the `R` toolchain to execute database-specific DDLs. Some of the `R` toolchain actually uses RJava to consume OHDSI Java libraries such as [SqlRender](https://github.com/OHDSI/SqlRender).

In my view, all of those steps make the process of database initialisation more complex, and more difficult to reproduce in data pipelines. I have a strong preference for automation, and simplicity. Many of my design decisions are based upon wishing to create potentially ephemeral OMOP CDM-based databases, such as file-based databases based on SQLite created on demand for end-users, as well as the more conventional approach of looking after a single carefully maintained observational analytics database. For that, I need to be able to initialise and populate a CDM database on demand from operational clinical systems, and that means needing to generate DDL SQL statements on the fly without depending on installing `R`. 


