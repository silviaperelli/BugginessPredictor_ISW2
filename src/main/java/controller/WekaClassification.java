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

public class WekaClassification {

    private static final Logger LOGGER = Logger.getLogger(WekaClassification.class.getName());
    private final String projectName;
    private final List<JavaMethod> allMethods;
    private final List<ClassifierEvaluation> evaluationResults;

    public WekaClassification(String projectName, List<JavaMethod> allMethods) {
        this.projectName = projectName;
        this.allMethods = allMethods;
        this.evaluationResults = new ArrayList<>();
    }

    public void execute() {
        LOGGER.log(Level.INFO, "--- Starting WEKA analysis for project: {0} ---", projectName);
        try {
            runCrossValidationAnalysis();
            saveResults();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred during WEKA analysis", e);
        }
        LOGGER.log(Level.INFO, "--- WEKA analysis finished for project: {0} ---", projectName);
    }

    private void runCrossValidationAnalysis() throws Exception {
        LOGGER.info("Building the complete dataset for cross-validation...");
        Instances fullDataset = WekaUtils.buildInstances(this.allMethods, projectName + "-full-dataset");
        fullDataset.setClassIndex(fullDataset.numAttributes() - 1);
        LOGGER.log(Level.INFO, "Dataset built with {0} instances.", fullDataset.numInstances());

        // --- NUOVA LOGICA: Imposta il numero di ripetizioni dinamicamente ---
        final int numRepetitions; // Dichiara la variabile come 'final' perché non cambierà più dopo essere stata impostata
        if ("BOOKKEEPER".equalsIgnoreCase(this.projectName)) {
            numRepetitions = 10;
        } else if ("SYNCOPE".equalsIgnoreCase(this.projectName)) {
            numRepetitions = 1;
        } else {
            // Fallback: se il progetto non è uno di quelli specificati, usa un valore di default (es. 10)
            numRepetitions = 10;
        }

        // Logga la scelta fatta, così è chiaro nell'output
        LOGGER.log(Level.INFO, "Project is ''{0}''. Setting repetitions to {1}.", new Object[]{this.projectName, numRepetitions});
        // --- FINE NUOVA LOGICA ---

        int numFolds = 10;

        int positiveClassIndex = fullDataset.classAttribute().indexOfValue("yes");
        int numBuggyInstances = 0;
        for (int j = 0; j < fullDataset.numInstances(); j++) {
            if (fullDataset.get(j).classValue() == positiveClassIndex) {
                numBuggyInstances++;
            }
        }
        final int SMOTE_MIN_INSTANCES = 6;

        List<WekaClassifier> classifiersToTest = ClassifierBuilder.buildClassifiers(fullDataset);

        // --- NUOVA STAMPA: Contatore per tenere traccia del progresso totale ---
        int totalExperiments = classifiersToTest.size();
        int currentExperiment = 0;

        for (WekaClassifier wekaConfig : classifiersToTest) {

            currentExperiment++; // Incrementa il contatore dell'esperimento

            if (wekaConfig.getSampling().equals("SMOTE") && numBuggyInstances < SMOTE_MIN_INSTANCES) {
                LOGGER.log(Level.WARNING, "[{1}/{2}] Skipping SMOTE for {0}: not enough minority instances.",
                        new Object[]{wekaConfig.getName(), currentExperiment, totalExperiments});
                continue;
            }

            // --- NUOVA STAMPA: Indica quale esperimento sta per iniziare ---
            LOGGER.log(Level.INFO, "\n--- [STARTING EXPERIMENT {0}/{1}] ---", new Object[]{currentExperiment, totalExperiments});
            LOGGER.log(Level.INFO, "Classifier: {0}, FS: {1}, Samp: {2}, CS: {3}",
                    new Object[]{wekaConfig.getName(), wekaConfig.getFeatureSelection(), wekaConfig.getSampling(), wekaConfig.getCostSensitive()});


            for (int i = 1; i <= numRepetitions; i++) {

                // --- NUOVA STAMPA: Indica a che punto della ripetizione siamo ---
                // Usiamo System.out.print con \r per scrivere sulla stessa riga, creando una barra di progresso.
                System.out.print(String.format("    -> Repetition %d/%d... ", i, numRepetitions));

                Evaluation eval = new Evaluation(fullDataset);
                eval.crossValidateModel(wekaConfig.getClassifier(), fullDataset, numFolds, new Random(i));

                // Sovrascrive la riga precedente con "DONE."
                System.out.println("DONE.");

                ClassifierEvaluation result = new ClassifierEvaluation(
                        projectName, i, wekaConfig.getName(),
                        wekaConfig.getFeatureSelection(), wekaConfig.getSampling(), wekaConfig.getCostSensitive(),
                        eval.precision(positiveClassIndex), eval.recall(positiveClassIndex),
                        eval.areaUnderROC(positiveClassIndex), eval.kappa(), eval.fMeasure(positiveClassIndex)
                );
                this.evaluationResults.add(result);
            }
            LOGGER.log(Level.INFO, "--- [FINISHED EXPERIMENT {0}/{1}] ---", new Object[]{currentExperiment, totalExperiments});
        }
    }

    private void saveResults() throws IOException {
        if (this.evaluationResults.isEmpty()) {
            LOGGER.warning("No evaluation results to save.");
            return;
        }
        LOGGER.info("Saving evaluation results...");
        PrintUtils.printEvaluationResults(projectName, this.evaluationResults);
    }
}