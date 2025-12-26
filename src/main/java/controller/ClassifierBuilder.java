package controller;

import model.WekaClassifier;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.AttributeStats;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe di utilità per costruire e configurare i diversi classificatori Weka utilizzati nell'analisi
 */
public class ClassifierBuilder {

    private static final String FEATURE_SELECTION_NAME = "BestFirst (backward)";

    private ClassifierBuilder() {}

    /**
     * Costruisce e restituisce una lista di tutti i classificatori Weka da testare.
     * Ogni classificatore di base viene combinato con diverse tecniche di pre-processing
     */
    public static List<WekaClassifier> buildClassifiers(Instances trainingSet) {
        List<WekaClassifier> classifiers = new ArrayList<>();

        // Aggiunge i classificatori di base
        addBaseClassifiers(classifiers);

        // Aggiunge i classificatori con Feature Selection
        addFeatureSelectionClassifiers(classifiers);

        // Aggiunge i classificatori con bilanciamento del dataset tramite SMOTE
        addSmoteClassifiers(classifiers, trainingSet);

        // Aggiunge i classificatori con Cost-Sensitive Learning
        addCostSensitiveClassifiers(classifiers);

        return classifiers;
    }

    // Aggiunge i tre classificatori di base (RandomForest, NaiveBayes, IBk)
    private static void addBaseClassifiers(List<WekaClassifier> classifiers) {
        classifiers.add(new WekaClassifier(new RandomForest(), "RandomForest", "none", "none", "none"));
        classifiers.add(new WekaClassifier(new NaiveBayes(), "NaiveBayes", "none", "none", "none"));
        classifiers.add(new WekaClassifier(new IBk(), "IBk", "none", "none", "none"));
    }

    // Combina ogni classificatore di base con un filtro di Feature Selection
    private static void addFeatureSelectionClassifiers(List<WekaClassifier> classifiers) {
        for (Classifier base : getBaseClassifiers()) {
            FilteredClassifier fc = new FilteredClassifier();
            fc.setClassifier(base);
            fc.setFilter(createFeatureSelectionFilter());
            classifiers.add(new WekaClassifier(fc, getClassifierName(base), FEATURE_SELECTION_NAME, "none", "none"));
        }
    }

    // Combina ogni classificatore di base con un filtro SMOTE
    private static void addSmoteClassifiers(List<WekaClassifier> classifiers, Instances trainingSet) {
        Filter smote = createSmoteFilter(trainingSet);
        for (Classifier base : getBaseClassifiers()) {
            FilteredClassifier fc = new FilteredClassifier();
            fc.setClassifier(base);
            fc.setFilter(smote);
            classifiers.add(new WekaClassifier(fc, getClassifierName(base), "none", "SMOTE", "none"));
        }
    }

    // Aggiunge i classificatori con Cost-Sensitive Learning
    private static void addCostSensitiveClassifiers(List<WekaClassifier> classifiers) {
        for (Classifier base : getBaseClassifiers()) {
            CostSensitiveClassifier csc = new CostSensitiveClassifier();
            csc.setClassifier(base);
            csc.setCostMatrix(createCostMatrix());
            csc.setMinimizeExpectedCost(false);
            classifiers.add(new WekaClassifier(csc, getClassifierName(base), "none", "none", "SensitiveThreshold"));
        }
    }

    // Metodo helper per ottenere una lista dei classificatori di base
    private static List<Classifier> getBaseClassifiers() {
        List<Classifier> baseClassifiers = new ArrayList<>();
        baseClassifiers.add(new RandomForest());
        baseClassifiers.add(new NaiveBayes());
        baseClassifiers.add(new IBk());
        return baseClassifiers;
    }

    // Metodo helper per ottenere un nome leggibile dal tipo di classificatore
    private static String getClassifierName(Classifier classifier) {
        if (classifier instanceof RandomForest) return "RandomForest";
        if (classifier instanceof NaiveBayes) return "NaiveBayes";
        if (classifier instanceof IBk) return "IBk";
        return classifier.getClass().getSimpleName();
    }

    // Configura il filtro per la selezione delle feature usando BestFirst in modalità backward
    private static Filter createFeatureSelectionFilter() {
        AttributeSelection filter = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        BestFirst search = new BestFirst();

        String[] options = {"-D", "0"}; // Opzione per la ricerca all'indietro
        try {
            search.setOptions(options);
        } catch (Exception e) {
            Logger.getLogger(ClassifierBuilder.class.getName()).log(Level.SEVERE, "Failed to set BestFirst options", e);
        }

        filter.setEvaluator(eval);
        filter.setSearch(search);

        return filter;
    }

    // Configura il filtro SMOTE per bilanciare il dataset, calcolando dinamicamente la percentuale necessaria
    private static Filter createSmoteFilter(Instances data) {
        SMOTE smote = new SMOTE();
        AttributeStats stats = data.attributeStats(data.classIndex());
        int[] nominalCounts = stats.nominalCounts;

        if (nominalCounts.length < 2) return new Resample(); // Fallback se non ci sono due classi
        double majoritySize = Math.max(nominalCounts[0], nominalCounts[1]);
        double minoritySize = Math.min(nominalCounts[0], nominalCounts[1]);
        if (minoritySize == 0) return new Resample(); // Fallback se la classe minoritaria è vuota

        // Calcola la percentuale per portare la classe minoritaria alla stessa dimensione della maggioritaria
        double percentage = (majoritySize - minoritySize) / minoritySize * 100.0;
        smote.setPercentage(percentage);
        return smote;
    }

    // Crea una matrice di costo che penalizza maggiormente i Falsi Negativi (FN) rispetto ai Falsi Positivi (FP)
    public static CostMatrix createCostMatrix() {
        CostMatrix matrix = new CostMatrix(2);
        matrix.setCell(0, 0, 0.0);  // TN
        matrix.setCell(1, 1, 0.0);  // TP
        matrix.setCell(0, 1, 1.0);  // FP
        matrix.setCell(1, 0, 10.0); // FN
        return matrix;
    }
}