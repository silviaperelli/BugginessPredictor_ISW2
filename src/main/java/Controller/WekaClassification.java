package Controller;

import Model.AcumeMethod; // <-- NUOVO IMPORT
import Model.ClassifierEvaluation;
import Model.JavaMethod;
import Model.WekaClassifier;
import utils.PrintUtils;
import utils.WekaUtils;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;

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

    public void execute() {
        // ... questo metodo rimane identico ...
        LOGGER.log(Level.INFO, "--- Starting WEKA analysis for project: {0} ---", projectName);
        try {
            prepareArffFilesForWalkForward();
            runClassificationFromFiles();
            saveResults();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred during WEKA analysis", e);
        }
        LOGGER.log(Level.INFO, "--- WEKA analysis finished for project: {0} ---", projectName);
    }

    private void prepareArffFilesForWalkForward() throws IOException {
        // ... questo metodo rimane identico ...
        int windowSize;
        if (this.projectName.equalsIgnoreCase("BOOKKEEPER")) {
            windowSize = 1;
        } else if (this.projectName.equalsIgnoreCase("SYNCOPE")) {
            windowSize = 5;
        } else {
            windowSize = 3;
            LOGGER.log(Level.WARNING, "Nessuna window size specifica configurata per il progetto {0}. Uso il valore di default: {1}", new Object[]{this.projectName, windowSize});
        }
        LOGGER.log(Level.INFO, "Preparing ARFF files for project {0} using sliding window with windowSize = {1}", new Object[]{this.projectName, windowSize});
        for (int i = 1; i < totalReleases; i++) {
            int lastTrainingReleaseId = i;
            int testingReleaseId = i + 1;
            List<JavaMethod> trainingMethods = allMethods.stream()
                    .filter(m -> {
                        int releaseId = m.getRelease().getId();
                        return releaseId > lastTrainingReleaseId - windowSize && releaseId <= lastTrainingReleaseId;
                    })
                    .collect(Collectors.toList());
            List<JavaMethod> testingMethods = allMethods.stream()
                    .filter(m -> m.getRelease().getId() == testingReleaseId)
                    .collect(Collectors.toList());
            if (trainingMethods.isEmpty() || testingMethods.isEmpty()) continue;
            String iterDir = "arffFiles/" + projectName.toLowerCase() + "/iteration_" + i + "/";
            new File(iterDir).mkdirs();
            Instances trainingSet = WekaUtils.buildInstances(trainingMethods, projectName + "-Training-" + i);
            Instances testingSet = WekaUtils.buildInstances(testingMethods, projectName + "-Testing-" + i);
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


    private void runClassificationFromFiles() throws Exception {
        LOGGER.info("Starting classification experiments from ARFF files...");

        for (int i = 1; i < totalReleases; i++) {
            String trainingPath = "arffFiles/" + projectName.toLowerCase() + "/iteration_" + i + "/training.arff";
            String testingPath = "arffFiles/" + projectName.toLowerCase() + "/iteration_" + i + "/testing.arff";

            File trainingFile = new File(trainingPath);
            if (!trainingFile.exists()) {
                continue;
            }

            Instances trainingSet = new DataSource(trainingPath).getDataSet();
            Instances testingSet = new DataSource(testingPath).getDataSet();
            trainingSet.setClassIndex(trainingSet.numAttributes() - 1);
            testingSet.setClassIndex(testingSet.numAttributes() - 1);
            int positiveClassIndex = trainingSet.classAttribute().indexOfValue("yes");

            if (positiveClassIndex == -1) {
                continue;
            }

            int numBuggyInstances = 0;
            for (int j = 0; j < trainingSet.numInstances(); j++) {
                if (trainingSet.get(j).classValue() == positiveClassIndex) {
                    numBuggyInstances++;
                }
            }
            final int SMOTE_MIN_INSTANCES = 6;

            LOGGER.log(Level.INFO, "--- Iteration {0}: Training on {1} instances ({2} buggy), Testing on {3} instances ---",
                    new Object[]{i, trainingSet.numInstances(), numBuggyInstances, testingSet.numInstances()});

            List<WekaClassifier> classifiersToTest = ClassifierBuilder.buildClassifiers(trainingSet);

            for (WekaClassifier wekaConfig : classifiersToTest) {

                if (wekaConfig.getSampling().equals("SMOTE") && numBuggyInstances < SMOTE_MIN_INSTANCES) {
                    continue;
                }

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

                // --- NUOVA PARTE: CHIAMATA PER CREARE I FILE ACUME ---
                // Generiamo il nome del file e chiamiamo il metodo che crea i dati per ACUME
                String acumeFileName = createAcumeFileName(wekaConfig, i);
                createAcumeFile(acumeFileName, wekaConfig.getClassifier(), testingSet, i);
                // --- FINE NUOVA PARTE ---
            }
        }
    }

    // --- INIZIO NUOVI METODI AGGIUNTI ---

    /**
     * Crea un nome file univoco per i risultati di ACUME basato sulla configurazione del classificatore.
     */
    private String createAcumeFileName(WekaClassifier wekaClassifier, int iteration) {
        String name = wekaClassifier.getName();
        if (!wekaClassifier.getFeatureSelection().equals("none")) {
            name = name + "_" + wekaClassifier.getFeatureSelection();
        }
        if (!wekaClassifier.getSampling().equals("none")) {
            name = name + "_" + wekaClassifier.getSampling();
        }
        if (!wekaClassifier.getCostSensitive().equals("none")) {
            name = name + "_" + wekaClassifier.getCostSensitive();
        }
        name = name + "_iteration_" + iteration;
        return name;
    }

    /**
     * Estrae le probabilità di predizione per ogni istanza del testing set e le salva in un file CSV per ACUME.
     */
    private void createAcumeFile(String fileName, weka.classifiers.Classifier classifier, Instances testingSet, int iteration) throws Exception {
        List<AcumeMethod> acumeMethods = new ArrayList<>();

        // Recupera i JavaMethod corrispondenti a questo specifico testing set
        int testingReleaseId = iteration + 1;
        List<JavaMethod> testingMethods = this.allMethods.stream()
                .filter(m -> m.getRelease().getId() == testingReleaseId)
                .collect(Collectors.toList());

        // Controllo di coerenza: il numero di istanze in Weka deve corrispondere al numero di metodi che abbiamo
        if (testingSet.numInstances() != testingMethods.size()) {
            LOGGER.log(Level.SEVERE, "Mismatch in ACUME data creation for iteration {0}. Weka instances: {1}, Java methods: {2}",
                    new Object[]{iteration, testingSet.numInstances(), testingMethods.size()});
            return; // Interrompe la creazione del file per questa iterazione se i dati non corrispondono
        }

        int buggyClassIndex = testingSet.classAttribute().indexOfValue("yes");

        for (int i = 0; i < testingSet.numInstances(); i++) {
            JavaMethod currentMethod = testingMethods.get(i);
            String trueClassLabel = testingSet.instance(i).toString(testingSet.classIndex());

            // Estrae la distribuzione di probabilità
            double[] predictionDistribution = classifier.distributionForInstance(testingSet.instance(i));

            // Estrae la probabilità della classe "buggy" ('yes')
            double predictionProbability = predictionDistribution[buggyClassIndex];

            // Crea l'oggetto con i dati per ACUME
            AcumeMethod acumeMethod = new AcumeMethod(i, currentMethod.getLoc(), predictionProbability, trueClassLabel);
            acumeMethods.add(acumeMethod);
        }

        // Chiama il metodo in PrintUtils per scrivere fisicamente il file CSV
        PrintUtils.createAcumeFile(this.projectName, acumeMethods, fileName);
    }

    // --- FINE NUOVI METODI AGGIUNTI ---


    private void saveResults() throws IOException {
        // ... questo metodo rimane identico ...
        if (this.evaluationResults.isEmpty()) {
            LOGGER.warning("No evaluation results to save.");
            return;
        }
        LOGGER.info("Saving evaluation results...");
        PrintUtils.printEvaluationResults(projectName, this.evaluationResults);
    }
}