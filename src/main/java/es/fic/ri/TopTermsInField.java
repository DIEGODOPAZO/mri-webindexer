package es.fic.ri;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TopTermsInField {
  public static void main(String[] args) {
    String index = null;
    String field = null;
    String outFile = null;
    int top = 0;
    //Recuperar argumentos
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
        case "-top":
          top = Integer.parseInt(args[++i]);
          break;
        default:
          System.err.println("Unknown argument: " + args[i]);
          System.exit(1);
      }
    }

    //Comprueba los argumentos obligatorios
    if (index == null || field == null || top <= 0 || outFile == null) {
      System.err.println("Todos los parámetros son obligatorios, y -top debe ser mayor que 0.");
      System.exit(1);
    }

    try (IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)))) {
      int numDocs = reader.numDocs();
      List<TermStats> termStatsList = new ArrayList<>();

      //Recorre los documentos y consigue el df de los términos
      for (int docID = 0; docID < numDocs; docID++) {
        TermsEnum termsEnum = MultiTerms.getTerms(reader, field).iterator();
        while (termsEnum.next() != null) {
          String termText = termsEnum.term().utf8ToString();
          int docFreq = termsEnum.docFreq();
          boolean termExists = false;
          for (TermStats termStats : termStatsList) {
            if (termStats.term.equals(termText)) {
              termExists = true;
              break;
            }
          }
          if (!termExists) {
            TermStats termStats = new TermStats(termText, docFreq);
            termStatsList.add(termStats);
          }
        }
      }


      //Ordena la lista y muestra los n primeros términos
      termStatsList.sort(Comparator.comparingInt(TermStats::getDf).reversed());
      List<TermStats> topTerms = termStatsList.subList(0, Math.min(top, termStatsList.size()));

      System.out.println("Top " + top + " términos para el campo " + field + " ordenados por df:");

      //Imprime y guarda en el outFile los top n términos
      try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
        writer.println("Top " + top + " términos para el campo " + field + " ordenados por df:");

        for (TermStats termStats : topTerms) {
          System.out.println("Términos: " + termStats.term + ", df: " + termStats.df);
          writer.println("Términos: " + termStats.term + ", df: " + termStats.df);
        }
      }
    } catch (IOException e) {
      System.err.println("Excepción: " + e);
    }
  }

  //Almacena el df y el String de un término
  private static class TermStats {
    String term;
    int df;

    public TermStats(String term, int df) {
      this.term = term;
      this.df = df;
    }

    public int getDf() {
      return df;
    }
  }
}
