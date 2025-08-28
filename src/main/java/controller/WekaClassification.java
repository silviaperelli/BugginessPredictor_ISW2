package controller;

import model.AcumeMethod;
import model.ClassifierEvaluation;
import model.JavaMethod;
import model.WekaClassifier;
import utils.PrintUtils;
import utils.PrintUtils.Console;
import utils.WekaUtils;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WekaClassification {

    private static final Logger LOGGER = Logger.getLogger(WekaClassification.class.getName());
    private final String projectName;
    private final List<JavaMethod> allMethods;

    private final List<ClassifierEvaluation> cvEvaluationResults;
    private final List<ClassifierEvaluation> temporalEvaluationResults;

    public WekaClassification(String projectName, List<JavaMethod> allMethods) {
        LOGGER.setLevel(Level.SEVERE);
        this.projectName = projectName;
        this.allMethods = allMethods;
        this.cvEvaluationResults = new ArrayList<>();
        this.temporalEvaluationResults = new ArrayList<>();
    }

    public void execute() {
        Console.info("--- Starting WEKA analysis for project: " + projectName + "---");
        try {
            executeCrossValidation();
            executeTemporalValidation();
            saveAllResults();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "A critical error occurred during WEKA analysis", e);
        }
        Console.info("--- WEKA analysis finished for project: " + projectName + "---");
    }

    public void executeCrossValidation() {
        Console.info("Starting Cross Validation");
        try {
            final int numRuns = 10;
            final int numFolds = 10;
            prepareCrossValidationData(numRuns, numFolds);
            runClassificationOnFolds(numRuns, numFolds);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred during Cross-Validation analysis", e);
        }
    }

    public void executeTemporalValidation() {
        Console.info("Starting Temporal Validation");
        try {
            int numIterations = prepareTemporalData();
            runClassificationOnTemporal(numIterations);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred during Temporal Validation analysis", e);
        }
    }

    private void prepareCrossValidationData(int numRuns, int numFolds) throws IOException {
        Console.info("Preparing data for cross-validation...");
        List<JavaMethod> methodsForCv;
        if ("SYNCOPE".equalsIgnoreCase(this.projectName)) {
            int totalReleases = (int) allMethods.stream().map(m -> m.getRelease().getId()).distinct().count();
            int releasesToConsider = (int) Math.ceil(totalReleases * 0.35);
            methodsForCv = allMethods.stream().filter(m -> m.getRelease().getId() <= releasesToConsider).collect(Collectors.toList());
        } else {
            methodsForCv = this.allMethods;
        }
        Instances fullDataset = WekaUtils.buildInstances(methodsForCv, projectName + "_full");
        fullDataset.setClassIndex(fullDataset.numAttributes() - 1);
        ArffSaver saver = new ArffSaver();
        for (int run = 1; run <= numRuns; run++) {
            Random rand = new Random(run);
            Instances randData = new Instances(fullDataset);
            randData.randomize(rand);
            for (int fold = 0; fold < numFolds; fold++) {
                Instances train = randData.trainCV(numFolds, fold, rand);
                Instances test = randData.testCV(numFolds, fold);
                String iterDir = String.format("arffFiles/%s/cv/run_%d/fold_%d", projectName.toLowerCase(), run, fold);
                Files.createDirectories(Paths.get(iterDir));
                saver.setInstances(train);
                saver.setFile(new File(iterDir + "/training.arff"));
                saver.writeBatch();
                saver.setInstances(test);
                saver.setFile(new File(iterDir + "/testing.arff"));
                saver.writeBatch();
            }
        }
        Console.info("Cross-validation data preparation complete.");
    }

    private int prepareTemporalData() throws IOException {
        Console.info("Preparing data for temporal validation...");
        int numReleases = (int) allMethods.stream().map(m -> m.getRelease().getId()).distinct().count();
        int lastIteration = 0;
        for (int i = 1; i < numReleases; i++) {
            final int currentReleaseId = i;
            List<JavaMethod> trainingMethods;
            if ("SYNCOPE".equalsIgnoreCase(this.projectName)) {
                final int windowSize = 5;
                trainingMethods = allMethods.stream().filter(m -> m.getRelease().getId() > currentReleaseId - windowSize && m.getRelease().getId() <= currentReleaseId).collect(Collectors.toList());
            } else {
                trainingMethods = allMethods.stream().filter(m -> m.getRelease().getId() <= currentReleaseId).collect(Collectors.toList());
            }
            List<JavaMethod> testingMethods = allMethods.stream().filter(m -> m.getRelease().getId() == currentReleaseId + 1).collect(Collectors.toList());
            if (trainingMethods.isEmpty() || testingMethods.isEmpty()) continue;
            String iterDir = String.format("arffFiles/%s/temporal/iteration_%d", projectName.toLowerCase(), i);
            Files.createDirectories(Paths.get(iterDir));
            Instances trainingSet = WekaUtils.buildInstances(trainingMethods, "training");
            Instances testingSet = WekaUtils.buildInstances(testingMethods, "testing");
            ArffSaver saver = new ArffSaver();
            saver.setInstances(trainingSet);
            saver.setFile(new File(iterDir + "/training.arff"));
            saver.writeBatch();
            saver.setInstances(testingSet);
            saver.setFile(new File(iterDir + "/testing.arff"));
            saver.writeBatch();
            lastIteration = i;
        }
        Console.info("Temporal validation data preparation complete.");
        return lastIteration;
    }

    private void runClassificationOnFolds(int numRuns, int numFolds) throws IOException {
        Console.info("Starting classification on CV folds...");
        for (int run = 1; run <= numRuns; run++) {
            Console.info("--- CV Run " + run + " / " + numRuns);
            Map<String, List<AcumeMethod>> aggregatedPredictions = new HashMap<>();

            for (int fold = 0; fold < numFolds; fold++) {
                String dirPath = String.format("arffFiles/%s/cv/run_%d/fold_%d/", projectName.toLowerCase(), run, fold);
                performSingleClassification(dirPath, "cv", run, fold, aggregatedPredictions);
            }

            Console.info("Aggregating predictions and writing ACUME files for Run " + run);
            for (Map.Entry<String, List<AcumeMethod>> entry : aggregatedPredictions.entrySet()) {
                String configName = entry.getKey();
                String finalFileName = String.format("%s_run%d", configName, run);
                PrintUtils.createAcumeFile(projectName, "cv", entry.getValue(), finalFileName);
            }
        }
    }

    private void runClassificationOnTemporal(int numIterations) {
        Console.info("Starting classification on temporal iterations...");
        for (int i = 1; i <= numIterations; i++) {
            String dirPath = String.format("arffFiles/%s/temporal/iteration_%d/", projectName.toLowerCase(), i);
            if (new File(dirPath).exists()) {
                performSingleClassification(dirPath, "temporal", 0, i, null); // null perché non aggreghiamo
            }
        }
    }

    // --- MOTORE DI CLASSIFICAZIONE UNIFICATO ---
    private void performSingleClassification(String dirPath, String validationType, int run, int foldOrIteration, Map<String, List<AcumeMethod>> aggregatedPredictions) {
        try {
            if (!new File(dirPath + "training.arff").exists()) return;
            DataSource trainingSource = new DataSource(dirPath + "training.arff");
            Instances trainingSet = trainingSource.getDataSet();
            trainingSet.setClassIndex(trainingSet.numAttributes() - 1);
            DataSource testingSource = new DataSource(dirPath + "testing.arff");
            Instances testingSet = testingSource.getDataSet();
            testingSet.setClassIndex(testingSet.numAttributes() - 1);
            if (testingSet.isEmpty()) return;

            List<WekaClassifier> classifiersToTest = ClassifierBuilder.buildClassifiers(trainingSet);
            int positiveClassIndex = trainingSet.classAttribute().indexOfValue("yes");
            List<ClassifierEvaluation> resultsList = "cv".equals(validationType) ? this.cvEvaluationResults : this.temporalEvaluationResults;
            int iterationId = "cv".equals(validationType) ? (run - 1) * 10 + foldOrIteration : foldOrIteration;

            for (WekaClassifier wekaConfig : classifiersToTest) {
                try {
                    Classifier classifier = wekaConfig.getClassifier();
                    classifier.buildClassifier(trainingSet);

                    List<AcumeMethod> predictions = getAcumePredictions(classifier, testingSet);
                    String configName = buildClassifierConfigName(wekaConfig);

                    if ("cv".equals(validationType) && aggregatedPredictions != null) {
                        // Se è CV, aggrega le previsioni
                        aggregatedPredictions.computeIfAbsent(configName, k -> new ArrayList<>()).addAll(predictions);
                    } else {
                        // Se è temporale, scrivi subito il file ACUME
                        String fileName = String.format("%s_iter%d", configName, foldOrIteration);
                        PrintUtils.createAcumeFile(projectName, validationType, predictions, fileName);
                    }

                    Evaluation eval = new Evaluation(trainingSet);
                    eval.evaluateModel(classifier, testingSet);
                    if (positiveClassIndex != -1) {
                        resultsList.add(new ClassifierEvaluation(projectName, iterationId, wekaConfig.getName(), wekaConfig.getFeatureSelection(), wekaConfig.getSampling(), wekaConfig.getCostSensitive(), eval.precision(positiveClassIndex), eval.recall(positiveClassIndex), eval.areaUnderROC(positiveClassIndex), eval.kappa(), eval.fMeasure(positiveClassIndex), eval.matthewsCorrelationCoefficient(positiveClassIndex)));
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Could not evaluate classifier {0}", wekaConfig.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed classification for {0}", dirPath);
        }
    }

    // --- METODI HELPER (INVARIATI) ---
    private List<AcumeMethod> getAcumePredictions(Classifier classifier, Instances dataSet) throws Exception {
        List<AcumeMethod> predictions = new ArrayList<>();
        int positiveClassIndex = dataSet.classAttribute().indexOfValue("yes");
        if (positiveClassIndex == -1) positiveClassIndex = 1;
        Attribute locAttribute = dataSet.attribute("LOC");
        if (locAttribute == null) {
            LOGGER.log(Level.SEVERE,"Attribute 'LOC' not found. Cannot create ACUME predictions.");
            return predictions;
        }
        int locIndex = locAttribute.index();

        for (int i = 0; i < dataSet.numInstances(); i++) {
            Instance instance = dataSet.instance(i);
            double[] distribution = classifier.distributionForInstance(instance);
            double predictedProbability = distribution[positiveClassIndex];
            String actualValueLabel = instance.classAttribute().value((int) instance.classValue());
            int size = (int) instance.value(locIndex);
            predictions.add(new AcumeMethod(i, size, predictedProbability, actualValueLabel));
        }
        return predictions;
    }

    private String buildClassifierConfigName(WekaClassifier wekaConfig) {
        StringBuilder nameBuilder = new StringBuilder(wekaConfig.getName());
        if (!"none".equals(wekaConfig.getFeatureSelection())) nameBuilder.append("_BestFirst");
        if (!"none".equals(wekaConfig.getSampling())) nameBuilder.append("_").append(wekaConfig.getSampling());
        if (!"none".equals(wekaConfig.getCostSensitive())) nameBuilder.append("_CostSensitive");
        return nameBuilder.toString();
    }

    private void saveAllResults() throws IOException {
        if (!this.cvEvaluationResults.isEmpty()) {
            Console.info("Saving cross-validation evaluation results...");
            PrintUtils.printEvaluationResults(projectName, this.cvEvaluationResults, "_cv");
        }
        if (!this.temporalEvaluationResults.isEmpty()) {
            Console.info("Saving temporal validation evaluation results...");
            PrintUtils.printEvaluationResults(projectName, this.temporalEvaluationResults, "_temporal");
        }
    }
}