package controller;

import model.JavaMethod;
import model.Release;
import model.Ticket;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import utils.PrintUtils;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StarterBugginessPredictor {

    static String choiceString = "Enter your choice (1 or 2): ";
    static String notValid = "Invalid choice. Please try again.";

    private static final Logger LOGGER = Logger.getLogger(StarterBugginessPredictor.class.getName());

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        // 1. PRIMO PASSO: Scelta obbligatoria del progetto
        String selectedProject = selectProject(scanner);

        // 2. SECONDO PASSO: Menù delle azioni per il progetto scelto
        handleProjectActions(selectedProject, scanner);

        // Chiusura finale
        scanner.close();
        PrintUtils.Console.info("\nApplication terminated.");
    }

    /**
     * Mostra il menù iniziale per la selezione OBBLIGATORIA del progetto.
     * Il programma non prosegue finché non viene fatta una scelta valida.
     * @param scanner L'oggetto Scanner per leggere l'input dell'utente.
     * @return Il nome del progetto scelto ("BOOKKEEPER" o "SYNCOPE").
     */
    private static String selectProject(Scanner scanner) {
        PrintUtils.Console.info("=====================================");
        PrintUtils.Console.info("    SELECT PROJECT TO ANALYZE        ");
        PrintUtils.Console.info("=====================================");
        PrintUtils.Console.info("1. BOOKKEEPER");
        PrintUtils.Console.info("2. SYNCOPE");
        PrintUtils.Console.info(choiceString);

        while (true) { // Cicla finché non riceve un input valido
            if (!scanner.hasNextInt()) {
                PrintUtils.Console.info(notValid);
                scanner.next(); // Pulisce l'input errato
                PrintUtils.Console.info(choiceString);
                continue;
            }

            int choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    return "BOOKKEEPER";
                case 2:
                    return "SYNCOPE";
                default:
                    PrintUtils.Console.info(notValid);
                    PrintUtils.Console.info(choiceString);
            }
        }
    }

    /**
     * Mostra il sottomenù con le azioni disponibili e l'opzione per uscire.
     * @param projectName Il nome del progetto su cui operare.
     * @param scanner L'oggetto Scanner per leggere l'input dell'utente.
     */
    private static void handleProjectActions(String projectName, Scanner scanner) {
        while (true) {
            PrintUtils.Console.info("\n--- PROJECT: " + projectName.toUpperCase() + " ---");
            PrintUtils.Console.info("Select the action to perform:");
            PrintUtils.Console.info("1. Phase 1 (Dataset Creation) + Phase 2 (Weka Classification)");
            PrintUtils.Console.info("2. Feature Correlation Calculation");
            PrintUtils.Console.info("3. Refactoring Metrics Analysis (from file)");
            PrintUtils.Console.info("4. What-If Analysis");
            PrintUtils.Console.info("-------------------------------------");
            PrintUtils.Console.info("0. Exit");
            PrintUtils.Console.info("Enter your choice: ");

            while (!scanner.hasNextInt()) {
                PrintUtils.Console.info("Invalid input. Please enter a number.");
                scanner.next();
                PrintUtils.Console.info("Enter your choice: ");
            }
            int action = scanner.nextInt();

            if (action == 0) {
                return;
            }

            try {
                switch (action) {
                    case 1:
                        PrintUtils.Console.info("\n>>> Starting Phase 1 and 2...");
                        runCompleteAnalysis(projectName);
                        break;
                    case 2:
                        PrintUtils.Console.info("\n>>> Starting correlation calculation...");
                        CorrelationCalculator.calculateAndSave(projectName);
                        PrintUtils.Console.info(">>> Correlation calculation completed.");
                        break;
                    case 3:
                        PrintUtils.Console.info("\n>>> Starting refactoring metrics analysis...");

                        String featureType;
                        if ("BOOKKEEPER".equals(projectName)) {
                            // Chiedi il tipo solo per Bookkeeper
                            featureType = selectFeatureTypeForBookkeeper(scanner);
                        } else {
                            // Per Syncope, imposta direttamente NSmell
                            PrintUtils.Console.info("Analysis for SYNCOPE set to NSmell refactoring.");
                            featureType = "NSmell";
                        }

                        MetricsAnalyzerFromFile analyzer = new MetricsAnalyzerFromFile(projectName, featureType);
                        analyzer.execute();
                        break;
                    case 4:
                        PrintUtils.Console.info("\n>>> Starting What-If analysis...");
                        WhatIfAnalysis analysis = new WhatIfAnalysis(projectName);
                        analysis.execute();
                        break;
                    default:
                        PrintUtils.Console.info(notValid);
                }
            } catch (Exception e) {
                PrintUtils.Console.info("\n!!! AN ERROR OCCURRED: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static String selectFeatureTypeForBookkeeper(Scanner scanner) {
        PrintUtils.Console.info("\nSelect the refactoring type to analyze for BOOKKEEPER:");
        PrintUtils.Console.info("1. Based on LOC (Lines of Code)");
        PrintUtils.Console.info("2. Based on NSmell (Number of Code Smells)");
        PrintUtils.Console.info(choiceString);

        while (true) {
            if (!scanner.hasNextInt()) {
                PrintUtils.Console.info(notValid);
                scanner.next();
                PrintUtils.Console.info(choiceString);
                continue;
            }
            int choice = scanner.nextInt();
            if (choice == 1) return "LOC";
            if (choice == 2) return "NSmell";
            PrintUtils.Console.info(notValid);
            PrintUtils.Console.info(choiceString);
        }
    }

    /**
     * Esegue l'intera pipeline di analisi: estrazione dati, creazione dataset e classificazione.
     * @param projectName Il nome del progetto.
     */
    public static void runCompleteAnalysis(String projectName) throws IOException, GitAPIException {
        PrintUtils.Console.info("\n==================================================");
        PrintUtils.Console.info("STARTING FULL ANALYSIS FOR PROJECT: " + projectName.toUpperCase());
        PrintUtils.Console.info("==================================================");

        // --- FASE 1: DATASET CREATION ---
        PrintUtils.Console.info("\n--- Phase 1: Dataset Creation ---");

        JiraDataExtractor jiraExtractor = new JiraDataExtractor(projectName);
        List<Release> fullReleaseList = jiraExtractor.getReleases();
        PrintUtils.Console.info(projectName + ": " + fullReleaseList.size() + " releases extracted.");

        List<Ticket> ticketList = jiraExtractor.getFinalTickets(fullReleaseList, true);
        PrintUtils.Console.info(projectName + ": " + ticketList.size() + " tickets extracted.");

        GitDataExtractor gitExtractor = new GitDataExtractor(projectName, fullReleaseList, ticketList);
        List<RevCommit> allCommits = gitExtractor.getAllCommitsAndAssignToReleases();
        PrintUtils.Console.info(projectName + ": Commits assigned to releases.");

        gitExtractor.filterCommitsOfIssues();
        ticketList = gitExtractor.getTicketList();
        PrintUtils.Console.info(projectName + ": Commits filtered by ticket IDs.");

        List<JavaMethod> allMethods = gitExtractor.getMethodsFromReleases();
        PrintUtils.Console.info(projectName + ": " + allMethods.size() + " method entries extracted.");

        PrintUtils.Console.info("Labeling method bugginess...");
        gitExtractor.setMethodBuggyness(allMethods);

        PrintUtils.Console.info("\n--- Generating Intermediate Report Files ---");
        try {
            // Stampa la lista di tutte le release analizzate con i loro dettagli
            PrintUtils.printReleases(projectName, gitExtractor.getReleaseList(), "AnalyzedReleases.csv");
            PrintUtils.Console.info(projectName + ": Report 'AnalyzedReleases.csv' created.");

            // Stampa la lista di tutti i ticket con i loro dettagli
            PrintUtils.printTickets(projectName, ticketList);
            PrintUtils.Console.info(projectName + ": Report 'AllTickets.csv' created.");

            // Stampa la lista di tutti i commit
            PrintUtils.printCommits(projectName, allCommits, "AllCommits.csv");
            PrintUtils.Console.info(projectName + ": Report 'AllCommits.csv' created.");

            // Stampa una vista semplificata dei metodi (opzionale, ma può essere utile)
            PrintUtils.printMethods(projectName, allMethods, "AllMethods.csv");
            PrintUtils.Console.info(projectName + ": Report 'AllMethods.csv' created.");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"Error while generating intermediate report files: {0}", e.getMessage());
        }

        PrintUtils.Console.info("\nCreating the final dataset for Weka...");
        PrintUtils.printMethodsDataset(projectName, allMethods);
        PrintUtils.Console.info(projectName + ": Dataset CSV created successfully.");
        PrintUtils.Console.info("--- Phase 1 Complete ---");

        // --- FASE 2: WEKA CLASSIFICATION ---
        PrintUtils.Console.info("\n--- Phase 2: Weka Classification ---");
        WekaClassification wekaAnalysis = new WekaClassification(projectName, allMethods);
        wekaAnalysis.execute();
        PrintUtils.Console.info("--- Phase 2 Complete ---");

        PrintUtils.Console.info("\n==================================================");
        PrintUtils.Console.info("ANALYSIS FOR " + projectName.toUpperCase() + " FINISHED");
        PrintUtils.Console.info("==================================================");
    }
}
