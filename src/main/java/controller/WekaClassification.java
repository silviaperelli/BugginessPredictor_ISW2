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

/**
 * Gestisce l'intera fase di classificazione con Weka.
 * Prepara i dati per la validazione, esegue i test e salva i risultati delle performance dei modelli
 */
public class WekaClassification {

    private static final Logger LOGGER = Logger.getLogger(WekaClassification.class.getName());
    private final String projectName;
    private final List<JavaMethod> allMethods;

    // Liste per memorizzare i risultati delle due diverse tecniche di validazione
    private final List<ClassifierEvaluation> cvEvaluationResults;
    private final List<ClassifierEvaluation> temporalEvaluationResults;

    public WekaClassification(String projectName, List<JavaMethod> allMethods) {
        LOGGER.setLevel(Level.SEVERE);
        this.projectName = projectName;
        this.allMethods = allMethods;
        this.cvEvaluationResults = new ArrayList<>();
        this.temporalEvaluationResults = new ArrayList<>();
    }

    /**
     * Metodo principale che orchestra l'esecuzione delle due strategie di validazione e il salvataggio finale dei risultati
     */
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

    /**
     * Esegue la Cross-Validation
     */
    public void executeCrossValidation() {
        Console.info("Starting Cross Validation");
        try {
            final int numRuns = 10;
            final int numFolds = 10;
            // Prepara i file .arff per ogni run e ogni fold
            prepareCrossValidationData(numRuns, numFolds);
            // Esegue la classificazione su ogni set di dati preparato
            runClassificationOnFolds(numRuns, numFolds);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred during Cross-Validation analysis", e);
        }
    }

    /**
     * Esegue la validazione Walk-Forward
     */
    public void executeTemporalValidation() {
        Console.info("Starting Temporal Validation");
        try {
            // Prepara i file .arff per ogni iterazione
            int numIterations = prepareTemporalData();
            // Esegue la classificazione su ogni set di dati
            runClassificationOnTemporal(numIterations);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred during Temporal Validation analysis", e);
        }
    }

