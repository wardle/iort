# `iort` - Interoperable Outcomes Research Tools

> The Observational Medical Outcomes Partnership (OMOP) Common Data Model (CDM) is an open community data standard, designed to standardize the structure and content of observational data and to enable efficient analyses that can produce reliable evidence.
>
> See [https://www.ohdsi.org/data-standardization](https://www.ohdsi.org/data-standardization/) 

`iort` is a library and command-line utility to make use of OMOP.

Most user-facing OMOP tools depend upon `R`, but `iort` is written in Clojure and runs on the JVM, and so is also usable from other JVM languages such as Java. `iort` can be run from the command-line as a runnable 'uberjar', or from directly source code if Clojure is installed.

For example, the initialisation of database tables, indexes and constraints is generated using `R` in the open-source repository [https://github.com/OHDSI/CommonDataModel](https://github.com/OHDSI/CommonDataModel), but the SQL statements cannot be readily executed independently as they include placeholders for the `R` toolchain to complete. The specifications for the CDM are actually recorded in CSV files, but these are processed to generate markdown and the markdown processed into parameterised SQL DDL statements, which are processed by the `R` toolchain to execute database-specific DDLs. Some of the `R` toolchain actually uses RJava to consume OHDSI Java libraries such as [SqlRender](https://github.com/OHDSI/SqlRender). 

Instead `iort` uses a simpler approach and generates DDL statements directly from the canonical CSV specifications.

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

# Intended functionality

`iort` will provide both a library and a command-line tool to support interoperable outcomes research:

* Download and execute Data Definition Language (DDL) statements to initialise a database with the OMOP CDM.
* Import OMOP vocabularies downloaded from the [OHDSI Athena service](https://athena.ohdsi.org).
* Provides a JVM hosted library and server for making use of OMOP data, including vocabularies
* Provides a JVM hosted library for simplifying data pipelines that extract, transform and load data into a database based on the OMOP CDN.
* Provides a FHIR terminology facade around OMOP vocabularies



