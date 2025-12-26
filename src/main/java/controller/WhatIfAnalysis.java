package controller;

import utils.WekaUtils;
import utils.PrintUtils.Console;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import weka.core.converters.CSVSaver;
import java.io.File;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementa l'analisi "What-If" per stimare l'impatto della rimozione dei code smell sulla predizione dei bug
 */
public class WhatIfAnalysis {
    private static final Logger LOGGER = Logger.getLogger(WhatIfAnalysis.class.getName());
    private final String project;
    private final String projectLower;
    private final Instances datasetA; // Il dataset completo del progetto

    /**
     * Costruttore che carica e prepara il dataset principale (A) per l'analisi
     */
    public WhatIfAnalysis(String projectName) throws Exception {
        Logger.getLogger("").setLevel(Level.SEVERE);

        String datasetCsvPath = String.format("csvFiles/%s/Dataset.csv", projectName.toLowerCase());
        Console.info("Loading full dataset from CSV: " + datasetCsvPath);

        // Carica i dati grezzi dal CSV
        Instances rawData = WekaUtils.loadInstancesFromCsv(datasetCsvPath);

        // Rimuove la colonna con il nome del metodo
        Remove removeFilter = new Remove();
        removeFilter.setAttributeIndices("1");
        removeFilter.setInputFormat(rawData);
        Instances fulldataset = Filter.useFilter(rawData, removeFilter);

        // Imposta l'ultima colonna ('IsBuggy') come classe da predire
        if (fulldataset.classIndex() == -1) {
            fulldataset.setClassIndex(fulldataset.numAttributes() - 1);
        }

        this.project = projectName.toUpperCase();
        this.projectLower = projectName.toLowerCase();
        this.datasetA = new Instances(fulldataset);
    }

    /**
     * Metodo principale che orchestra l'intera analisi What-If
     */
    public void execute() throws Exception {
        Console.info("--- Starting What-If Analysis ---");

        // Crea i sotto-dataset B+, C e B
        Console.info("Creating sub-datasets based on NSmells...");

        // B+: Porzione di A con NSmells > 0
        Instances datasetBPlus = filterBySmell(this.datasetA, 0, "greater");

        // C: Porzione di A con NSmells = 0
        Instances datasetC = filterBySmell(this.datasetA, 0, "equals");

        // B: Una copia di B+ ma con NSmells manipolato a 0
        Instances datasetB = new Instances(datasetBPlus);
        int nSmellsIndex = datasetB.attribute("NumCodeSmells").index();
        if (nSmellsIndex == -1) throw new IllegalStateException("Feature 'NSmells' not found.");
        datasetB.forEach(instance -> instance.setValue(nSmellsIndex, 0));

        // Salvataggio dei dataset B, B+, C su file
        String outputDir = String.format("whatIf/%s/", this.project.toLowerCase());
        new File(outputDir).mkdirs(); // Crea la directory se non esiste

        CSVSaver saver = new CSVSaver();

        // Salva B.csv
        saver.setInstances(datasetB);
        saver.setFile(new File(outputDir + "B.csv"));
        saver.writeBatch();

        // Salva B_plus.csv
        saver.setInstances(datasetBPlus);
        saver.setFile(new File(outputDir + "B_plus.csv"));
        saver.writeBatch();

        // Salva C.csv
        saver.setInstances(datasetC);
        saver.setFile(new File(outputDir + "C.csv"));
        saver.writeBatch();
        Console.info("Intermediate datasets B, B+, C saved successfully.");

        // Addestra BClassifier su A
        Console.info("Training BClassifier on the full dataset A...");
        Classifier bClassifierA = new RandomForest();
        bClassifierA.buildClassifier(this.datasetA);

        // Esegue le predizioni su tutti i dataset e conta i bug reali (Actual) e predetti (Estimated)
        Console.info("Counting actual and estimated bugs on all datasets...");

        int actualA = countActualBugs(this.datasetA);
        int actualBPlus = countActualBugs(datasetBPlus);
        int actualC = countActualBugs(datasetC);
        // B e B+ hanno gli stessi metodi, quindi i bug reali sono gli stessi
        int actualB = actualBPlus;

        int estimatedA = countBuggyPredictions(bClassifierA, this.datasetA);
        int estimatedBPlus = countBuggyPredictions(bClassifierA, datasetBPlus);
        int estimatedC = countBuggyPredictions(bClassifierA, datasetC);
        int estimatedB = countBuggyPredictions(bClassifierA, datasetB);

        // Salva i risultati in un file CSV
        String outputFile = outputDir + "whatIf_results_" + project.toLowerCase() + ".csv";
        printWhatIfResultsToCsv(outputFile,
                actualA, estimatedA,
                actualBPlus, estimatedBPlus,
                actualB, estimatedB,
                actualC, estimatedC);

        // Analizza e stampa i risultati finali
        analyzeFinalResults(actualA, estimatedBPlus, estimatedB);
    }

