package utils;

import model.JavaMethod;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Classe di utilità per gestire le operazioni specifiche di Weka,
 * in particolare la conversione delle strutture dati del progetto nel formato `Instances` di Weka
 */
public final class WekaUtils {

    private WekaUtils() {}

    /**
     * Costruisce un oggetto `Instances` di Weka a partire da una lista di oggetti `JavaMethod`
     */
    public static Instances buildInstances(List<JavaMethod> methods, String relationName) {
        // Definisce gli attributi
        ArrayList<Attribute> attributes = new ArrayList<>();

        // Aggiunge tutti gli attributi numerici che corrispondono alle metriche calcolate
        attributes.add(new Attribute("LOC"));
        attributes.add(new Attribute("NumParameters"));
        attributes.add(new Attribute("NumBranches"));
        attributes.add(new Attribute("NestingDepth"));
        attributes.add(new Attribute("NumCodeSmells"));
        attributes.add(new Attribute("NumLocalVariables"));
        attributes.add(new Attribute("NumRevisions"));
        attributes.add(new Attribute("NumAuthors"));
        attributes.add(new Attribute("TotalStmtAdded"));
        attributes.add(new Attribute("TotalStmtDeleted"));
        attributes.add(new Attribute("MaxChurn"));
        attributes.add(new Attribute("AvgChurn"));
        attributes.add(new Attribute("HasFixHistory"));

        // Aggiunge l'attributo nominale ovvero la variabile che vogliamo predire (il target)
        List<String> classValues = Arrays.asList("no", "yes");
        attributes.add(new Attribute("IsBuggy", classValues));

        // Crea l'oggetto Instances vuoto con la struttura definita e la capacità iniziale
        Instances data = new Instances(relationName, attributes, methods.size());

        // Imposta l'ultimo attributo come quello da predire
        data.setClassIndex(data.numAttributes() - 1);

        // Popola l'oggetto Instances con i dati, iterando su ogni `JavaMethod`
        for (JavaMethod method : methods) {
            // Crea un array di double per contenere i valori di una riga
            double[] values = new double[data.numAttributes()];

            // Popola l'array
            values[0] = method.getLoc();
            values[1] = method.getNumParameters();
            values[2] = method.getNumBranches();
            values[3] = method.getNestingDepth();
            values[4] = method.getNumCodeSmells();
            values[5] = method.getNumLocalVariables();
            values[6] = method.getNumRevisions();
            values[7] = method.getNumAuthors();
            values[8] = method.getTotalStmtAdded();
            values[9] = method.getTotalStmtDeleted();
            values[10] = method.getMaxChurn();
            values[11] = method.getAvgChurn();
            values[12] = method.getHasFixHistory();

            // Per l'attributo nominale, usiamo l'indice del valore ("no" = 0, "yes" = 1)
            values[data.classIndex()] = method.isBuggy() ? 1.0 : 0.0;

            // Aggiunge la riga (istanza) al dataset
            data.add(new DenseInstance(1.0, values));
        }

        return data;
    }

    /**
     * Carica un dataset Weka direttamente da un file CSV
     */
    public static Instances loadInstancesFromCsv(String csvPath) throws IOException {
        // Usa il CSVLoader di Weka, che gestisce automaticamente il parsing
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();

        // Controllo di sicurezza: se Weka non ha identificato automaticamente la colonna della classe,
        // impostiamo l'ultima colonna come default
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }
        return data;
    }
}