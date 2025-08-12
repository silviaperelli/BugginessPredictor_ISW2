package controller;

import model.ClassifierEvaluation;
import model.JavaMethod;
import model.WekaClassifier;
import utils.PrintUtils;
import utils.WekaUtils;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WekaClassification {

    private static final Logger LOGGER = Logger.getLogger(WekaClassification.class.getName());
    private final String projectName;
    private final List<JavaMethod> allMethods;

    // Liste separate per i risultati delle due tecniche di validazione
    private final List<ClassifierEvaluation> cvEvaluationResults;
    private final List<ClassifierEvaluation> temporalEvaluationResults;

    public WekaClassification(String projectName, List<JavaMethod> allMethods) {
        this.projectName = projectName;
        this.allMethods = allMethods;
        this.cvEvaluationResults = new ArrayList<>();
        this.temporalEvaluationResults = new ArrayList<>();
    }

    public void execute() {
        LOGGER.log(Level.INFO, "--- Starting WEKA analysis for project: {0} ---", projectName);
        try {
            // --- ESECUZIONE DELLE DUE VALIDAZIONI ---

            // 1. Esegui la 10-times 10-fold Cross-Validation
            //runCrossValidationAnalysis();

            // 2. Esegui la Validazione Temporale (Walk-Forward o Sliding Window)
            runTemporalValidation();

            // 3. Salva entrambi i set di risultati in file separati
            saveResults();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred during WEKA analysis", e);
        }
        LOGGER.log(Level.INFO, "--- WEKA analysis finished for project: {0} ---", projectName);
    }

    // --- METODO PER LA 10-TIMES 10-FOLD CROSS-VALIDATION (IL TUO CODICE ORIGINALE, LEGGERMENTE ADATTATO) ---
    private void runCrossValidationAnalysis() throws Exception {
        LOGGER.log(Level.INFO, "\n\n--- [STARTING CROSS-VALIDATION ANALYSIS] ---");

        // Per la CV, usiamo un sottoinsieme se il progetto è SYNCPOPE per gestire le dimensioni
        List<JavaMethod> methodsForCv;
        if ("SYNCOPE".equalsIgnoreCase(this.projectName)) {
            LOGGER.info("Project is SYNCOPE, using the first 35% of releases for Cross-Validation to manage dataset size.");
            int totalReleases = allMethods.stream().mapToInt(m -> m.getRelease().getId()).max().orElse(0);
            int releasesToConsider = (int) Math.ceil(totalReleases * 0.35);
            methodsForCv = allMethods.stream()
                    .filter(m -> m.getRelease().getId() <= releasesToConsider)
                    .collect(Collectors.toList());
        } else {
            methodsForCv = this.allMethods;
        }

        Instances fullDataset = WekaUtils.buildInstances(methodsForCv, projectName + "-full-dataset");
        fullDataset.setClassIndex(fullDataset.numAttributes() - 1);
        LOGGER.log(Level.INFO, "Dataset for CV built with {0} instances.", fullDataset.numInstances());

        final int numRepetitions = "BOOKKEEPER".equalsIgnoreCase(this.projectName) ? 10 : 1;
        int numFolds = 10;

        // ... Il resto della logica di questo metodo è identico al tuo codice precedente ...
        int positiveClassIndex = fullDataset.classAttribute().indexOfValue("yes");
        List<WekaClassifier> classifiersToTest = ClassifierBuilder.buildClassifiers(fullDataset);
        int totalExperiments = classifiersToTest.size();
        int currentExperiment = 0;

        for (WekaClassifier wekaConfig : classifiersToTest) {
            currentExperiment++;
            // ... logica per SMOTE e stampe ...

            for (int i = 1; i <= numRepetitions; i++) {
                System.out.print(String.format("    -> Repetition %d/%d... ", i, numRepetitions));

                Evaluation eval = new Evaluation(fullDataset);
                eval.crossValidateModel(wekaConfig.getClassifier(), fullDataset, numFolds, new Random(i));

                System.out.println("DONE.");
                ClassifierEvaluation result = new ClassifierEvaluation(
                        projectName, i, wekaConfig.getName(),
                        wekaConfig.getFeatureSelection(), wekaConfig.getSampling(), wekaConfig.getCostSensitive(),
                        eval.precision(positiveClassIndex), eval.recall(positiveClassIndex),
                        eval.areaUnderROC(positiveClassIndex), eval.kappa(),
                        eval.fMeasure(positiveClassIndex), eval.matthewsCorrelationCoefficient(positiveClassIndex)
                );
                this.cvEvaluationResults.add(result); // Salva nella lista della CV
            }
        }
    }

    // --- NUOVO METODO PER LA VALIDAZIONE TEMPORALE ---
    private void runTemporalValidation() throws Exception {
        LOGGER.log(Level.INFO, "\n\n--- [STARTING TEMPORAL VALIDATION ANALYSIS] ---");

        int totalReleases = allMethods.stream().mapToInt(m -> m.getRelease().getId()).max().orElse(0);

        if (this.projectName.equalsIgnoreCase("BOOKKEEPER")) {
            LOGGER.info("Applying Walk-Forward validation for BOOKKEEPER.");
            for (int i = 1; i < totalReleases; i++) {
                final int lastTrainingReleaseId = i;
                List<JavaMethod> trainingMethods = allMethods.stream()
                        .filter(m -> m.getRelease().getId() <= lastTrainingReleaseId)
                        .collect(Collectors.toList());
                List<JavaMethod> testingMethods = allMethods.stream()
                        .filter(m -> m.getRelease().getId() == (lastTrainingReleaseId + 1))
                        .collect(Collectors.toList());
                runSingleTemporalIteration(i, trainingMethods, testingMethods);
            }
        } else if (this.projectName.equalsIgnoreCase("SYNCOPE")) {
            final int windowSize = 5;
            LOGGER.log(Level.INFO, "Applying Sliding Window validation for SYNCOPE with windowSize = {0}.", windowSize);
            for (int i = 1; i < totalReleases; i++) {
                final int lastTrainingReleaseId = i;
                List<JavaMethod> trainingMethods = allMethods.stream()
                        .filter(m -> {
                            int releaseId = m.getRelease().getId();
                            return releaseId > lastTrainingReleaseId - windowSize && releaseId <= lastTrainingReleaseId;
                        })
                        .collect(Collectors.toList());
                List<JavaMethod> testingMethods = allMethods.stream()
                        .filter(m -> m.getRelease().getId() == (lastTrainingReleaseId + 1))
                        .collect(Collectors.toList());
                runSingleTemporalIteration(i, trainingMethods, testingMethods);
            }
        }
    }

    // --- NUOVO METODO HELPER PER LA VALIDAZIONE TEMPORALE ---
    private void runSingleTemporalIteration(int iterationNum, List<JavaMethod> trainingMethods, List<JavaMethod> testingMethods) throws Exception {
        if (trainingMethods.isEmpty() || testingMethods.isEmpty()) {
            LOGGER.log(Level.INFO, "Skipping temporal iteration {0}: training or testing set is empty.", iterationNum);
            return;
        }

        Instances trainingSet = WekaUtils.buildInstances(trainingMethods, projectName + "-Training-" + iterationNum);
        Instances testingSet = WekaUtils.buildInstances(testingMethods, projectName + "-Testing-" + iterationNum);

        trainingSet.setClassIndex(trainingSet.numAttributes() - 1);
        testingSet.setClassIndex(testingSet.numAttributes() - 1);

        int positiveClassIndex = trainingSet.classAttribute().indexOfValue("yes");
        if (positiveClassIndex == -1) {
            LOGGER.log(Level.WARNING, "Skipping temporal iteration {0}: class 'yes' not found in training data.", iterationNum);
            return;
        }

        int numBuggyInstances = 0;
        for (int j = 0; j < trainingSet.numInstances(); j++) {
            if (trainingSet.get(j).classValue() == positiveClassIndex) numBuggyInstances++;
        }
        final int SMOTE_MIN_INSTANCES = 6;

        LOGGER.log(Level.INFO, "--- Temporal Iteration {0}: Training on {1} instances ({2} buggy), Testing on {3} instances ---",
                new Object[]{iterationNum, trainingSet.numInstances(), numBuggyInstances, testingSet.numInstances()});

        List<WekaClassifier> classifiersToTest = ClassifierBuilder.buildClassifiers(trainingSet);

        for (WekaClassifier wekaConfig : classifiersToTest) {
            if (wekaConfig.getSampling().equals("SMOTE") && numBuggyInstances < SMOTE_MIN_INSTANCES) {
                continue;
            }

            wekaConfig.getClassifier().buildClassifier(trainingSet);
            Evaluation eval = new Evaluation(trainingSet);
            eval.evaluateModel(wekaConfig.getClassifier(), testingSet);

            ClassifierEvaluation result = new ClassifierEvaluation(
                    projectName, iterationNum, wekaConfig.getName(),
                    wekaConfig.getFeatureSelection(), wekaConfig.getSampling(), wekaConfig.getCostSensitive(),
                    eval.precision(positiveClassIndex), eval.recall(positiveClassIndex),
                    eval.areaUnderROC(positiveClassIndex), eval.kappa(),
                    eval.fMeasure(positiveClassIndex), eval.matthewsCorrelationCoefficient(positiveClassIndex)
            );
            this.temporalEvaluationResults.add(result); // Salva nella lista temporale
        }
    }

    private void saveResults() throws IOException {
        if (!this.cvEvaluationResults.isEmpty()) {
            LOGGER.info("Saving cross-validation evaluation results...");
            // Aggiungo il suffisso "_cv" per distinguere il file
            PrintUtils.printEvaluationResults(projectName, this.cvEvaluationResults, "_cv");
        } else {
            LOGGER.warning("No cross-validation results to save.");
        }

        if (!this.temporalEvaluationResults.isEmpty()) {
            LOGGER.info("Saving temporal validation evaluation results...");
            // Aggiungo il suffisso "_temporal" per distinguere il file
            PrintUtils.printEvaluationResults(projectName, this.temporalEvaluationResults, "_temporal");
        } else {
            LOGGER.warning("No temporal validation results to save.");
        }
    }
}
