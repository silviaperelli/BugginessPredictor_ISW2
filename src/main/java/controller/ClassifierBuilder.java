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

public class ClassifierBuilder {

    private static final String FEATURE_SELECTION_NAME = "BestFirst (backward)";

    private ClassifierBuilder() {}

    /**
     * Costruisce una lista semplificata di classificatori per l'analisi.
     * Include: Baseline, Feature Selection, SMOTE, e Cost-Sensitive.
     * Le combinazioni complesse sono state rimosse per ridurre il tempo di esecuzione.
     */
    public static List<WekaClassifier> buildClassifiers(Instances trainingSet) {
        List<WekaClassifier> classifiers = new ArrayList<>();

        // 1. Classificatori Base (Baseline)
        addBaseClassifiers(classifiers);

        // 2. Classificatori con Feature Selection
        addFeatureSelectionClassifiers(classifiers);

        // 3. Classificatori con SMOTE
        addSmoteClassifiers(classifiers, trainingSet);

        // 4. Classificatori con Cost-Sensitive Learning
        addCostSensitiveClassifiers(classifiers);


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
            classifiers.add(new WekaClassifier(fc, getClassifierName(base), FEATURE_SELECTION_NAME, "none", "none"));
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
            csc.setMinimizeExpectedCost(false);
            classifiers.add(new WekaClassifier(csc, getClassifierName(base), "none", "none", "SensitiveThreshold"));
        }
    }

    // ... IL RESTO DELLA CLASSE RIMANE IDENTICO ...

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
        CfsSubsetEval eval = new CfsSubsetEval();
        BestFirst search = new BestFirst();

        String[] options = {"-D", "0"}; // BACKWARD
        try {
            search.setOptions(options);
        } catch (Exception e) {
            Logger.getLogger(ClassifierBuilder.class.getName()).log(Level.SEVERE, "Failed to set BestFirst options", e);
        }

        filter.setEvaluator(eval);
        filter.setSearch(search);

        return filter;
    }

    private static Filter createSmoteFilter(Instances data) {
        SMOTE smote = new SMOTE();
        AttributeStats stats = data.attributeStats(data.classIndex());
        int[] nominalCounts = stats.nominalCounts;

        if (nominalCounts.length < 2) return new Resample();
        double majoritySize = Math.max(nominalCounts[0], nominalCounts[1]);
        double minoritySize = Math.min(nominalCounts[0], nominalCounts[1]);
        if (minoritySize == 0) return new Resample();

        double percentage = (majoritySize - minoritySize) / minoritySize * 100.0;
        smote.setPercentage(percentage);
        return smote;
    }

    public static CostMatrix createCostMatrix() {
        CostMatrix matrix = new CostMatrix(2);
        matrix.setCell(0, 0, 0.0);  // TN
        matrix.setCell(1, 1, 0.0);  // TP
        matrix.setCell(0, 1, 1.0);  // FP
        matrix.setCell(1, 0, 10.0); // FN
        return matrix;
    }


}