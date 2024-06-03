package es.fic.ri;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class WebIndexer {

    static private int timeOut;
    public static void main(String[] args) {

        //directorio de las urls
        String pathUrl = Paths.get("src", "test", "resources", "urls").toString();

        //config.properties
        Properties properties = loadProperties();
        String time = properties.getProperty("timeOut");
        if(time == null){
            timeOut = 10;
        }else{
            timeOut = Integer.parseInt(time);
        }

        String[] onlyDoms = new String[0];
        boolean onlyDomsP = false;
        String onlyDom =  properties.getProperty("onlyDoms");
        if(onlyDom != null){
            onlyDoms = onlyDom.split(" ");
            onlyDomsP = true;
        }

        //Para -p
        long startTimeMillis = 0;
        long finishTimeMillis;

        //parámetros que se pasan por linea de comandos

        //obligatorios
        String index = null;
        String docs = null;

        //opcionales
        boolean create = false;
        int numThreads = Runtime.getRuntime().availableProcessors();
        boolean h = false; //en true cada hilo indica el comienzo y el fin de su trabajo
        boolean p = false; // en true la aplicación informará del fin de su trabajo
        boolean titleTermVectors = false;
        boolean bodyTermVectors = false;
        String analyzerName = null;
        Analyzer analyzer;


        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    index = args[++i];
                    break;
                case "-docs":
                    docs = args[++i];
                    break;
                case "-create":
                    create = true;
                    break;
                case "-numThreads":
                    try {
                        numThreads = Integer.parseInt(args[++i]);
                        if(numThreads <= 0){
                            System.err.println("Numthreads no puede ser menor o igual a 0. Usando el valor por defecto");
                            numThreads = Runtime.getRuntime().availableProcessors();
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Numthreads tiene que ser un Integer. Usando el valor por defecto.");
                    }
                    break;
                case "-h":
                    h = true;
                    break;
                case "-p":
                    p = true;
                    break;
                case "-titleTermVectors":
                    titleTermVectors = true;
                    break;
                case "-bodyTermVectors":
                    bodyTermVectors = true;
                    break;
                case "-analyzer":
                    analyzerName = args[++i];
                    break;
                default:
                    System.err.println("Argumento desconocido: " + args[i]);
                    System.exit(1);
            }
        }

        if (index == null || docs == null) {
            System.err.println("Parámetros -index y -docs obligatorios");
            System.exit(1);
        }
        checkDocsPath(docs);

        if (p) {
            startTimeMillis = System.currentTimeMillis();
        }

        analyzer = getAnalyzer(analyzerName);

        IndexWriterConfig iwc = getIndexWriterConfig(analyzer, create);

        try {
            Directory dir = FSDirectory.open(Paths.get(index));
            try (IndexWriter writer = new IndexWriter(dir, iwc)) {
                processUrls(writer, pathUrl, docs, numThreads, h, titleTermVectors, bodyTermVectors, onlyDoms, onlyDomsP);
            } catch (Exception e) {
                System.err.println(e);
            }
        } catch (Exception e) {
            System.err.println(e);
        }

        if (p) {
            finishTimeMillis = System.currentTimeMillis();
            System.out.println("Creado el indice " + index + " en " + (finishTimeMillis - startTimeMillis) + " milisegundos");
        }
    }

    private static void checkDocsPath(String docs){
        Path path = Paths.get(docs);

        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                System.out.println("El directorio no existía y se creo en: " + docs);
            } catch (IOException e) {
                System.err.println("Error al crear el directorio: " + e.getMessage());
                System.exit(-1);
            }
        }
    }
    private static Analyzer getAnalyzer(String analyzerName) {
        if (analyzerName == null) {
            return new StandardAnalyzer();
        }

        switch (analyzerName) {
            case "EnglishAnalyzer":
                return new EnglishAnalyzer();
            case "SpanishAnalyzer":
                return new SpanishAnalyzer();
            default:
                System.err.println("Nombre del analyzer incorrecto, usando el StandarAnalyzer");
                return new StandardAnalyzer();
        }
    }

    private static void processUrls(IndexWriter writer, String urlFilesLocation,
                                    String docs, int numThreads, boolean h, boolean titleTermVectors,
                                    boolean bodyTermVectors, String [] onlyDoms, boolean onlyDomsV) {

        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);


        /*Por cada archivo con urls creamos un nuevo hilo*/
        for (String path : getUrlFiles(urlFilesLocation)) {

            final Runnable worker = new WorkerThread(writer, Path.of(path), docs, h, titleTermVectors, bodyTermVectors, onlyDoms, onlyDomsV);
            /*
             * Send the thread to the ThreadPool. It will be processed eventually.
             */
            executor.execute(worker);
        }

        /*
         * Close the ThreadPool; no more jobs will be accepted, but all the previously
         * submitted jobs will be processed.
         */
        executor.shutdown();

        /* Wait up to 1 hour to finish all the previously submitted jobs */
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (final InterruptedException e) {
            e.printStackTrace();
            System.exit(-2);
        }

        if (h) {
            System.out.println("Acabaron todos los hilos");
        }

    }


    private static List<String> getUrlFiles(String urlFilesLocation) {
        List<String> filePaths = new ArrayList<>();

        try {
            Files.walk(Paths.get(urlFilesLocation))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".url"))
                    .forEach(path -> filePaths.add(path.toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return filePaths;
    }


    private static class WorkerThread implements Runnable {

        private final Path file;
        private final boolean h;
        private final boolean bodyTermVectors;
        private final boolean titleTermVectors;
        private final boolean onlyDomsV;
        private final String docs;
        private final String[] onlyDoms;
        private final IndexWriter writer;

        public WorkerThread(IndexWriter writer, final Path file, String docs, boolean h, boolean titleTermVectors, boolean bodyTermVectors, String [] onlyDoms, boolean onlyDomsV) {
            this.file = file;
            this.h = h;
            this.docs = docs;
            this.writer = writer;
            this.titleTermVectors = titleTermVectors;
            this.bodyTermVectors = bodyTermVectors;
            this.onlyDoms = onlyDoms;
            this.onlyDomsV = onlyDomsV;
        }

        /**
         * This is the work that the current thread will do when processed by the pool.
         * In this case, it will make a petition for each url in de file and store the results
         * in order to be indexed later.
         */
        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
                if (h) {
                    System.out.printf("Hilo '%s' comienza con urls en el archivo:'%s'%n", Thread.currentThread().getName(), file);
                }
                String url;
                List<String> urlList = new ArrayList<>();
                while ((url = br.readLine()) != null) {
                    urlList.add(url);
                }
                doHttpOnList(writer, urlList, docs, titleTermVectors, bodyTermVectors, onlyDoms, onlyDomsV);

                if (h) {
                    System.out.printf("Hilo '%s' Termina con urls en el archivo:'%s'%n", Thread.currentThread().getName(), file);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private static void doHttpOnList(IndexWriter indexWriter, List<String> urls, String docs, boolean titleTermVectors, boolean bodyTermVectors, String [] onlyDoms, boolean onlyDomsV) {
        //En esta función se descargan las páginas web y se almacenan en los archivos .loc y .loc.notags
        urls.forEach(url -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeOut)).build();
                if (onlyDomsV) {
                    if (isInOnlyDomain(url, onlyDoms)) {
                        doHttpOnUrl(client, url, docs, indexWriter, titleTermVectors, bodyTermVectors);
                    }
                } else {
                    doHttpOnUrl(client, url, docs, indexWriter, titleTermVectors, bodyTermVectors);
                }
            } catch (Exception e) {
                System.err.println(e);
            }
        });
    }

    private static void doHttpOnUrl(HttpClient client, String url, String docs, IndexWriter indexWriter, boolean titleTermVectors, boolean bodyTermVectors){
        String httpS = "http://";
        String httpsS = "https://";

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(timeOut)).build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 200) {
                try {
                    // Guardar el contenido en un archivo
                    String domain = url.replace(httpsS, "").replace(httpS, "");
                    domain = noSlash(domain);
                    String filename = noFinalSlash(docs) + "/" + domain + ".loc";

                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
                        writer.write(response.body());
                        toNotags(filename);
                        indexDoc(indexWriter, url, Paths.get(filename), Paths.get(filename.replace(".loc", ".loc.notags")), titleTermVectors, bodyTermVectors);
                    }
                } catch (IOException e) {
                    System.err.println("Dirección no válida para los documentos");
                    System.err.println(e);
                    System.exit(-1);
                }
            } else if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String newUrl = response.headers().firstValue("Location").orElse("");
                System.err.println("Código 3xx");
                doHttpOnUrl(client, newUrl, docs, indexWriter, titleTermVectors, bodyTermVectors);
            } else {
                System.err.println("Solicitud para " + url + " falló con código de estado " + response.statusCode());
            }
            return null;
        }).join(); // Wait for the request to complete
    }

    private static void toNotags(String filePath) {
        //La función sirve para quitar las etiquetas html
        String notagsPath = filePath.replace(".loc", ".loc.notags");

        try {
            Document doc = Jsoup.parse(new File(filePath), "UTF-8");
            String titulo = doc.title();
            String cuerpo = doc.body().text();

            try (FileWriter writer = new FileWriter(notagsPath)) {
                writer.write(titulo);
                writer.write("\n");
                writer.write(cuerpo);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String noFinalSlash(String cadena) {
        if (cadena.endsWith("/")) {
            // Eliminar el último carácter
            cadena = cadena.substring(0, cadena.length() - 1);
        }
        return cadena;
    }

    private static String noSlash(String cadena) {
        return cadena.replace("/", "").replace("?", "").replace("=","_");
    }


    private static void indexDoc(IndexWriter writer, String url, Path fileT, Path fileNT, boolean titleTermVectors, boolean bodyTermVectors) throws IOException {
        try (InputStream streamT = Files.newInputStream(fileT); InputStream streamNT = Files.newInputStream(fileNT); BufferedReader reader = new BufferedReader(new InputStreamReader(streamNT, StandardCharsets.UTF_8)); BufferedReader readerContent = new BufferedReader(new InputStreamReader(streamT, StandardCharsets.UTF_8))) {
            // make a new, empty document
            org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();

            //get the parameters to index
            String hostname = InetAddress.getLocalHost().getHostName();
            String thread = Thread.currentThread().getName();
            long locKb = Files.size(fileT) / 1024; // Tamaño en kilobytes del .loc
            long notagsKb = Files.size(fileNT) / 1024; // Tamaño en kilobytes del .loc.notags
            String creationTimeI = Files.getAttribute(fileT, "creationTime").toString();
            String lastAccessTimeI = Files.getAttribute(fileT, "lastAccessTime").toString();
            String lastModifiedTimeI = Files.getAttribute(fileT, "lastModifiedTime").toString();

            FileTime creationFileTime = (FileTime) Files.getAttribute(fileT, "creationTime");
            FileTime lastAccessFileTime = (FileTime) Files.getAttribute(fileT, "lastAccessTime");
            FileTime lastModifiedFileTime = (FileTime) Files.getAttribute(fileT, "lastModifiedTime");
            String creationTimeLucene = DateTools.dateToString(new Date(creationFileTime.toMillis()), DateTools.Resolution.SECOND);
            String lastAccessTimeLucene = DateTools.dateToString(new Date(lastAccessFileTime.toMillis()), DateTools.Resolution.SECOND);
            String lastModifiedTimeLucene = DateTools.dateToString(new Date(lastModifiedFileTime.toMillis()), DateTools.Resolution.SECOND);

            String title = reader.readLine();

            // Read the remaining lines to form the body
            StringBuilder bodyBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                bodyBuilder.append(line).append("\n");
            }
            String body = bodyBuilder.toString();

            // Add the path of the file as a field named "path".  Use a
            // field that is indexed (i.e. searchable), but don't tokenize
            // the field into separate words and don't index term frequency
            // or positional information:
            doc.add(new KeywordField("path", fileT.toString(), Field.Store.YES));

            // Add the contents of the file to a field named "contents".  Specify a Reader,
            // so that the text of the file is tokenized and indexed, but not stored.
            doc.add(new TextField("contents", readerContent));
            doc.add(new StringField("url", url, Field.Store.YES));
            doc.add(new StringField("hostname", hostname, Field.Store.YES));
            doc.add(new StringField("thread", thread, Field.Store.YES));
            doc.add(new LongField("locKb", locKb, Field.Store.YES));
            doc.add(new LongField("notagsKb", notagsKb, Field.Store.YES));
            doc.add(new StringField("creationTime", creationTimeI, Field.Store.YES));
            doc.add(new StringField("lastAccessTime", lastAccessTimeI, Field.Store.YES));
            doc.add(new StringField("lastModifiedTime", lastModifiedTimeI, Field.Store.YES));
            doc.add(new StringField("creationTimeLucene", creationTimeLucene, Field.Store.YES));
            doc.add(new StringField("lastAccessTimeLucene", lastAccessTimeLucene, Field.Store.YES));
            doc.add(new StringField("lastModifiedTimeLucene", lastModifiedTimeLucene, Field.Store.YES));

            if (titleTermVectors) {
                FieldType titleFieldType = new FieldType(TextField.TYPE_STORED);
                titleFieldType.setStoreTermVectors(true);
                titleFieldType.setStoreTermVectorOffsets(true);
                titleFieldType.setStoreTermVectorPositions(true);
                doc.add(new Field("title", title, titleFieldType));
            } else {
                doc.add(new TextField("title", title, Field.Store.YES));
            }

            if (bodyTermVectors) {
                FieldType bodyFieldType = new FieldType(TextField.TYPE_STORED);
                bodyFieldType.setStoreTermVectors(true);
                bodyFieldType.setStoreTermVectorOffsets(true);
                bodyFieldType.setStoreTermVectorPositions(true);
                doc.add(new Field("body", body, bodyFieldType));
            } else {
                doc.add(new TextField("body", body, Field.Store.YES));
            }

            if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
                // New index, so we just add the document (no old document can be there):
                System.out.println("adding " + fileT);
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                System.out.println("updating " + fileT);
                writer.updateDocument(new Term("path", fileT.toString()), doc);
            }
        } catch (Exception e) {
            System.err.println("Excepción: " + e);
        }
    }

    private static IndexWriterConfig getIndexWriterConfig(Analyzer analyzer, boolean create) {
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

        if (create) {
            // Create a new index in the directory, removing any
            // previously indexed documents:
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        } else {
            // Add new documents to an existing index:
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        }
        return iwc;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        Path configPath = Paths.get("src", "main", "resources", "config.properties");
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (Exception e) {
            System.err.println("Ocurrió el siguiente error mientras se cargaba el archivo config.properties (usando propiedades por defecto) " + e);
        }
        return properties;
    }

    private static boolean isInOnlyDomain(String url, String[] onlyDomains){
        try{
          URI uri = new URI(url);
          String domain = uri.getHost();
          for (String onlyDomain : onlyDomains) {
            if (domain.endsWith(onlyDomain)) {
              return true;
            }
          }
          return false;
        }catch (Exception e){
          System.err.println("Error al filtrar por dominio");
          return false;
        }
    }
}
