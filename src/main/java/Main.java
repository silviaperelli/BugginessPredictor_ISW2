import controller.GitDataExtractor;
import controller.JiraDataExtractor;
import controller.WekaClassification;
import model.JavaMethod;
import model.Release;
import model.Ticket;
import utils.PrintUtils;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    public static void main(String[] args) throws Exception {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.SEVERE);

        String print;
        String project1 = "BOOKKEEPER";
        String project2 = "SYNCOPE";

        // --- ESEGUI L'ANALISI PER IL PRIMO PROGETTO ---
        //runAnalysisForProject(project1);

        // --- ESEGUI L'ANALISI PER IL SECONDO PROGETTO ---
        runAnalysisForProject(project2);
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
        gitExtractor.getAllCommitsAndAssignToReleases();
        System.out.println(projectName + ": commits assigned to releases.");

        gitExtractor.filterCommitsOfIssues();
        ticketList = gitExtractor.getTicketList(); // Aggiorna la lista di ticket
        System.out.println(projectName + ": commits filtered by ticket IDs.");

        List<JavaMethod> allMethods = gitExtractor.getMethodsFromReleases();
        System.out.println(projectName + ": " + allMethods.size() + " method entries extracted across analyzed releases.");

        System.out.println("Labeling method bugginess...");
        gitExtractor.setMethodBuggyness(allMethods);

        PrintUtils.printMethodsDataset(projectName, allMethods);
        System.out.println(projectName + ": Dataset CSV created successfully.");
        System.out.println("--- Phase 1 Complete ---");

        // --- FASE 2: WEKA CLASSIFICATION ---
        System.out.println("\n--- Phase 2: Weka Classification ---");

        // --- 2. CREA UN'ISTANZA DELLA NUOVA CLASSE DI ANALISI ---
        WekaClassification wekaAnalysis = new WekaClassification(projectName, allMethods);

        // --- 3. ESEGUI L'ANALISI WALK-FORWARD ---
        wekaAnalysis.execute();

        System.out.println("--- Phase 2 Complete ---");
        System.out.println("\n==================================================");
        System.out.println("ANALYSIS FOR " + projectName.toUpperCase() + " FINISHED");
        System.out.println("==================================================");

    }
}