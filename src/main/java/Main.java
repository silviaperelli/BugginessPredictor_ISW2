import controller.GitDataExtractor;
import controller.JiraDataExtractor;
import controller.WekaClassification;
import model.JavaMethod;
import model.Release;
import model.Ticket;
import org.eclipse.jgit.revwalk.RevCommit; // <-- Aggiunto import necessario
import utils.PrintUtils;

import java.io.IOException; // <-- Aggiunto import necessario
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    public static void main(String[] args) throws Exception {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.SEVERE);

        String project1 = "BOOKKEEPER";
        String project2 = "SYNCOPE";

        // Decommenta per eseguire l'analisi per il primo progetto
        runAnalysisForProject(project1);

        // Esegui l'analisi per il secondo progetto
        //runAnalysisForProject(project2);
    }

    /**
     * Metodo helper per eseguire l'intera pipeline per un singolo progetto.
     * @param projectName Il nome del progetto (es. "BOOKKEEPER")
     */
    public static void runAnalysisForProject(String projectName) throws Exception {
        System.out.println("\n==================================================");
        System.out.println("STARTING ANALYSIS FOR PROJECT: " + projectName.toUpperCase());
        System.out.println("==================================================");

        // --- FASE 1: DATASET CREATION ---
        System.out.println("\n--- Phase 1: Dataset Creation ---");

        JiraDataExtractor jiraExtractor = new JiraDataExtractor(projectName);
        List<Release> fullReleaseList = jiraExtractor.getReleases();
        System.out.println(projectName + ": " + fullReleaseList.size() + " releases extracted.");

        List<Ticket> ticketList = jiraExtractor.getFinalTickets(fullReleaseList, true);
        System.out.println(projectName + ": " + ticketList.size() + " tickets extracted.");

        GitDataExtractor gitExtractor = new GitDataExtractor(projectName, fullReleaseList, ticketList);
        List<RevCommit> allCommits = gitExtractor.getAllCommitsAndAssignToReleases(); // <-- Salva la lista dei commit
        System.out.println(projectName + ": commits assigned to releases.");

        gitExtractor.filterCommitsOfIssues();
        ticketList = gitExtractor.getTicketList(); // Aggiorna la lista di ticket
        System.out.println(projectName + ": commits filtered by ticket IDs.");

        List<JavaMethod> allMethods = gitExtractor.getMethodsFromReleases();
        System.out.println(projectName + ": " + allMethods.size() + " method entries extracted across analyzed releases.");

        System.out.println("Labeling method bugginess...");
        gitExtractor.setMethodBuggyness(allMethods);

        // --- INIZIO NUOVA PARTE: Stampe di Report Intermedi ---
        System.out.println("\n--- Generating Intermediate Report Files ---");
        try {
            // Stampa la lista di tutte le release analizzate con i loro dettagli
            PrintUtils.printReleases(projectName, gitExtractor.getReleaseList(), "AnalyzedReleases.csv");
            System.out.println(projectName + ": Report 'AnalyzedReleases.csv' created.");

            // Stampa la lista di tutti i ticket con i loro dettagli
            PrintUtils.printTickets(projectName, ticketList);
            System.out.println(projectName + ": Report 'AllTickets.csv' created.");

            // Stampa la lista di tutti i commit
            PrintUtils.printCommits(projectName, allCommits, "AllCommits.csv");
            System.out.println(projectName + ": Report 'AllCommits.csv' created.");

            // Stampa una vista semplificata dei metodi (opzionale, ma può essere utile)
            PrintUtils.printMethods(projectName, allMethods, "AllMethods.csv");
            System.out.println(projectName + ": Report 'AllMethods.csv' created.");

        } catch (IOException e) {
            System.err.println("Error while generating intermediate report files: " + e.getMessage());
        }
        // --- FINE NUOVA PARTE ---

        System.out.println("\nCreating the final dataset for Weka...");
        PrintUtils.printMethodsDataset(projectName, allMethods);
        System.out.println(projectName + ": Dataset CSV created successfully.");
        System.out.println("--- Phase 1 Complete ---");

        // --- FASE 2: WEKA CLASSIFICATION ---
        System.out.println("\n--- Phase 2: Weka Classification ---");

        WekaClassification wekaAnalysis = new WekaClassification(projectName, allMethods);
        wekaAnalysis.execute();

        System.out.println("--- Phase 2 Complete ---");
        System.out.println("\n==================================================");
        System.out.println("ANALYSIS FOR " + projectName.toUpperCase() + " FINISHED");
        System.out.println("==================================================");
    }
}