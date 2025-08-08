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
import java.util.logging.Logger;

public class PrintUtils {

    private PrintUtils(){}

    public static final String DELIMITER = "\n";
    public static final String SEPARATOR = ",";
    public static final String CLASS = PrintUtils.class.getName();
    private static final Logger logger = Logger.getLogger(CLASS);
    private static final String MAINDIR = "reportFiles/";
    public static final String SLASH = "/";
    public static final String ERROR = "Error in writeOnReportFiles when trying to create directory";
    private static final String CSV_FILES_DIR = "csvFiles/"; // Nuova cartella base per i CSV

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
            logger.info(ERROR);
        }
    }

    public static void printTickets(String project, List<Ticket> ticketList) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists() && !file.mkdirs()) {
            throw new IOException("Could not create directory: " + file.getAbsolutePath());
        }

        file = new File(MAINDIR + project + SLASH + "AllTickets.csv");
        try(FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.append("key,creationDate,resolutionDate,injectedVersion,openingVersion,fixedVersion,affectedVersion,numOfCommits\n");
            List<Ticket> ticketOrderedByCreation = new ArrayList<>(ticketList);
            ticketOrderedByCreation.sort(Comparator.comparing(Ticket::getCreationDate));
            for (Ticket ticket : ticketOrderedByCreation) {
                List<String> iDs = new ArrayList<>();
                for(Release release : ticket.getAv()) {
                    iDs.add(release.getName());
                }
                fileWriter.append(ticket.getTicketID()).append(",")
                        .append(String.valueOf(ticket.getCreationDate())).append(",")
                        .append(String.valueOf(ticket.getResolutionDate())).append(",")
                        .append(ticket.getIv() != null ? ticket.getIv().getName() : "N/A").append(",")
                        .append(ticket.getOv() != null ? ticket.getOv().getName() : "N/A").append(",")
                        .append(ticket.getFv() != null ? ticket.getFv().getName() : "N/A").append(",")
                        .append(String.valueOf(iDs)).append(DELIMITER);
            }
        } catch (IOException e) {
            logger.info(ERROR);
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
            logger.info(ERROR);
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
            logger.info(ERROR);
        }
    }

    // --- METODO MODIFICATO E AGGIORNATO ---
    public static void printMethodsDataset(String projectName, List<JavaMethod> methods) throws IOException {
        String projectDirName = projectName.toLowerCase();
        File projectCsvDir = new File(CSV_FILES_DIR + projectDirName);

        if (!projectCsvDir.exists()) {
            if (!projectCsvDir.mkdirs()) {
                logger.severe("ERRORE: Impossibile creare la cartella " + projectCsvDir.getAbsolutePath());
                throw new IOException("Impossibile creare la cartella: " + projectCsvDir.getAbsolutePath());
            }
        }

        File datasetFile = new File(projectCsvDir.getAbsolutePath() + SLASH + "Dataset.csv");
        logger.info("Scrittura del dataset in: " + datasetFile.getAbsolutePath());

        try (FileWriter fileWriter = new FileWriter(datasetFile)) {
            // Scrivi l'intestazione del CSV con tutte le nuove metriche
            fileWriter.append("MethodFullyQualifiedName").append(SEPARATOR)
                    .append("ReleaseID").append(SEPARATOR)
                    // Metriche di Dimensione e Complessità
                    .append("LOC").append(SEPARATOR)
                    .append("NumParameters").append(SEPARATOR)
                    .append("NumBranches").append(SEPARATOR)
                    .append("NestingDepth").append(SEPARATOR)
                    .append("NumCodeSmells").append(SEPARATOR)
                    .append("NumLocalVariables").append(SEPARATOR)
                    // Metriche Storiche
                    .append("NumRevisions").append(SEPARATOR)
                    .append("NumAuthors").append(SEPARATOR)
                    .append("TotalStmtAdded").append(SEPARATOR)
                    .append("TotalStmtDeleted").append(SEPARATOR)
                    .append("MaxChurn").append(SEPARATOR)
                    .append("AvgChurn").append(SEPARATOR)
                    .append("HasFixHistory").append(SEPARATOR)
                    // Etichetta (Target)
                    .append("IsBuggy")
                    .append(DELIMITER);

            // Scrivi i dati per ogni metodo
            for (JavaMethod method : methods) {
                String releaseIdStr = (method.getRelease() != null) ? String.valueOf(method.getRelease().getId()) : "N/A";

                fileWriter.append(escapeCSV(method.getFullyQualifiedName())).append(SEPARATOR)
                        .append(releaseIdStr).append(SEPARATOR)
                        // Metriche di Dimensione e Complessità
                        .append(String.valueOf(method.getLoc())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumParameters())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumBranches())).append(SEPARATOR)
                        .append(String.valueOf(method.getNestingDepth())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumCodeSmells())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumLocalVariables())).append(SEPARATOR)
                        // Metriche Storiche
                        .append(String.valueOf(method.getNumRevisions())).append(SEPARATOR)
                        .append(String.valueOf(method.getNumAuthors())).append(SEPARATOR)
                        .append(String.valueOf(method.getTotalStmtAdded())).append(SEPARATOR)
                        .append(String.valueOf(method.getTotalStmtDeleted())).append(SEPARATOR)
                        .append(String.valueOf(method.getMaxChurn())).append(SEPARATOR)
                        .append(String.format(Locale.US, "%.4f", method.getAvgChurn())).append(SEPARATOR)
                        .append(String.valueOf(method.getHasFixHistory())).append(SEPARATOR)
                        // Etichetta
                        .append(method.isBuggy() ? "yes" : "no")
                        .append(DELIMITER);
            }
        } catch (IOException e) {
            logger.severe("Errore durante la scrittura del dataset CSV: " + e.getMessage());
            throw e;
        }
    }

    // ... [Il resto della classe (printEvaluationResults, escapeCSV, etc.) rimane invariato] ...

    public static void printEvaluationResults(String projectName, List<ClassifierEvaluation> results) throws IOException {
        String outputDir = "wekaFiles/" + projectName.toLowerCase() + "/";
        new File(outputDir).mkdirs();
        String filename = outputDir + "classificationResults.csv";
        try (FileWriter writer = new FileWriter(filename)) {
            writer.append(ClassifierEvaluation.getCsvHeader()).append(DELIMITER);
            for (ClassifierEvaluation result : results) {
                writer.append(result.toCsvString()).append(DELIMITER);
            }
            logger.info("Weka Evaluation results written to " + filename);
        } catch (IOException e) {
            logger.severe("Error writing Weka Evaluation results: " + e.getMessage());
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

    public static void createAcumeFile(String project, List<AcumeMethod> methods, String fileName) throws IOException {
        String projectLower = project.toLowerCase();

        // Crea la cartella di destinazione se non esiste
        File dir = new File("acumeFiles/" + projectLower);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Could not create directory: " + dir.getAbsolutePath());
            }
        }

        // Scrive il file CSV
        File file = new File(dir.getAbsolutePath() + "/" + fileName + ".csv");
        try (FileWriter fileWriter = new FileWriter(file)) {

            fileWriter.append("ID,Size,Predicted %,Actual value\n");
            for (AcumeMethod m : methods) {
                fileWriter.append(m.getId()).append(",")
                        .append(m.getSize()).append(",")
                        .append(m.getPredictedProbability()).append(",")
                        .append(m.getActualValue()).append("\n");
            }
            fileWriter.flush();
        }
        // Il logger è meglio del print, ma per ora va bene
        System.out.println("ACUME file created: " + file.getAbsolutePath());
    }
}

