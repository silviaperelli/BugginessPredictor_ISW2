package utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class WhatIfDatasetCreator {

    private static final String PROJECT_NAME = "bookkeeper";
    private static final String INPUT_DIR = "csvFiles/" + PROJECT_NAME;
    private static final String OUTPUT_DIR = "whatIfDataset/" + PROJECT_NAME;
    private static final String SMELL_METRIC_COLUMN = "NumCodeSmells";

    public static void main(String[] args) {
        // Definisce i percorsi dei file
        Path inputFileA = Paths.get(INPUT_DIR, "Dataset.csv");
        Path outputDir = Paths.get(OUTPUT_DIR);
        Path outputFileC = outputDir.resolve("C.csv");
        Path outputFileBPlus = outputDir.resolve("B_plus.csv");
        Path outputFileB = outputDir.resolve("B.csv");

        try {
            // Crea la cartella di output se non esiste
            Files.createDirectories(outputDir);
            System.out.println("Cartella di output creata (se non esisteva): " + outputDir);

            // Prepara i file reader e i writer
            Reader readerA = new FileReader(inputFileA.toFile());

            // Usa try-with-resources per garantire la chiusura automatica
            try (
                    CSVParser csvParser = new CSVParser(readerA, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                    CSVPrinter printerC = new CSVPrinter(new FileWriter(outputFileC.toFile()), CSVFormat.DEFAULT);
                    CSVPrinter printerBPlus = new CSVPrinter(new FileWriter(outputFileBPlus.toFile()), CSVFormat.DEFAULT);
                    CSVPrinter printerB = new CSVPrinter(new FileWriter(outputFileB.toFile()), CSVFormat.DEFAULT)
            ) {
                // Prendi l'header e scrivilo nei file di output
                List<String> header = csvParser.getHeaderNames();
                printerC.printRecord(header);
                printerBPlus.printRecord(header);
                printerB.printRecord(header);

                int smellColumnIndex = header.indexOf(SMELL_METRIC_COLUMN);
                if (smellColumnIndex == -1) {
                    System.err.println("ERRORE: La colonna '" + SMELL_METRIC_COLUMN + "' non è stata trovata.");
                    return;
                }

                System.out.println("Inizio elaborazione delle righe...");
                int rowCountA = 0;
                int rowCountC = 0;
                int rowCountBPlus = 0;

                // Itera su ogni riga del dataset A
                for (CSVRecord record : csvParser) {
                    rowCountA++;
                    int smellCount = Integer.parseInt(record.get(SMELL_METRIC_COLUMN));

                    if (smellCount > 0) {
                        // 10.1: Scrivi la riga originale in B+
                        printerBPlus.printRecord(record);
                        rowCountBPlus++;

                        // 10.3: Crea la versione manipolata per B
                        List<String> manipulatedRecord = new ArrayList<>();
                        record.forEach(manipulatedRecord::add); // Copia i valori
                        manipulatedRecord.set(smellColumnIndex, "0"); // Imposta NSmells a 0
                        printerB.printRecord(manipulatedRecord);

                    } else {
                        // 10.2: Scrivi la riga in C
                        printerC.printRecord(record);
                        rowCountC++;
                    }
                }

                System.out.println("\n--- Riepilogo ---");
                System.out.println("Righe totali elaborate da Dataset.csv: " + rowCountA);
                System.out.println("Righe scritte in C.csv: " + rowCountC);
                System.out.println("Righe scritte in B_plus.csv: " + rowCountBPlus);
                System.out.println("Righe scritte in B.csv: " + rowCountBPlus);
                System.out.println("\nProcesso completato con successo!");


            }
        } catch (IOException e) {
            System.err.println("Si è verificato un errore di I/O: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("Errore di formato numerico: assicurati che la colonna 'NumCodeSmells' contenga solo numeri interi.");
            e.printStackTrace();
        }
    }
}