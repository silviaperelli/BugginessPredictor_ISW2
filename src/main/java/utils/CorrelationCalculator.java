package utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CorrelationCalculator {

    public static void main(String[] args) throws Exception {
        String projectName = "SYNCOPE";

        String inputFilePath = String.format("csvFiles/%s/Dataset.csv", projectName.toLowerCase(), projectName.toLowerCase());
        String outputDir = "correlationFiles";
        String outputFileName = String.format("%s_correlation.csv", projectName.toLowerCase());

        // Parsing CSV
        Reader reader = new FileReader(Paths.get(inputFilePath).toFile());
        CSVParser csvParser = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(reader);

        List<String> numericColumns = new ArrayList<>();
        Map<String, List<Double>> featureValues = new HashMap<>();
        List<Double> labelValues = new ArrayList<>();

        // Identifica le colonne numeriche
        for (String header : csvParser.getHeaderMap().keySet()) {
            if (!header.equals("MethodFullyQualifiedName") && !header.equals("IsBuggy")) {
                numericColumns.add(header);
                featureValues.put(header, new ArrayList<>());
            }
        }

        // Estrai i dati dal CSV
        for (CSVRecord record : csvParser) {
            for (String feature : numericColumns) {
                featureValues.get(feature).add(Double.parseDouble(record.get(feature)));
            }
            String label = record.get("IsBuggy").trim().toLowerCase();
            labelValues.add(label.equals("yes") ? 1.0 : 0.0);
        }

        // Calcolo correlazioni
        SpearmansCorrelation correlation = new SpearmansCorrelation();
        List<String[]> correlationResults = new ArrayList<>();
        correlationResults.add(new String[]{"Feature", "SpearmanCorrelation"});

        for (String feature : numericColumns) {
            double[] featureArray = featureValues.get(feature).stream().mapToDouble(Double::doubleValue).toArray();
            double[] labelArray = labelValues.stream().mapToDouble(Double::doubleValue).toArray();

            double corr = correlation.correlation(featureArray, labelArray);
            correlationResults.add(new String[]{feature, String.format(Locale.US, "%.4f", corr)});
        }

        // Scrittura su CSV di output
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath); // crea cartella se non esiste

        try (
                BufferedWriter writer = Files.newBufferedWriter(outputPath.resolve(outputFileName));
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("Feature", "SpearmanCorrelation"))
        ) {
            for (int i = 1; i < correlationResults.size(); i++) {
                String[] row = correlationResults.get(i);
                csvPrinter.printRecord(row[0], row[1]);
            }
            System.out.println("File CSV creato in: " + outputPath.resolve(outputFileName).toAbsolutePath());
        }
    }
}
