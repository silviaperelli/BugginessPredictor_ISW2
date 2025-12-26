package controller;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import utils.MetricsCalculator;
import utils.PrintUtils.Console;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Analizza un file Java contenente una versione originale e una soggetta a refactoring di un metodo,
 * calcola le metriche di qualità per entrambe e le confronta in un report CSV.
 */
public class MetricsAnalyzerFromFile {

    private final String projectName;
    private final String feature;

    private static final Logger LOGGER = Logger.getLogger(MetricsAnalyzerFromFile.class.getName());

    public MetricsAnalyzerFromFile(String projectName, String feature) {
        this.projectName = projectName;
        this.feature = feature;
    }

    /**
     * Metodo principale che orchestra l'analisi del file di refactoring
     */
    public void execute() throws IOException {
        String originalMethodName;
        String refactoredMethodName;

        // Determina i nomi dei metodi da cercare in base al progetto e alla feature
        if ("BOOKKEEPER".equals(projectName)) {
            if ("NSmell".equals(feature)) {
                originalMethodName = "readEntry";
                refactoredMethodName = "readEntry2";
            } else {
                originalMethodName = "main";
                refactoredMethodName = "main2";
            }
        } else { // Assumiamo SYNCOPE
            originalMethodName = "getTaskTO";
            refactoredMethodName = "getTaskTO2";
        }

        // Definisce i percorsi dei file di input e output
        String dir = "refactoringReport";
        String inputFile = String.format("%s/Refactoring_%s_%s.java", dir, feature, projectName.toLowerCase());
        String outputFile = String.format("%s/feature_comparison_%s_%s.csv", dir, feature, projectName.toLowerCase());

        if (!Files.exists(Paths.get(inputFile))) {
            LOGGER.log(Level.SEVERE,"\nERROR: Input file not found: {0}", inputFile);
            return;
        }

        Files.createDirectories(Paths.get(dir));
        Console.info("Analyzing file: " + inputFile);
        Console.info("Saving report to: " + outputFile + "\n");

        // Legge il file e lo prepara per il parsing "avvolgendolo" in una classe fittizia
        List<String> allLines = Files.readAllLines(Paths.get(inputFile));
        String importsSection = allLines.stream()
                .filter(line -> line.trim().startsWith("import"))
                .collect(Collectors.joining("\n"));
        String methodsCodeSection = allLines.stream()
                .filter(line -> !line.trim().startsWith("import"))
                .collect(Collectors.joining("\n"));
        String fullCodeToParse = importsSection + "\n\n" + "class DummyWrapperClass { \n" + methodsCodeSection + "\n }";

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(fullCodeToParse);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,"PARSING ERROR: Check if file {0} contains valid Java code", inputFile);
            e.printStackTrace();
            return;
        }

        // Cerca la classe wrapper e i metodi di interesse al suo interno
        Optional<ClassOrInterfaceDeclaration> wrapperClassOpt = cu.findFirst(ClassOrInterfaceDeclaration.class, c -> c.getNameAsString().equals("DummyWrapperClass"));
        if (!wrapperClassOpt.isPresent()) {
            LOGGER.log(Level.SEVERE,"ERROR: Impossible to find wrapper class 'DummyWrapperClass'");
            return;
        }
        ClassOrInterfaceDeclaration wrapperClass = wrapperClassOpt.get();

        Optional<MethodDeclaration> originalMethodOpt = wrapperClass.getMethodsByName(originalMethodName).stream().findFirst();
        Optional<MethodDeclaration> refactoredEntryPointOpt = wrapperClass.getMethodsByName(refactoredMethodName).stream().findFirst();

        List<MethodDeclaration> allRefactoredMethods = wrapperClass.getMethods().stream()
                .filter(md -> !md.getNameAsString().equals(originalMethodName))
                .collect(Collectors.toList());

        if (!originalMethodOpt.isPresent() || !refactoredEntryPointOpt.isPresent()) {
            LOGGER.log(Level.SEVERE,"ERROR: Impossible to find methods {0} and/or  {1} in file.", new Object[]{originalMethodName, refactoredMethodName});
            return;
        }

        // Scrive i risultati nel file CSV
        try (FileWriter fileWriter = new FileWriter(outputFile);
             PrintWriter writer = new PrintWriter(fileWriter)) {

            writer.println("MethodName,Version,LOC,NumParameters,NumBranches,NestingDepth,NumCodeSmells,NumLocalVariables");

            // Stampa le metriche per il metodo originale
            printMetrics(originalMethodOpt.get(), "Original", writer);
            // Stampa le metriche per il metodo soggetto a refactoring
            printRefactoredMetrics(refactoredEntryPointOpt.get(), allRefactoredMethods, writer);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"ERROR: Impossible to write CSV file");
            e.printStackTrace();
        }

        Console.info("Analysis completed. CSV report generated successfully");
    }

    /**
     * Calcola tutte le metriche per un singolo metodo e le stampa come una riga in un file CSV
     */
    private static void printMetrics(MethodDeclaration md, String version, PrintWriter writer) {
        int loc = MetricsCalculator.calculateLOC(md);
        int numParams = md.getParameters().size();
        int numBranches = MetricsCalculator.calculateNumBranches(md);
        int cyclomaticComplexity = numBranches + 1;
        int nestingDepth = MetricsCalculator.calculateNestingDepth(md);
        int numSmells = MetricsCalculator.calculateCodeSmells(md, cyclomaticComplexity, loc, nestingDepth, numParams);
        int numVars = MetricsCalculator.calculateNumLocalVariables(md);

        writer.printf("%s,%s,%d,%d,%d,%d,%d,%d%n",
                md.getNameAsString(), version, loc, numParams, numBranches, nestingDepth, numSmells, numVars);
    }

    /**
     * Calcola e stampa le metriche per il metodo soggetto a refactoring.
     * Stampa sia il dettaglio per ogni metodo "helper" sia un riepilogo aggregato
     */
    private static void printRefactoredMetrics(MethodDeclaration mainRefactored, List<MethodDeclaration> allRefactored, PrintWriter writer) {
        int totalLoc = 0;
        int totalBranches = 0;
        int maxNesting = 0;
        int totalSmells = 0;
        int totalVars = 0;

        writer.println();
        writer.println("// --- Dettaglio Metodi Refattorizzati ---");

        // Itera su tutti i metodi (entry-point + helper)
        for (MethodDeclaration md : allRefactored) {
            String versionTag = md.getNameAsString().equals(mainRefactored.getNameAsString()) ? "Refactored_EntryPoint" : "Refactored_Helper";
            printMetrics(md, versionTag, writer); // Stampa la riga di dettaglio

            // Calcola le metriche aggregate
            int currentLoc = MetricsCalculator.calculateLOC(md);
            int currentBranches = MetricsCalculator.calculateNumBranches(md);
            int currentNesting = MetricsCalculator.calculateNestingDepth(md);
            int currentParams = md.getParameters().size();
            totalLoc += currentLoc;
            totalBranches += currentBranches;
            if (currentNesting > maxNesting) maxNesting = currentNesting; // Prende il nesting massimo
            totalSmells += MetricsCalculator.calculateCodeSmells(md, currentBranches + 1, currentLoc, currentNesting, currentParams);
            totalVars += MetricsCalculator.calculateNumLocalVariables(md);
        }

        int mainParams = mainRefactored.getParameters().size();

        writer.println();
        writer.println("// --- Riepilogo Aggregato per Confronto (Feature 1 vs Feature 2) ---");

        // Stampa la riga con le metriche aggregate
        writer.printf("%s (refactored system),%s,%d,%d,%d,%d,%d,%d%n",
                mainRefactored.getNameAsString(), "Refactored_Aggregate", totalLoc, mainParams, totalBranches, maxNesting, totalSmells, totalVars);
    }
}