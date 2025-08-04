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
            int windowSize = 3; // Prova con 3, puoi aumentarla se i risultati sono ancora instabili

            List<JavaMethod> trainingMethods = allMethods.stream()
                    .filter(m -> {
                        int releaseId = m.getRelease().getId();
                        // Prendi i metodi delle ultime 'windowSize' release
                        return releaseId > lastTrainingReleaseId - windowSize && releaseId <= lastTrainingReleaseId;
                    })
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
// In Controller/WekaClassification.java

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

            Instances trainingSet = new DataSource(trainingPath).getDataSet();
            Instances testingSet = new DataSource(testingPath).getDataSet();

            trainingSet.setClassIndex(trainingSet.numAttributes() - 1);
            testingSet.setClassIndex(testingSet.numAttributes() - 1);

            // --- NUOVA PARTE: CONTROLLO DINAMICO ---
            int positiveClassIndex = trainingSet.classAttribute().indexOfValue("yes");
            if (positiveClassIndex == -1) {
                LOGGER.log(Level.WARNING, "Skipping iteration {0}: class 'yes' not found in training data.", i);
                continue;
            }

            // Calcola quante istanze "buggy" ci sono nel training set
            int numBuggyInstances = 0;
            for (int j = 0; j < trainingSet.numInstances(); j++) {
                if (trainingSet.get(j).classValue() == positiveClassIndex) {
                    numBuggyInstances++;
                }
            }

            // La soglia di default per SMOTE è k=5, quindi servono almeno k+1=6 istanze per essere sicuri.
            // Possiamo essere più conservativi e usare una soglia più bassa, es. 2.
            final int SMOTE_MIN_INSTANCES = 6;

            LOGGER.log(Level.INFO, "--- Iteration {0}: Training on {1} instances ({2} buggy), Testing on {3} instances ---",
                    new Object[]{i, trainingSet.numInstances(), numBuggyInstances, testingSet.numInstances()});

            // --- FINE NUOVA PARTE ---

            List<WekaClassifier> classifiersToTest = ClassifierBuilder.buildClassifiers(trainingSet);

            for (WekaClassifier wekaConfig : classifiersToTest) {

                // --- AGGIUNTA CONDIZIONE PER SALTARE SMOTE ---
                if (wekaConfig.getSampling().equals("SMOTE") && numBuggyInstances < SMOTE_MIN_INSTANCES) {
                    LOGGER.log(Level.WARNING, "Skipping SMOTE for classifier {0} in iteration {1}: not enough minority instances ({2} < {3}).",
                            new Object[]{wekaConfig.getName(), i, numBuggyInstances, SMOTE_MIN_INSTANCES});
                    continue; // Salta questa configurazione e passa alla successiva
                }
                // --- FINE AGGIUNTA ---

                // Il resto del codice rimane identico
                wekaConfig.getClassifier().buildClassifier(trainingSet);

                Evaluation eval = new Evaluation(testingSet);
                eval.evaluateModel(wekaConfig.getClassifier(), testingSet);

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