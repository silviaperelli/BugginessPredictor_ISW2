package controller; // Assicurati che il package sia corretto

import utils.PrintUtils;
import utils.WekaUtils;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class WhatIfAnalysis {
    private static final Logger LOGGER = Logger.getLogger(WhatIfAnalysis.class.getName());
    private final String project;
    private final String projectLower;
    private final Instances datasetA;


    public WhatIfAnalysis(String projectName) throws Exception {
        String datasetCsvPath = String.format("csvFiles/%s/Dataset.csv", projectName.toLowerCase());
        LOGGER.info("Loading full dataset from CSV: " + datasetCsvPath);

        Instances rawData = WekaUtils.loadInstancesFromCsv(datasetCsvPath);

        Remove removeFilter = new Remove();
        removeFilter.setAttributeIndices("1"); // Rimuove la prima colonna (FullyQualifiedName)
        removeFilter.setInputFormat(rawData);
        Instances fulldataset = Filter.useFilter(rawData, removeFilter);

        if (fulldataset.classIndex() == -1) {
            fulldataset.setClassIndex(fulldataset.numAttributes() - 1);
        }

        this.project = projectName.toUpperCase();
        this.projectLower = projectName.toLowerCase();
        this.datasetA = new Instances(fulldataset);
    }

    public static void main(String[] args) throws Exception {
        Logger.getLogger("").setLevel(Level.SEVERE);
        String projectToAnalyze = "BOOKKEEPER";
        WhatIfAnalysis analysis = new WhatIfAnalysis(projectToAnalyze);
        analysis.execute();
    }

    public void execute() throws Exception {
        LOGGER.info("--- Starting What-If Analysis ---");

        // --- Creare i dataset B+, C, e B ---
        LOGGER.info("Creating sub-datasets based on NSmells...");

        // --- B+: Porzione di A con NSmells > 0
        Instances datasetBPlus = filterBySmell(this.datasetA, 0, "greater");

        // --- C: Porzione di A con NSmells = 0
        Instances datasetC = filterBySmell(this.datasetA, 0, "equals");

        // --- B: Una copia di B+ ma con NSmells manipolato a 0
        Instances datasetB = new Instances(datasetBPlus);
        int nSmellsIndex = datasetB.attribute("NumCodeSmells").index();
        if (nSmellsIndex == -1) throw new IllegalStateException("Feature 'NSmells' not found.");
        datasetB.forEach(instance -> instance.setValue(nSmellsIndex, 0));

        // --- Addestrare BClassifier su A (BClassifierA) ---
        LOGGER.info("Training BClassifier on the full dataset A...");
        Classifier bClassifierA = new RandomForest();
        bClassifierA.buildClassifier(this.datasetA);

        // --- Predire e contare Actual/Estimated su A, B, B+, C ---
        LOGGER.info("Counting actual and estimated bugs on all datasets...");

        // Calcola i valori "Actual"
        int actualA = countActualBugs(this.datasetA);
        int actualBPlus = countActualBugs(datasetBPlus);
        int actualC = countActualBugs(datasetC);
        // Nota: B e B+ hanno gli stessi metodi, quindi gli Actual sono identici.
        int actualB = actualBPlus;

        // Calcola i valori "Estimated"
        int estimatedA = countBuggyPredictions(bClassifierA, this.datasetA);
        int estimatedBPlus = countBuggyPredictions(bClassifierA, datasetBPlus);
        int estimatedC = countBuggyPredictions(bClassifierA, datasetC);
        int estimatedB = countBuggyPredictions(bClassifierA, datasetB);

        // --- Passo 13: Salva i risultati in un file CSV ---
        String outputDir = String.format("whatIf/%s/", this.project.toLowerCase());
        String outputFile = outputDir + "whatIf_results_" + project.toLowerCase() + ".csv";
        printWhatIfResultsToCsv(outputFile,
                actualA, estimatedA,
                actualBPlus, estimatedBPlus,
                actualB, estimatedB,
                actualC, estimatedC);

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


    // Conta le istanze predette come "buggy" in un dataset.
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

    // Conta le istanze che sono effettivamente "buggy" in un dataset.
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

    public static void printWhatIfResultsToCsv(String filePath, int... params) throws IOException {
        File file = new File(filePath);    file.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Dataset,Type,Count");        writer.printf("A,Actual,%d%n", params[0]);
            writer.printf("A,Estimated,%d%n", params[1]);        writer.printf("B+,Actual,%d%n", params[2]);
            writer.printf("B+,Estimated,%d%n", params[3]);        writer.printf("B,Actual,%d%n", params[4]);
            writer.printf("B,Estimated,%d%n", params[5]);        writer.printf("C,Actual,%d%n", params[6]);
            writer.printf("C,Estimated,%d%n", params[7]);    }
    }

}