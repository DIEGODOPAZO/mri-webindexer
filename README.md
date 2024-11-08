# MRI Web Indexer

**MRI Web Indexer** is a web indexing project developed in Java and based on Apache Lucene. It allows indexing and searching information from web pages, based on specific URLs. This project also includes functionalities for analyzing and ranking terms within the generated indexes.

## Main Features

1. **Multithreaded Web Indexing**:
   - Reads `.url` files in the `src/test/resources/urls` directory, containing lists of URLs.
   - Downloads and stores each web page in `.loc` and `.loc.notags` files (HTML tags removed).
   - Extracts and indexes fields such as `title`, `body`, `hostname`, `thread`, and other relevant metadata.
   - Uses Java 11+ `HttpClient` to manage downloads, with connection timeouts and optional redirect handling.

2. **Index Search and Analysis**:
   - **TopTermsInDoc**: Displays the most relevant terms for a specific document in the index, along with term frequency statistics.
   - **TopTermsInField**: Displays the most frequent terms in a specific field of the index.

## Project Setup

### Requirements

- **Java 11** or higher.
- **Apache Maven** for dependency management.

### Installation

1. Clone the repository:
    ```bash
    git clone <repository-URL>
    cd mri-webindexer
    ```

2. Configure project properties in the `config.properties` file (located in `src/main/resources`):
    ```plaintext
    onlyDoms=.uk .es .com
    ```

3. Build the project using Maven:
    ```bash
    mvn clean install
    ```

## Running the Application

### Main Class: `WebIndexer`

This class is responsible for processing `.url` files, downloading web pages, extracting and storing content in `.loc` and `.loc.notags` formats, and indexing the content.

#### Execution Options

```bash
java -cp target/mri-webindexer-1.0-SNAPSHOT.jar WebIndexer \
    -index <INDEX_PATH> \
    -docs <FILES_PATH> \
    [-create] \
    [-numThreads <NUMBER_OF_THREADS>] \
    [-h] \
    [-p] \
    [-titleTermVectors] \
    [-bodyTermVectors] \
    [-analyzer <ANALYZER_NAME>]
```

- `-index INDEX_PATH`: Path where the index will be stored.
- `-docs DOCS_PATH`: Path of `.loc` and `.loc.notags` files.
- `-create`: (Optional) Creates a new index. If not used, opens in `CREATE_OR_APPEND` mode.
- `-numThreads`: Specifies the number of threads (defaults to the number of cores).
- `-h`: Each thread logs the start and end of its processing.
- `-p`: Shows total time taken to create the index.
- `-titleTermVectors` / `-bodyTermVectors`: Stores term vectors for `title` and `body`.
- `-analyzer Analyzer`: Specifies the Lucene Analyzer to use (defaults to `Standard Analyzer`).

### Additional Main Classes 
#### `TopTermsInDoc` 
Retrieves the top terms of a specific document in the index.
```bash
java -cp target/mri-webindexer-1.0-SNAPSHOT.jar TopTermsInDoc \ 
	-index <INDEX_PATH> \ 
	-field <FIELD_NAME> \
	-docID <DOCUMENT_ID> \ 
	-top <NUMBER_OF_TERMS> \ 
	-outfile <OUTPUT_FILE>
```
- `-docID`: Document ID in the index (or `-url` to specify a URL). - `-top`: Number of terms to display, ordered by `(raw tf) x idflog10`.

### `TopTermsInField`
Retrieves the top terms of a specific field in the index, ordered by document frequency (df).
```bash
java -cp target/mri-webindexer-1.0-SNAPSHOT.jar TopTermsInField \ 
	-index <INDEX_PATH> \ 
	-field <FIELD_NAME> \ 
	-top <NUMBER_OF_TERMS> \ 
	-outfile <OUTPUT_FILE>
```
## Advanced Configuration 
The `config.properties` file (in `src/main/resources`) allows customization of the domains to process using the `onlyDoms` property.  Example: 
```plaintext 
onlyDoms=.uk .es .com
 ```
