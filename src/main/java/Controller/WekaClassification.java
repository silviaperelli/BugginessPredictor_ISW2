package Controller;

import Model.ClassifierEvaluation;
import Model.JavaMethod;
import Model.WekaClassifier;
import utils.PrintUtils;
import utils.WekaUtils; // Usa WekaUtils per creare gli Instances in memoria
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.core.converters.ArffSaver; // Import per salvare i file ARFF
import weka.core.converters.ConverterUtils.DataSource; // Import per caricare i file ARFF

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WekaClassification {

    private static final Logger LOGGER = Logger.getLogger(WekaClassification.class.getName());
    private final String projectName;
    private final List<JavaMethod> allMethods;
    private final int totalReleases;
    private final List<ClassifierEvaluation> evaluationResults;

    public WekaClassification(String projectName, List<JavaMethod> allMethods) {
        this.projectName = projectName;
        this.allMethods = allMethods;
        this.totalReleases = allMethods.stream().mapToInt(m -> m.getRelease().getId()).max().orElse(0);
        this.evaluationResults = new ArrayList<>();
    }

    /**
     * Metodo principale che orchestra l'intera pipeline.
     */
    public void execute() {
        LOGGER.log(Level.INFO, "--- Starting WEKA analysis for project: {0} ---", projectName);
        try {
            // Prepara i file ARFF per tutte le iterazioni
            prepareArffFilesForWalkForward();

            // Esegue la classificazione leggendo i file creati
            runClassificationFromFiles();

            // Salva i risultati finali
            saveResults();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred during WEKA analysis", e);
        }
        LOGGER.log(Level.INFO, "--- WEKA analysis finished for project: {0} ---", projectName);
    }

    /**
     * Prepara e scrive su disco i file training.arff e testing.arff per ogni iterazione di Walk-Forward.
     */
    private void prepareArffFilesForWalkForward() throws IOException {
        LOGGER.info("Preparing ARFF files for walk-forward validation...");
        for (int i = 1; i < totalReleases; i++) {
            int lastTrainingReleaseId = i;
            int testingReleaseId = i + 1;

            // --- LOGICA WALK-FORWARD CORRETTA ---
            List<JavaMethod> trainingMethods = allMethods.stream()
                    .filter(m -> m.getRelease().getId() <= lastTrainingReleaseId)
                    .collect(Collectors.toList());

            List<JavaMethod> testingMethods = allMethods.stream()
                    .filter(m -> m.getRelease().getId() == testingReleaseId)
                    .collect(Collectors.toList());

            if (trainingMethods.isEmpty() || testingMethods.isEmpty()) continue;

            String iterDir = "arffFiles/" + projectName.toLowerCase() + "/iteration_" + i + "/";
            new File(iterDir).mkdirs();

            // Crea gli Instances in memoria usando il tuo WekaUtils
            Instances trainingSet = WekaUtils.buildInstances(trainingMethods, projectName + "-Training-" + i);
            Instances testingSet = WekaUtils.buildInstances(testingMethods, projectName + "-Testing-" + i);

            // Salva gli Instances su file .arff
            ArffSaver saver = new ArffSaver();
            saver.setInstances(trainingSet);
            saver.setFile(new File(iterDir + "training.arff"));
            saver.writeBatch();

            saver.setInstances(testingSet);
            saver.setFile(new File(iterDir + "testing.arff"));
            saver.writeBatch();
        }
        LOGGER.info("ARFF file preparation complete.");
    }

    /**
     * Esegue il ciclo di classificazione leggendo i file ARFF precedentemente creati.
     */
    private void runClassificationFromFiles() throws Exception {
        LOGGER.info("Starting classification experiments from ARFF files...");

        for (int i = 1; i < totalReleases; i++) {
            String trainingPath = "arffFiles/" + projectName.toLowerCase() + "/iteration_" + i + "/training.arff";
            String testingPath = "arffFiles/" + projectName.toLowerCase() + "/iteration_" + i + "/testing.arff";

            File trainingFile = new File(trainingPath);
            if (!trainingFile.exists()) {
                LOGGER.log(Level.WARNING, "Skipping iteration {0}: training.arff not found.", i);
                continue;
            }

            // Carica i dati
            Instances trainingSet = new DataSource(trainingPath).getDataSet();
            Instances testingSet = new DataSource(testingPath).getDataSet();

            LOGGER.log(Level.INFO, "--- Iteration {0}: Training on {1} instances, Testing on {2} instances ---",
                    new Object[]{i, trainingSet.numInstances(), testingSet.numInstances()});

            trainingSet.setClassIndex(trainingSet.numAttributes() - 1);
            testingSet.setClassIndex(testingSet.numAttributes() - 1);
            int positiveClassIndex = trainingSet.classAttribute().indexOfValue("yes");

            List<WekaClassifier> classifiersToTest = ClassifierBuilder.buildClassifiers(trainingSet);

            for (WekaClassifier wekaConfig : classifiersToTest) {
                // Addestra il classificatore
                wekaConfig.getClassifier().buildClassifier(trainingSet);

                // Valuta il modello
                Evaluation eval = new Evaluation(testingSet);
                eval.evaluateModel(wekaConfig.getClassifier(), testingSet);

                // Salva i risultati
                ClassifierEvaluation result = new ClassifierEvaluation(
                        projectName, i, wekaConfig.getName(),
                        wekaConfig.getFeatureSelection(), wekaConfig.getSampling(), wekaConfig.getCostSensitive(),
                        eval.precision(positiveClassIndex), eval.recall(positiveClassIndex),
                        eval.areaUnderROC(positiveClassIndex), eval.kappa(), eval.fMeasure(positiveClassIndex)
                );
                this.evaluationResults.add(result);
            }
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