package es.fic.ri;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TopTermsInDoc {

  public static void main (String[] args) {
    String index = null;
    String field = null;
    String outFile = null;
    String url = null;
    int docId = -1;
    int top = 10;

    boolean urlB = false;


    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-index":
          index = args[++i];
          break;
        case "-field":
          field = args[++i];
          break;
        case "-outfile":
          outFile = args[++i];
          break;
        case "-url":
          url = args[++i];
          urlB = true;
          break;
        case "-docID":
          docId = Integer.parseInt(args[++i]);
          break;
        case "-top":
          top = Integer.parseInt(args[++i]);
          break;
        default:
          System.err.println("Argumento desconocido: " + args[i]);
          System.exit(1);
      }
    }

    if (index == null || field == null || top <= 0 || outFile == null ) {
      System.err.println("Parámetros -index, -field y -outfile son obligatorios, y -top debe ser mayor que 0.");
      System.exit(1);
    }

    if (docId < 0 && url == null) {
      System.err.println("Parámetro -docId debe ser igual o mayor a 0, o el parámetro -url es obligatorio");
      System.exit(1);
    }

    try (IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)))) {
      if (urlB) {
        docId = getDocIdFromUrl(reader, url);
        if (docId == -1) {
          System.err.println("Url " + url + " no válida");
          System.exit(-1);
        }
      }

      TermVectors termVectors = reader.termVectors();
      Terms terms = termVectors.get(docId, field);
      TermsEnum termsEnum = terms.iterator();



      //Mostrar los resultados por pantalla y guardarlos al outfile
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {

          List<Map.Entry<String, Double>> topTerms = new ArrayList<>();
          while (termsEnum.next() != null) {

            String term = termsEnum.term().utf8ToString();
            long tf = termsEnum.totalTermFreq();
            termsEnum.seekExact(new BytesRef(term));
            int df = reader.docFreq(new Term(field,term));

            double idf = Math.log10((double) reader.numDocs() / (double) df);
            double tfIdf = tf * idf;
            topTerms.add(new AbstractMap.SimpleEntry<>(term, tfIdf));

            //Ordenar los resultados
            topTerms.sort((term1, term2) -> Double.compare(term2.getValue(), term1.getValue()));

            //Seleccionar solo los N primeros
            topTerms = topTerms.subList(0, Math.min(top, topTerms.size()));

            System.out.printf("Término: %s, TF: %d, DF: %d, TF x IDF(log10): %.2f%n", term, tf, df, tfIdf);
            writer.write(String.format("Término: %s, TF: %d, DF: %d, TF x IDF(log10): %.2f%n", term, tf, df, tfIdf));
          }
//      }
//      try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
//        //Calcular TF, DF, IDF, and TF x IDF
//        List<Map.Entry<String, Double>> topTerms = new ArrayList<>();
//        while (termsEnum.next() != null) {
//          String term = termsEnum.term().utf8ToString();
//          long tf = termsEnum.totalTermFreq();
//          int df = termsEnum.docFreq();
//          double idf = Math.log10((double) reader.numDocs() / (double) df);
//          double tfIdf = tf * idf;
//          topTerms.add(new AbstractMap.SimpleEntry<>(term, tfIdf));
//
//          //Ordenar los resultados
//          topTerms.sort((term1, term2) -> Double.compare(term2.getValue(), term1.getValue()));
//
//          //Seleccionar solo los N primeros
//          topTerms = topTerms.subList(0, Math.min(top, topTerms.size()));
//
//          System.out.printf("Término: %s, TF: %d, DF: %d, TF x IDF(log10): %.2f%n", term, tf, df, tfIdf);
//          writer.write(String.format("Término: %s, TF: %d, DF: %d, TF x IDF(log10): %.2f%n", term, tf, df, tfIdf));
//        }
      } catch (IOException e) {
        e.printStackTrace();
      }

    } catch (IOException e) {
      System.out.println("Excepción: " + e);
      e.printStackTrace();
    }

  }

  private static int getDocIdFromUrl(IndexReader reader, String urlStr) throws IOException {
    for (int docId = 0; docId < reader.maxDoc(); docId++) {
      Document doc = reader.storedFields().document(docId);
      String storedUrl = doc.getField("url").stringValue();
      if (storedUrl.equals(urlStr)) {
        return docId;
      }
    }
    return -1;
  }
}
