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
 * Utility class to convert custom data structures into Weka's Instances format.
 */
public final class WekaUtils {

    private WekaUtils() {}

    /**
     * Builds a Weka Instances object from a list of JavaMethod objects.
     * This method defines the structure of the dataset (attributes) and populates it.
     *
     * @param methods The list of JavaMethod instances to convert.
     * @param relationName A name for the dataset (e.g., "BOOKKEEPER-Training").
     * @return An Instances object ready for use with Weka classifiers.
     */

    public static Instances buildInstances(List<JavaMethod> methods, String relationName) {
        // 1. Definisci gli attributi (le colonne)
        ArrayList<Attribute> attributes = new ArrayList<>();

        // Aggiungi tutti gli attributi numerici nell'ordine desiderato
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

        // Aggiungi l'attributo nominale della classe (il target da predire)
        List<String> classValues = Arrays.asList("no", "yes");
        attributes.add(new Attribute("IsBuggy", classValues));

        // 2. Crea l'oggetto Instances vuoto con la struttura definita
        Instances data = new Instances(relationName, attributes, methods.size());

        // Imposta l'ultimo attributo come quello da predire
        data.setClassIndex(data.numAttributes() - 1);

        // 3. Popola l'oggetto Instances con i dati
        for (JavaMethod method : methods) {
            // Crea un array di double per contenere i valori di una riga
            double[] values = new double[data.numAttributes()];

            // Popola l'array. L'ordine DEVE corrispondere a quello degli attributi sopra.
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

            // Aggiungi la riga (istanza) al dataset
            data.add(new DenseInstance(1.0, values));
        }

        return data;
    }

    public static Instances loadInstancesFromCsv(String csvPath) throws IOException {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();

        // È fondamentale impostare l'attributo della classe (l'ultimo)
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }
        return data;
    }
}