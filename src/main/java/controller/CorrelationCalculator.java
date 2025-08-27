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

public class CorrelationCalculator {

    // Rendi la classe non istanziabile se contiene solo metodi statici
    private CorrelationCalculator() {}

    /**
     * Calcola la correlazione di Spearman tra ogni feature e la bugginess,
     * e salva i risultati in un file CSV.
     *
     * @param projectName Il nome del progetto da analizzare.
     * @throws IOException Se si verifica un errore di lettura o scrittura.
     */
    public static void calculateAndSave(String projectName) throws IOException {
        String inputFilePath = String.format("csvFiles/%s/Dataset.csv", projectName.toLowerCase());
        String outputDir = "correlationFiles";
        String outputFileName = String.format("%s_correlation.csv", projectName.toLowerCase());

        // Parsing CSV
        try (Reader reader = new FileReader(Paths.get(inputFilePath).toFile());
             CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            List<String> numericColumns = new ArrayList<>();
            Map<String, List<Double>> featureValues = new HashMap<>();
            List<Double> labelValues = new ArrayList<>();

            // Identifica le colonne numeriche
            for (String header : csvParser.getHeaderMap().keySet()) {
                // Esclude le colonne non numeriche
                if (!header.equals("MethodFullyQualifiedName") && !header.equals("IsBuggy") && !header.equals("ReleaseID")) {
                    numericColumns.add(header);
                    featureValues.put(header, new ArrayList<>());
                }
            }

            // Estrai i dati dal CSV
            for (CSVRecord csvRecord : csvParser) {
                for (String feature : numericColumns) {
                    try {
                        featureValues.get(feature).add(Double.parseDouble(csvRecord.get(feature)));
                    } catch (NumberFormatException e) {
                        // Aggiunge un valore nullo o di default se il parsing fallisce
                        featureValues.get(feature).add(0.0);
                    }
                }
                String label = csvRecord.get("IsBuggy").trim().toLowerCase();
                labelValues.add(label.equals("yes") ? 1.0 : 0.0);
            }

            // Calcolo correlazioni
            SpearmansCorrelation correlation = new SpearmansCorrelation();
            List<String[]> correlationResults = new ArrayList<>();

            for (String feature : numericColumns) {
                double[] featureArray = featureValues.get(feature).stream().mapToDouble(Double::doubleValue).toArray();
                double[] labelArray = labelValues.stream().mapToDouble(Double::doubleValue).toArray();

                double corr = correlation.correlation(featureArray, labelArray);
                correlationResults.add(new String[]{feature, String.format(Locale.US, "%.4f", corr)});
            }

            // Ordina i risultati per valore di correlazione assoluto, in ordine decrescente
            correlationResults.sort((o1, o2) -> {
                double corr1 = Math.abs(Double.parseDouble(o1[1]));
                double corr2 = Math.abs(Double.parseDouble(o2[1]));
                return Double.compare(corr2, corr1);
            });

            // Scrittura su CSV di output
            saveResultsToCsv(outputDir, outputFileName, correlationResults);

        }
    }

    /**
     * Metodo helper per salvare i risultati della correlazione in un file CSV.
     */
    private static void saveResultsToCsv(String outputDir, String outputFileName, List<String[]> results) throws IOException {
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath); // crea cartella se non esiste

        try (
                BufferedWriter writer = Files.newBufferedWriter(outputPath.resolve(outputFileName));
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("Feature", "SpearmanCorrelation"))
        ) {
            for (String[] row : results) {
                csvPrinter.printRecord(row[0], row[1]);
            }
            PrintUtils.Console.info("Correlation CSV file created");
        }
    }
}