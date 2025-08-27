package utils;

import model.*;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PrintUtils {

    private PrintUtils(){}

    public static final String DELIMITER = "\n";
    public static final String SEPARATOR = ",";
    public static final String CLASS = PrintUtils.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS);
    private static final String MAINDIR = "reportFiles/";
    public static final String SLASH = "/";
    public static final String ERROR = "Error in writeOnReportFiles when trying to create directory";
    private static final String CSV_FILES_DIR = "csvFiles/"; // Nuova cartella base per i CSV

    public static class Console {
        private Console(){}

        @SuppressWarnings("java:S106")
        public static void info(String msg) {
            System.out.println(msg);
        }
    }

    // ... [I metodi printCommits, printTickets, printReleases, printMethods rimangono invariati] ...
    public static void printCommits(String project, List<RevCommit> commitList, String name) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists() && !file.mkdirs()) {
            throw new IOException("Could not create directory: " + file.getAbsolutePath());
        }

        file = new File(MAINDIR + project + SLASH + name);
        try(FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.append("id,committer,creationDate\n");
            for (RevCommit commit: commitList){
                fileWriter.append(commit.getName()).append(",")
                        .append(commit.getCommitterIdent().getName()).append(",")
                        .append(String.valueOf(LocalDate.parse((new SimpleDateFormat("yyyy-MM-dd").format(commit.getCommitterIdent().getWhen()))))).append(DELIMITER);
            }
        } catch (IOException e) {
            LOGGER.info(ERROR);
        }
    }

    // In PrintUtils.java

    public static void printTickets(String project, List<Ticket> ticketList) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists() && !file.mkdirs()) {
            throw new IOException("Could not create directory: " + file.getAbsolutePath());
        }

        file = new File(MAINDIR + project + SLASH + "AllTickets.csv");
        try(FileWriter fileWriter = new FileWriter(file)) {
            // Ho aggiunto numOfCommits all'header per coerenza
            fileWriter.append("key,creationDate,resolutionDate,injectedVersion,openingVersion,fixedVersion,affectedVersion,numOfCommits\n");
            List<Ticket> ticketOrderedByCreation = new ArrayList<>(ticketList);
            ticketOrderedByCreation.sort(Comparator.comparing(Ticket::getCreationDate));

            for (Ticket ticket : ticketOrderedByCreation) {

                // --- INIZIO MODIFICA ---

                // 1. Raccogli i nomi delle release come prima
                List<String> releaseNames = new ArrayList<>();
                for(Release release : ticket.getAv()) {
                    releaseNames.add(release.getName());
                }

                // 2. Unisci i nomi in una singola stringa usando un separatore diverso (es. ";")
                String affectedVersionsString = String.join(";", releaseNames);

                // --- FINE MODIFICA ---

                fileWriter.append(ticket.getTicketID()).append(",")
                        .append(String.valueOf(ticket.getCreationDate())).append(",")
                        .append(String.valueOf(ticket.getResolutionDate())).append(",")
                        .append(ticket.getIv() != null ? ticket.getIv().getName() : "N/A").append(",")
                        .append(ticket.getOv() != null ? ticket.getOv().getName() : "N/A").append(",")
                        .append(ticket.getFv() != null ? ticket.getFv().getName() : "N/A").append(",")
                        .append(affectedVersionsString).append(",") // <-- USA LA NUOVA STRINGA
                        .append(String.valueOf(ticket.getCommitList().size())) // <-- AGGIUNGI numOfCommits
                        .append(DELIMITER);
            }
        } catch (IOException e) {
            LOGGER.info(ERROR);
        }
    }

    public static void printReleases(String project, List<Release> releaseList, String name) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists() && !file.mkdirs()) {
            throw new IOException("Could not create directory: " + file.getAbsolutePath());
        }
        file = new File(MAINDIR + project + SLASH + name);
        try(FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.append("id,releaseName,releaseDate,numOfCommits\n");
            for (Release release : releaseList) {
                fileWriter.append(String.valueOf(release.getId())).append(",")
                        .append(release.getName()).append(",")
                        .append(String.valueOf(release.getDate())).append(",")
                        .append(String.valueOf(release.getCommitList().size())).append(DELIMITER);
            }
        } catch (IOException e) {
            LOGGER.info(ERROR);
        }
    }

    public static void printMethods(String project, List<JavaMethod> methods, String name) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists() && !file.mkdirs()) {
            throw new IOException("Could not create directory: " + file.getAbsolutePath());
        }
        file = new File(MAINDIR + project + SLASH + name);
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.append("fullyQualifiedName,firstCommit,#Commits\n");
            for (JavaMethod m : methods) {
                RevCommit firstCommit = m.getFirstCommit();
                String firstCommitName = (firstCommit != null) ? firstCommit.getName() : "";
                fileWriter.append(escapeCSV(m.getFullyQualifiedName())).append(",")
                        .append(escapeCSV(firstCommitName)).append(",")
                        .append(String.valueOf(m.getCommits().size()))
                        .append(DELIMITER);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, ERROR);
        }
    }

    // --- METODO MODIFICATO E AGGIORNATO ---
    public static void printMethodsDataset(String projectName, List<JavaMethod> methods) throws IOException {
        String projectDirName = projectName.toLowerCase();
        File projectCsvDir = new File(CSV_FILES_DIR + projectDirName);

        if (!projectCsvDir.exists() && !projectCsvDir.mkdirs()) {
                LOGGER.log(Level.SEVERE, "ERRORE: Impossibile creare la cartella {0}", projectCsvDir.getAbsolutePath());
        }

        File datasetFile = new File(projectCsvDir.getAbsolutePath() + SLASH + "Dataset.csv");
        Console.info("Scrittura del dataset in: " + datasetFile.getAbsolutePath());

        // --- NUOVA PARTE: Inizializzazione dei contatori per le statistiche ---
        int totalInstances = 0;
        int buggyInstances = 0;
        // Usiamo un HashSet per contare le release uniche
        java.util.Set<Integer> uniqueReleaseIDs = new java.util.HashSet<>();
        // --- FINE NUOVA PARTE ---

        try (FileWriter fileWriter = new FileWriter(datasetFile)) {
            // L'intestazione del CSV rimane invariata
            fileWriter.append("MethodFullyQualifiedName").append(SEPARATOR)
                    .append("ReleaseID").append(SEPARATOR)
                    .append("LOC").append(SEPARATOR)
                    .append("NumParameters").append(SEPARATOR)
                    .append("NumBranches").append(SEPARATOR)
                    .append("NestingDepth").append(SEPARATOR)
                    .append("NumCodeSmells").append(SEPARATOR)
                    .append("NumLocalVariables").append(SEPARATOR)
                    .append("NumRevisions").append(SEPARATOR)
                    .append("NumAuthors").append(SEPARATOR)
                    .append("TotalStmtAdded").append(SEPARATOR)
                    .append("TotalStmtDeleted").append(SEPARATOR)
                    .append("MaxChurn").append(SEPARATOR)
                    .append("AvgChurn").append(SEPARATOR)
                    .append("HasFixHistory").append(SEPARATOR)
                    .append("IsBuggy")
                    .append(DELIMITER);

            // Scrivi i dati per ogni metodo
            for (JavaMethod method : methods) {
                // --- NUOVA PARTE: Aggiornamento delle statistiche ---
                totalInstances++;
                if (method.isBuggy()) {
                    buggyInstances++;
                }
                if (method.getRelease() != null) {
                    uniqueReleaseIDs.add(method.getRelease().getId());
                }
                // --- FINE NUOVA PARTE ---

                String releaseIdStr = (method.getRelease() != null) ? String.valueOf(method.getRelease().getId()) : "N/A";

                // Il resto della scrittura del file rimane identico...
                fileWriter.append(escapeCSV(method.getFullyQualifiedName())).append(SEPARATOR)
                        .append(releaseIdStr).append(SEPARATOR)
                        .append(String.valueOf(method.getLoc())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumParameters())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumBranches())).append(SEPARATOR)
                        .append(String.valueOf(method.getNestingDepth())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumCodeSmells())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumLocalVariables())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumRevisions())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumAuthors())).append(SEPARATOR)
                        .append(String.valueOf(method.getTotalStmtAdded())).append(SEPARATOR)
                        .append(String.valueOf(method.getTotalStmtDeleted())).append(SEPARATOR)
                        .append(String.valueOf(method.getMaxChurn())).append(SEPARATOR)
                        .append(String.format(Locale.US, "%.4f", method.getAvgChurn())).append(SEPARATOR)
                        .append(String.valueOf(method.getHasFixHistory())).append(SEPARATOR)
                        .append(method.isBuggy() ? "yes" : "no")
                        .append(DELIMITER);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore durante la scrittura del dataset CSV: {0}", e.getMessage());
        }

        // --- NUOVA PARTE: Calcolo finale e stampa delle statistiche ---
        int nonBuggyInstances = totalInstances - buggyInstances;
        double buggyPercentage = (totalInstances > 0) ? (100.0 * buggyInstances / totalInstances) : 0;
        int numReleases = uniqueReleaseIDs.size();

        // Crea la stringa del riepilogo
        String statsSummary = String.format(
                "\n--- STATISTICHE DI BASE DEL DATASET ---\n" +
                        " Progetto:                      %s\n" +
                        " Numero di Release analizzate:   %d\n" +
                        " Totale Istanze (metodo-release): %d\n" +
                        "   - Istanze Buggy ('yes'):     %d\n" +
                        "   - Istanze Non-Buggy ('no'):  %d\n" +
                        " Percentuale Istanze Buggy:     %.2f%%\n" +
                        "---------------------------------------",
                projectName, numReleases, totalInstances, buggyInstances, nonBuggyInstances, buggyPercentage
        );

        // Stampa il riepilogo sul flusso di output standard (console normale, non "rossa")
        Console.info(statsSummary);
        // --- FINE MODIFICA ---
    }

    // ... [Il resto della classe (printEvaluationResults, escapeCSV, etc.) rimane invariato] ...

    public static void printEvaluationResults(String projectName, List<ClassifierEvaluation> results, String fileSuffix) throws IOException {
        String outputDir = "wekaFiles/" + projectName.toLowerCase() + "/";

        // Assicura che la cartella esista
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Costruisce il nome del file aggiungendo il suffisso prima dell'estensione .csv
        String filename = outputDir + "classificationResults" + fileSuffix + ".csv";

        try (FileWriter writer = new FileWriter(filename)) {
            // Usa il metodo statico per ottenere l'header del CSV
            writer.append(ClassifierEvaluation.getCsvHeader()).append(DELIMITER); // Usando la tua costante DELIMITER

            // Itera sui risultati e li scrive nel file
            for (ClassifierEvaluation result : results) {
                writer.append(result.toCsvString()).append(DELIMITER); // Usando la tua costante DELIMITER
            }

            // Logga il nome completo del file per chiarezza
            Console.info("Weka Evaluation results written to " + filename);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"Error writing Weka Evaluation results to {0}: {1}", new Object[]{filename, e.getMessage()});
        }
    }

    private static String escapeCSV(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            field = field.replace("\"", "\"\"");
            return "\"" + field + "\"";
        }
        return field;
    }

    public static void createAcumeFile(String project, String validationType, List<AcumeMethod> methods, String fileName) throws IOException {
        String projectLower = project.toLowerCase();

        // Costruisce il percorso completo includendo la sottocartella per il tipo di validazione.
        // Esempio: "acumeFiles/bookkeeper/walk_forward/"
        String dirPath = String.format("acumeFiles/%s/%s/", projectLower, validationType);

        // Crea le cartelle di destinazione se non esistono
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
                // Lancia un'eccezione se la creazione della cartella fallisce
                throw new IOException("Could not create directory: " + dir.getAbsolutePath());
        }

        // Crea il percorso completo del file CSV
        File file = new File(dir, fileName + ".csv");

        // Utilizza try-with-resources per garantire che FileWriter venga chiuso automaticamente
        try (FileWriter fileWriter = new FileWriter(file)) {

            // Scrive l'intestazione del file CSV
            fileWriter.append("ID,Size,PredictedProbability,ActualValue\n");

            // Scrive una riga per ogni metodo, usando i getter che restituiscono già String
            for (AcumeMethod m : methods) {
                fileWriter.append(m.getId())
                        .append(",")
                        .append(m.getSize())
                        .append(",")
                        .append(m.getPredictedProbability())
                        .append(",")
                        .append(m.getActualValue()) // Questo getter restituisce già "YES" o "NO"
                        .append("\n");
            }

            fileWriter.flush();
        }

    }
}

