package controller;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import utils.PrintUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Classe di utilità per calcolare la correlazione tra le feature del dataset e la bugginess.
 * Utilizza la correlazione di Spearman per misurare la relazione monotona tra le variabili
 */
public class CorrelationCalculator {

    private CorrelationCalculator() {}

    /**
     * Metodo principale che orchestra il processo di calcolo della correlazione.
     * Legge il dataset, calcola la correlazione per ogni feature e salva i risultati
     */
    public static void calculateAndSave(String projectName) throws IOException {
        String inputFilePath = String.format("csvFiles/%s/Dataset.csv", projectName.toLowerCase());
        String outputDir = "correlationFiles";
        String outputFileName = String.format("%s_correlation.csv", projectName.toLowerCase());

        try (Reader reader = new FileReader(Paths.get(inputFilePath).toFile());
             CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            List<String> numericColumns = new ArrayList<>();
            Map<String, List<Double>> featureValues = new HashMap<>();
            List<Double> labelValues = new ArrayList<>();

            // Identifica le colonne numeriche (le feature) da analizzare
            for (String header : csvParser.getHeaderMap().keySet()) {
                // Esclude le colonne non numeriche come nomi e ID
                if (!header.equals("MethodFullyQualifiedName") && !header.equals("IsBuggy") && !header.equals("ReleaseID")) {
                    numericColumns.add(header);
                    featureValues.put(header, new ArrayList<>());
                }
            }

            // Estrai i dati dal CSV, convertendoli in formato numerico
            for (CSVRecord csvRecord : csvParser) {
                for (String feature : numericColumns) {
                    try {
                        featureValues.get(feature).add(Double.parseDouble(csvRecord.get(feature)));
                    } catch (NumberFormatException e) {
                        // Se un valore non è un numero valido, si aggiunge un valore nullo e non si blocca l'analisi
                        featureValues.get(feature).add(0.0);
                    }
                }
                // Converte l'etichetta 'yes'/'no' in 1.0/0.0 per il calcolo statistico
                String label = csvRecord.get("IsBuggy").trim().toLowerCase();
                labelValues.add(label.equals("yes") ? 1.0 : 0.0);
            }

            // Calcola la correlazione di Spearman per ogni feature
            SpearmansCorrelation correlation = new SpearmansCorrelation();
            List<String[]> correlationResults = new ArrayList<>();

            for (String feature : numericColumns) {
                double[] featureArray = featureValues.get(feature).stream().mapToDouble(Double::doubleValue).toArray();
                double[] labelArray = labelValues.stream().mapToDouble(Double::doubleValue).toArray();

                double corr = correlation.correlation(featureArray, labelArray);
                correlationResults.add(new String[]{feature, String.format(Locale.US, "%.4f", corr)});
            }

            // Ordina i risultati in base al valore assoluto della correlazione, dal più alto al più basso
            correlationResults.sort((o1, o2) -> {
                double corr1 = Math.abs(Double.parseDouble(o1[1]));
                double corr2 = Math.abs(Double.parseDouble(o2[1]));
                return Double.compare(corr2, corr1);
            });

            // Salva i risultati ordinati in un nuovo file CSV
            saveResultsToCsv(outputDir, outputFileName, correlationResults);

        }
    }

    // Metodo helper per scrivere i risultati della correlazione in un file CSV
    private static void saveResultsToCsv(String outputDir, String outputFileName, List<String[]> results) throws IOException {
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath); // Crea cartella di output se non esiste

        try (
                BufferedWriter writer = Files.newBufferedWriter(outputPath.resolve(outputFileName));
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("Feature", "SpearmanCorrelation"))
        ) {
            // Scrive ogni riga (Feature, Correlazione) nel file
            for (String[] row : results) {
                csvPrinter.printRecord(row[0], row[1]);
            }
            PrintUtils.Console.info("Correlation CSV file created");
        }
    }
}