package Controller;

import Model.WekaClassifier; // Assicurati che l'import sia corretto
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
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClassifierBuilder {

    private ClassifierBuilder() {}

    public static List<WekaClassifier> buildClassifiers(Instances trainingSet) {
        List<WekaClassifier> classifiers = new ArrayList<>();

        addBaseClassifiers(classifiers);
        addFeatureSelectionClassifiers(classifiers);
        addSmoteClassifiers(classifiers, trainingSet);
        addCostSensitiveClassifiers(classifiers);

        addFeatureSelectionAndSmoteClassifiers(classifiers, trainingSet);
        addFeatureSelectionAndCostSensitiveClassifiers(classifiers);

        return classifiers;
    }

    private static void addBaseClassifiers(List<WekaClassifier> classifiers) {
        classifiers.add(new WekaClassifier(new RandomForest(), "RandomForest", "none", "none", "none"));
        classifiers.add(new WekaClassifier(new NaiveBayes(), "NaiveBayes", "none", "none", "none"));
        classifiers.add(new WekaClassifier(new IBk(), "IBk", "none", "none", "none"));
    }

    private static void addFeatureSelectionClassifiers(List<WekaClassifier> classifiers) {
        for (Classifier base : getBaseClassifiers()) {
            FilteredClassifier fc = new FilteredClassifier();
            fc.setClassifier(base);
            fc.setFilter(createFeatureSelectionFilter());
            classifiers.add(new WekaClassifier(fc, getClassifierName(base), "BestFirst", "none", "none"));
        }
    }

    private static void addSmoteClassifiers(List<WekaClassifier> classifiers, Instances trainingSet) {
        Filter smote = createSmoteFilter(trainingSet);
        for (Classifier base : getBaseClassifiers()) {
            FilteredClassifier fc = new FilteredClassifier();
            fc.setClassifier(base);
            fc.setFilter(smote);
            classifiers.add(new WekaClassifier(fc, getClassifierName(base), "none", "SMOTE", "none"));
        }
    }

    private static void addCostSensitiveClassifiers(List<WekaClassifier> classifiers) {
        for (Classifier base : getBaseClassifiers()) {
            CostSensitiveClassifier csc = new CostSensitiveClassifier();
            csc.setClassifier(base);
            csc.setCostMatrix(createCostMatrix());
            csc.setMinimizeExpectedCost(false); // Usa re-weighting
            classifiers.add(new WekaClassifier(csc, getClassifierName(base), "none", "none", "SensitiveThreshold"));
        }
    }

    private static List<Classifier> getBaseClassifiers() {
        List<Classifier> baseClassifiers = new ArrayList<>();
        baseClassifiers.add(new RandomForest());
        baseClassifiers.add(new NaiveBayes());
        baseClassifiers.add(new IBk());
        return baseClassifiers;
    }

    private static String getClassifierName(Classifier classifier) {
        if (classifier instanceof RandomForest) return "RandomForest";
        if (classifier instanceof NaiveBayes) return "NaiveBayes";
        if (classifier instanceof IBk) return "IBk";
        return classifier.getClass().getSimpleName();
    }

    private static Filter createFeatureSelectionFilter() {
        AttributeSelection filter = new AttributeSelection();

        // Create the evaluator
        CfsSubsetEval eval = new CfsSubsetEval();

        // Create the search method
        BestFirst search = new BestFirst();

        // Configure the search method using command-line options
        // -D 1 specifies a forward search.
        try {
            search.setOptions(Utils.splitOptions("-D 1"));
        } catch (Exception e) {
            Logger.getLogger(ClassifierBuilder.class.getName()).log(Level.SEVERE, "Failed to set BestFirst options", e);
        }

        // Set the evaluator and search method on the filter
        filter.setEvaluator(eval);
        filter.setSearch(search);

        return filter;
    }

    private static Filter createSmoteFilter(Instances data) {
        SMOTE smote = new SMOTE();

        // Get class distribution
        AttributeStats stats = data.attributeStats(data.classIndex());
        int[] nominalCounts = stats.nominalCounts;

        if (nominalCounts.length < 2) return new Resample(); // Failsafe if data is not binary

        double majoritySize = Math.max(nominalCounts[0], nominalCounts[1]);
        double minoritySize = Math.min(nominalCounts[0], nominalCounts[1]);

        if (minoritySize == 0) return new Resample(); // Avoid division by zero

        // Calculate the percentage of new instances to create
        double percentage = (majoritySize - minoritySize) / minoritySize * 100.0;
        smote.setPercentage(percentage);

        return smote;
    }

    private static CostMatrix createCostMatrix() {
        CostMatrix matrix = new CostMatrix(2);
        // I tuoi dati sono {no, yes}, Weka li ordina alfabeticamente: 0=no, 1=yes.
        // La classe "positiva" (buggy) è 'yes', quindi l'indice è 1.
        matrix.setCell(0, 0, 0.0);   // TN: Reale 'no', Predetto 'no' -> Costo 0
        matrix.setCell(1, 1, 0.0);   // TP: Reale 'yes', Predetto 'yes' -> Costo 0
        matrix.setCell(0, 1, 1.0);   // FP: Reale 'no', Predetto 'yes' -> Costo 1 (falso allarme)
        matrix.setCell(1, 0, 10.0);  // FN: Reale 'yes', Predetto 'no' -> Costo 10 (bug non trovato!)
        return matrix;
    }

    // AGGIUNGI QUESTO NUOVO METODO
    private static void addFeatureSelectionAndSmoteClassifiers(List<WekaClassifier> classifiers, Instances trainingSet) {
        Filter smote = createSmoteFilter(trainingSet);
        for (Classifier base : getBaseClassifiers()) {
            // Step 1: Crea il classificatore con Feature Selection
            FilteredClassifier fcWithFeatureSelection = new FilteredClassifier();
            fcWithFeatureSelection.setClassifier(base);
            fcWithFeatureSelection.setFilter(createFeatureSelectionFilter());

            // Step 2: Avvolgi il classificatore filtrato con SMOTE
            FilteredClassifier fcWithBoth = new FilteredClassifier();
            fcWithBoth.setClassifier(fcWithFeatureSelection);
            fcWithBoth.setFilter(smote);

            classifiers.add(new WekaClassifier(fcWithBoth, getClassifierName(base), "BestFirst", "SMOTE", "none"));
        }
    }

    // AGGIUNGI ANCHE QUESTO NUOVO METODO
    private static void addFeatureSelectionAndCostSensitiveClassifiers(List<WekaClassifier> classifiers) {
        for (Classifier base : getBaseClassifiers()) {
            // Step 1: Crea il classificatore con Cost Sensitive
            CostSensitiveClassifier csc = new CostSensitiveClassifier();
            csc.setClassifier(base);
            csc.setCostMatrix(createCostMatrix());
            csc.setMinimizeExpectedCost(false);

            // Step 2: Avvolgi il classificatore Cost-Sensitive con la Feature Selection
            FilteredClassifier fcWithBoth = new FilteredClassifier();
            fcWithBoth.setClassifier(csc);
            fcWithBoth.setFilter(createFeatureSelectionFilter());

            classifiers.add(new WekaClassifier(fcWithBoth, getClassifierName(base), "BestFirst", "none", "SensitiveThreshold"));
        }
    }
}