    // Filtra un dataset basato sul valore della feature "NSmells"
    private Instances filterBySmell(Instances data, double value, String comparison) {
        int attrIndex = data.attribute("NumCodeSmells").index();
        if (attrIndex == -1) {
            throw new IllegalArgumentException("Attribute not found: " + "NumCodeSmells");
        }

        Instances filteredData = new Instances(data, 0);

        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            double currentValue = inst.value(attrIndex);
            boolean conditionMet = false;

            switch (comparison) {
                case "equals":
                    if (currentValue == value) conditionMet = true;
                    break;
                case "greater":
                    if (currentValue > value) conditionMet = true;
                    break;
                case "less":
                    if (currentValue < value) conditionMet = true;
                    break;
                default:
                    throw new IllegalArgumentException("Comparison type not supported: " + comparison);
            }

            if (conditionMet) {
                filteredData.add(inst);
            }
        }
        return filteredData;
    }


    // Conta le istanze predette come "buggy" in un dataset
    private int countBuggyPredictions(Classifier classifier, Instances data) throws Exception {
        if (data.isEmpty()) return 0;
        int buggyCount = 0;
        int buggyClassIndex = data.classAttribute().indexOfValue("yes");
        for (int i = 0; i < data.numInstances(); i++) {
            if (classifier.classifyInstance(data.instance(i)) == buggyClassIndex) {
                buggyCount++;
            }
        }
        return buggyCount;
    }

    // Conta le istanze che sono effettivamente "buggy" in un dataset
    private int countActualBugs(Instances data) {
        if (data.isEmpty()) return 0;
        int actualBuggyCount = 0;
        int buggyClassIndex = data.classAttribute().indexOfValue("yes");
        for (int i = 0; i < data.numInstances(); i++) {
            if (data.instance(i).classValue() == buggyClassIndex) {
                actualBuggyCount++;
            }
        }
        return actualBuggyCount;
    }

    // Scrive i risultati numerici dell'analisi What-If in un file CSV formattato
    public static void printWhatIfResultsToCsv(String filePath, int... params) throws IOException {
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Dataset,Type,Count");        writer.printf("A,Actual,%d%n", params[0]);
            writer.printf("A,Estimated,%d%n", params[1]);        writer.printf("B+,Actual,%d%n", params[2]);
            writer.printf("B+,Estimated,%d%n", params[3]);        writer.printf("B,Actual,%d%n", params[4]);
            writer.printf("B,Estimated,%d%n", params[5]);        writer.printf("C,Actual,%d%n", params[6]);
            writer.printf("C,Estimated,%d%n", params[7]);    }
    }

    /**
     * Analizza i risultati chiave e stampa a console una sintesi leggibile.
     * Calcola il numero di "bug prevenibili" e la loro incidenza percentuale
     */
    private void analyzeFinalResults(int totalActualDefects, int predictedDefectsWithSmells, int predictedDefectsWithoutSmells) {
        Console.info("\n--- Final Analysis ---");
        Console.info("Predicted defects on smelly methods (B+): " + predictedDefectsWithSmells);
        Console.info("Predicted defects if smells were removed (B): " + predictedDefectsWithoutSmells);
        int preventableDefects = predictedDefectsWithSmells - predictedDefectsWithoutSmells;
        Console.info("\n>> Estimated number of preventable defects by removing code smells: " + preventableDefects);
        if (totalActualDefects > 0) {
            double percentageOfTotal = ((double) preventableDefects / totalActualDefects) * 100;
            Console.info(">> This represents " + String.format("%.3f", percentageOfTotal)  + "% of the total actual defects in the project.");
        }
        if (predictedDefectsWithSmells > 0) {
            double percentageOfSmelly = ((double) preventableDefects / predictedDefectsWithSmells) * 100;
            Console.info(">> This represents a " + String.format("%.3f", percentageOfSmelly)  + "% reduction in defects among the methods that were originally smelly.");
        }
        Console.info("---------------------\n");
    }
}