package controller;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WhatIfAnalysis {

    private static final Logger LOGGER = Logger.getLogger(WhatIfAnalysis.class.getName());
    private final String projectName;

    public WhatIfAnalysis(String projectName) {
        this.projectName = projectName.toUpperCase();
    }

    /**
     * Punto di ingresso per l'analisi What-If.
     */
    public static void main(String[] args) throws Exception {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.SEVERE);
        // Puoi cambiare il nome del progetto qui per eseguirlo su un altro
        String projectToAnalyze = "BOOKKEEPER";

        WhatIfAnalysis analysis = new WhatIfAnalysis(projectToAnalyze);
        analysis.execute();
    }

    /**
     * Orchestra l'intera pipeline di analisi What-If.
     */
    public void execute() throws Exception {
        System.out.println("\n--- Starting What-If Analysis for project: " + projectName + " ---");

        // FASE 1: Creazione dei dataset B, B+, C
        createWhatIfDatasets();

        // FASE 2: Analisi
        runAnalysis();
    }

    /**
     * Passo 10: Crea i dataset B, B+, e C partendo dal Dataset.csv principale.
     */
    private void createWhatIfDatasets() throws IOException {
        System.out.println("--- Phase 1: Creating What-If Datasets ---");

        String projectLower = projectName.toLowerCase();
        Path inputFileA = Paths.get("csvFiles", projectLower, "Dataset.csv");
        Path outputDir = Paths.get("whatIf", projectLower);
        Files.createDirectories(outputDir);

        Path outputFileC = outputDir.resolve("C.csv");
        Path outputFileBPlus = outputDir.resolve("B_plus.csv");
        Path outputFileB = outputDir.resolve("B.csv");

        try (
                Reader readerA = new FileReader(inputFileA.toFile());
                CSVParser csvParser = new CSVParser(readerA, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                CSVPrinter printerC = new CSVPrinter(new FileWriter(outputFileC.toFile()), CSVFormat.DEFAULT);
                CSVPrinter printerBPlus = new CSVPrinter(new FileWriter(outputFileBPlus.toFile()), CSVFormat.DEFAULT);
                CSVPrinter printerB = new CSVPrinter(new FileWriter(outputFileB.toFile()), CSVFormat.DEFAULT)
        ) {
            List<String> header = csvParser.getHeaderNames();
            printerC.printRecord(header);
            printerBPlus.printRecord(header);
            printerB.printRecord(header);

            int smellColumnIndex = header.indexOf("NumCodeSmells");
            if (smellColumnIndex == -1) {
                LOGGER.severe("Column 'NumCodeSmells' not found.");
                return;
            }

            for (CSVRecord record : csvParser) {
                int smellCount = Integer.parseInt(record.get("NumCodeSmells"));
                if (smellCount > 0) {
                    printerBPlus.printRecord(record);
                    List<String> manipulatedRecord = new ArrayList<>();
                    record.forEach(manipulatedRecord::add);
                    manipulatedRecord.set(smellColumnIndex, "0");
                    printerB.printRecord(manipulatedRecord);
                } else {
                    printerC.printRecord(record);
                }
            }
        }
    }

    /**
     * Passi 11-12: Addestra il modello su A e predice su tutti i dataset.
     */
    private void runAnalysis() throws Exception {
        System.out.println("\n--- Phase 2: Training and Prediction ---");

        Instances datasetA = loadDatasetFromCsv("csvFiles/" + projectName.toLowerCase() + "/Dataset.csv");
        if (datasetA == null) return;

        Classifier bClassifier = buildBestClassifier();
        System.out.println("Training the best classifier on dataset A...");
        bClassifier.buildClassifier(datasetA);

        System.out.println("Loading What-If datasets and predicting bugginess for each dataset...");
        Instances datasetB = loadDatasetFromCsv("whatIf/" + projectName.toLowerCase() + "/B.csv");
        Instances datasetBPlus = loadDatasetFromCsv("whatIf/" + projectName.toLowerCase() + "/B_plus.csv");
        Instances datasetC = loadDatasetFromCsv("whatIf/" + projectName.toLowerCase() + "/C.csv");

        if (datasetB == null || datasetBPlus == null || datasetC == null) return;

        int actualA = countActualDefective(datasetA);
        int predictedA = predictDefectiveCount(bClassifier, datasetA);
        int actualBPlus = countActualDefective(datasetBPlus);
        int predictedBPlus = predictDefectiveCount(bClassifier, datasetBPlus);
        int predictedB = predictDefectiveCount(bClassifier, datasetB);
        int actualC = countActualDefective(datasetC);
        int predictedC = predictDefectiveCount(bClassifier, datasetC);

        saveResultsToCsv(actualA, predictedA, actualBPlus, predictedBPlus, predictedB, actualC, predictedC);
    }

    /**
     * Costruisce e configura il classificatore migliore identificato:
     * RandomForest avvolto da un CostSensitiveClassifier.
     */
    private Classifier buildBestClassifier() {
        RandomForest rf = new RandomForest();

        // Chiama direttamente il metodo pubblico e statico da ClassifierBuilder
        CostMatrix costMatrix = ClassifierBuilder.createCostMatrix();

        CostSensitiveClassifier csc = new CostSensitiveClassifier();
        csc.setClassifier(rf);
        csc.setCostMatrix(costMatrix);
        csc.setMinimizeExpectedCost(false);

        return csc;
    }

    /**
     * Carica un dataset da un file CSV.
     */
    private Instances loadDatasetFromCsv(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            LOGGER.severe("File not found: " + path);
            return null;
        }

        CSVLoader loader = new CSVLoader();
        loader.setSource(file);
        Instances data = loader.getDataSet();
        data.setClassIndex(data.numAttributes() - 1);
        return data;
    }

    /**
     * Conta il numero di istanze "buggy" reali in un dataset.
     */
    private int countActualDefective(Instances data) {
        int count = 0;
        int classIndex = data.classIndex();
        // L'indice di "yes" è 1 perché Weka ordina alfabeticamente {"no", "yes"}
        int positiveClassIndexValue = 1;

        for (Instance instance : data) {
            if ((int) instance.value(classIndex) == positiveClassIndexValue) {
                count++;
            }
        }
        return count;
    }

    /**
     * Usa un classificatore addestrato per predire il numero di istanze "buggy".
     */
    private int predictDefectiveCount(Classifier classifier, Instances data) throws Exception {
        int count = 0;
        int positiveClassIndexValue = 1;

        for (Instance instance : data) {
            if ((int) classifier.classifyInstance(instance) == positiveClassIndexValue) {
                count++;
            }
        }
        return count;
    }


    private void saveResultsToCsv(int actualA, int predictedA, int actualBPlus, int predictedBPlus,
                                  int predictedB, int actualC, int predictedC) throws IOException {

        String outputDir = "whatIf/" + projectName.toLowerCase();
        // Assicura che la cartella esista
        Files.createDirectories(Paths.get(outputDir));
        String outputFile = outputDir + "/what_if_results_" + projectName.toLowerCase() + ".csv";

        System.out.println("Saving What-If analysis results to: " + outputFile);

        // Usa try-with-resources per garantire che il file venga chiuso correttamente
        try (FileWriter fileWriter = new FileWriter(outputFile);
             PrintWriter writer = new PrintWriter(fileWriter)) {

            // Scrivi l'header del CSV
            writer.println("Dataset,Description,Actual_Defective_Count,Predicted_Defective_Count");

            // Scrivi i dati per ogni dataset
            writer.printf("A,Total Dataset,%d,%d%n", actualA, predictedA);
            writer.printf("B+,Smelly Methods (Original),%d,%d%n", actualBPlus, predictedBPlus);
            writer.printf("B,Smelly Methods (What-If: No Smells),%s,%d%n", "N/A", predictedB);
            writer.printf("C,Not Smelly Methods,%d,%d%n", actualC, predictedC);
        }

        System.out.println("What-If results saved successfully.");
    }

}