    /**
     * Prepara i dati per la Cross-Validation. Crea 10x10 = 100 coppie di file (training.arff, testing.arff)
     * randomizzando il dataset a ogni run per garantire la robustezza statistica
     */
    private void prepareCrossValidationData(int numRuns, int numFolds) throws IOException {
        Console.info("Preparing data for cross-validation...");
        List<JavaMethod> methodsForCv;
        methodsForCv = this.allMethods;

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

                // Salva i set di training e testing come file .arff
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

    /**
     * Prepara i dati per la validazione Walk-Forward
     */
    private int prepareTemporalData() throws IOException {
        Console.info("Preparing data for temporal validation...");
        int numReleases = (int) allMethods.stream().map(m -> m.getRelease().getId()).distinct().count();
        int lastIteration = 0;

        // Itera su tutte le release che possono servire da training set
        for (int i = 1; i < numReleases; i++) {
            final int currentReleaseId = i;
            List<JavaMethod> trainingMethods;
            if ("SYNCOPE".equalsIgnoreCase(this.projectName)) {
                // Strategia a finestra mobile: usa solo le ultime 5 release per il training
                final int windowSize = 5;
                trainingMethods = allMethods.stream().filter(m -> m.getRelease().getId() > currentReleaseId - windowSize && m.getRelease().getId() <= currentReleaseId).collect(Collectors.toList());
            } else {
                // Strategia standard: usa tutte le release passate per il training
                trainingMethods = allMethods.stream().filter(m -> m.getRelease().getId() <= currentReleaseId).collect(Collectors.toList());
            }
            // Il testing set è sempre la release successiva
            List<JavaMethod> testingMethods = allMethods.stream().filter(m -> m.getRelease().getId() == currentReleaseId + 1).collect(Collectors.toList());
            if (trainingMethods.isEmpty() || testingMethods.isEmpty()) continue;

            // Salva i dati nei file .arff
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

    /**
     * Esegue il ciclo di classificazione per la Cross-Validation
     */
    private void runClassificationOnFolds(int numRuns, int numFolds) throws IOException {
        Console.info("Starting classification on CV folds...");
        for (int run = 1; run <= numRuns; run++) {
            Console.info("--- CV Run " + run + " / " + numRuns);

            // Mappa per aggregare le previsioni di ogni fold all'interno di un singolo run
            Map<String, List<AcumeMethod>> aggregatedPredictions = new HashMap<>();

            for (int fold = 0; fold < numFolds; fold++) {
                String dirPath = String.format("arffFiles/%s/cv/run_%d/fold_%d/", projectName.toLowerCase(), run, fold);
                performSingleClassification(dirPath, "cv", run, fold, aggregatedPredictions);
            }

            // A fine run, scrive un file ACUME per ogni configurazione di classificatore
            Console.info("Aggregating predictions and writing ACUME files for Run " + run);
            for (Map.Entry<String, List<AcumeMethod>> entry : aggregatedPredictions.entrySet()) {
                String configName = entry.getKey();
                String finalFileName = String.format("%s_run%d", configName, run);
                PrintUtils.createAcumeFile(projectName, "cv", entry.getValue(), finalFileName);
            }
        }
    }

    /**
     * Esegue il ciclo di classificazione per la validazione temporale.
     * Itera su ogni coppia di file training/testing preparata in precedenza e lancia la classificazione
     */
    private void runClassificationOnTemporal(int numIterations) {
        Console.info("Starting classification on temporal iterations...");
        for (int i = 1; i <= numIterations; i++) {
            String dirPath = String.format("arffFiles/%s/temporal/iteration_%d/", projectName.toLowerCase(), i);
            if (new File(dirPath).exists()) {
                performSingleClassification(dirPath, "temporal", 0, i, null); // null perché non aggreghiamo
            }
        }
    }

    /**
     * Carica i dati, costruisce i classificatori, li esegue e delega la registrazione dei risultati
     */
    private void performSingleClassification(String dirPath, String validationType, int run, int foldOrIteration, Map<String, List<AcumeMethod>> aggregatedPredictions) {
        try {
            if (!new File(dirPath + "training.arff").exists()) return;

            // Carica i dataset di training e testing da file .arff
            DataSource trainingSource = new DataSource(dirPath + "training.arff");
            Instances trainingSet = trainingSource.getDataSet();
            trainingSet.setClassIndex(trainingSet.numAttributes() - 1);

            DataSource testingSource = new DataSource(dirPath + "testing.arff");
            Instances testingSet = testingSource.getDataSet();
            testingSet.setClassIndex(testingSet.numAttributes() - 1);

            if (testingSet.isEmpty()) return;

            // Costruisce la lista di tutti i classificatori da testare
            List<WekaClassifier> classifiersToTest = ClassifierBuilder.buildClassifiers(trainingSet);
            int positiveClassIndex = trainingSet.classAttribute().indexOfValue("yes");

            // Seleziona la lista corretta dove salvare i risultati
            List<ClassifierEvaluation> resultsList = "cv".equals(validationType) ? this.cvEvaluationResults : this.temporalEvaluationResults;
            // Calcola un ID univoco per l'iterazione corrente
            int iterationId = "cv".equals(validationType) ? (run - 1) * 10 + foldOrIteration : foldOrIteration;

            // Itera su ogni configurazione di classificatore e la valuta
            for (WekaClassifier wekaConfig : classifiersToTest) {
                EvalContext ctx = new EvalContext(trainingSet, testingSet, positiveClassIndex, foldOrIteration, iterationId);
                evaluateAndRecordClassifier(wekaConfig, ctx, validationType, resultsList, aggregatedPredictions);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, () -> "Failed classification for " + dirPath);
        }
    }

    /**
     * Esegue l'addestramento e la valutazione di un singolo classificatore.
     * Calcola le metriche e salva il risultato
     */
    private void evaluateAndRecordClassifier(WekaClassifier wekaConfig, EvalContext ctx, String validationType,
                                             List<ClassifierEvaluation> resultsList,
                                             Map<String, List<AcumeMethod>> aggregatedPredictions) {
        try {
            Classifier classifier = wekaConfig.getClassifier();
            // Addestra il classificatore sul training set
            classifier.buildClassifier(ctx.trainingSet);

            // Ottiene le previsioni di probabilità per i file ACUME
            List<AcumeMethod> predictions = getAcumePredictions(classifier, ctx.testingSet);
            String configName = buildClassifierConfigName(wekaConfig);

            // Gestisce le previsioni: le aggrega (per CV) o le scrive subito (per temporal)
            if ("cv".equals(validationType) && aggregatedPredictions != null) {
                aggregatedPredictions.computeIfAbsent(configName, k -> new ArrayList<>()).addAll(predictions);
            } else {
                String fileName = String.format("%s_iter%d", configName, ctx.foldOrIteration);
                PrintUtils.createAcumeFile(projectName, validationType, predictions, fileName);
            }

            // Esegue la valutazione formale con Weka per calcolare le metriche
            Evaluation eval = new Evaluation(ctx.trainingSet);
            eval.evaluateModel(classifier, ctx.testingSet);

            // Se la classe "buggy" ('yes') esiste, raccoglie e salva le metriche
            if (ctx.positiveClassIndex != -1) {
                ClassifierEvaluation.EvaluationMetrics metrics = new ClassifierEvaluation.EvaluationMetrics(
                        eval.precision(ctx.positiveClassIndex),
                        eval.recall(ctx.positiveClassIndex),
                        eval.areaUnderROC(ctx.positiveClassIndex),
                        eval.kappa(),
                        eval.fMeasure(ctx.positiveClassIndex),
                        eval.matthewsCorrelationCoefficient(ctx.positiveClassIndex)
                );

                resultsList.add(new ClassifierEvaluation(projectName, ctx.iterationId, wekaConfig.getName(),
                        wekaConfig.getFeatureSelection(), wekaConfig.getSampling(), wekaConfig.getCostSensitive(),
                        metrics));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "Could not evaluate classifier " + wekaConfig.getName());
        }
    }

    /**
     * Ottiene le previsioni di probabilità per ogni istanza, necessarie per i file ACUME.
     * Restituisce una lista di oggetti `AcumeMethod` contenenti ID, dimensione e probabilità predetta
     */
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

    /**
     * Costruisce un nome di file standardizzato basato sulla configurazione del classificatore
     */
    private String buildClassifierConfigName(WekaClassifier wekaConfig) {
        StringBuilder nameBuilder = new StringBuilder(wekaConfig.getName());
        if (!"none".equals(wekaConfig.getFeatureSelection())) nameBuilder.append("_BestFirst");
        if (!"none".equals(wekaConfig.getSampling())) nameBuilder.append("_").append(wekaConfig.getSampling());
        if (!"none".equals(wekaConfig.getCostSensitive())) nameBuilder.append("_CostSensitive");
        return nameBuilder.toString();
    }

    /**
     * Salva i risultati delle performance dei classificatori in due file CSV separati,
     * uno per la Cross-Validation e uno per la validazione Temporale
     */
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

    /**
     * Classe interna per passare un gruppo di parametri
     */
    private static class EvalContext {
        Instances trainingSet;
        Instances testingSet;
        int positiveClassIndex;
        int foldOrIteration;
        int iterationId;

        EvalContext(Instances training, Instances testing, int posIndex, int fold, int iter) {
            this.trainingSet = training;
            this.testingSet = testing;
            this.positiveClassIndex = posIndex;
            this.foldOrIteration = fold;
            this.iterationId = iter;
        }
    }
}