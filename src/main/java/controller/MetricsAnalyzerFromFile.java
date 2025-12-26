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

public class MetricsAnalyzerFromFile {

    private final String projectName;
    private final String feature;

    private static final Logger LOGGER = Logger.getLogger(MetricsAnalyzerFromFile.class.getName());

    public MetricsAnalyzerFromFile(String projectName, String feature) {
        this.projectName = projectName;
        this.feature = feature;
    }

    public void execute() throws IOException {
        String originalMethodName;
        String refactoredMethodName;

        // La logica di selezione ora è più semplice
        if ("BOOKKEEPER".equals(projectName)) {
            if ("NSmell".equals(feature)) {
                originalMethodName = "readEntry";
                refactoredMethodName = "readEntry2";
            } else { // Assumiamo LOC
                originalMethodName = "main";
                refactoredMethodName = "main2";
            }
        } else { // Assumiamo SYNCOPE
            originalMethodName = "getTaskTO";
            refactoredMethodName = "getTaskTO2";
        }

        String dir = "refactoringReport";
        String inputFile = String.format("%s/Refactoring_%s_%s.java", dir, feature, projectName.toLowerCase());
        String outputFile = String.format("%s/feature_comparison_%s_%s.csv", dir, feature, projectName.toLowerCase());

        if (!Files.exists(Paths.get(inputFile))) {
            LOGGER.log(Level.SEVERE,"\nERRORE: File di input non trovato: {0}", inputFile);
            return;
        }

        Files.createDirectories(Paths.get(dir));
        Console.info("Analizzando il file: " + inputFile);
        Console.info("Salvando il report in: " + outputFile + "\n");

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
            LOGGER.log(Level.SEVERE,"ERRORE GRAVE DI PARSING: Controlla che il file {0} contenga codice Java valido.", inputFile);
            e.printStackTrace();
            return;
        }

        // --- INIZIO MODIFICA 1: Ricerca metodi più precisa ---
        // Prima troviamo la classe wrapper che abbiamo creato
        Optional<ClassOrInterfaceDeclaration> wrapperClassOpt = cu.findFirst(ClassOrInterfaceDeclaration.class, c -> c.getNameAsString().equals("DummyWrapperClass"));
        if (!wrapperClassOpt.isPresent()) {
            LOGGER.log(Level.SEVERE,"ERRORE: Impossibile trovare la classe wrapper 'DummyWrapperClass'.");
            return;
        }
        ClassOrInterfaceDeclaration wrapperClass = wrapperClassOpt.get();

        // Ora cerchiamo i metodi solo all'interno della classe wrapper
        Optional<MethodDeclaration> originalMethodOpt = wrapperClass.getMethodsByName(originalMethodName).stream().findFirst();
        Optional<MethodDeclaration> refactoredEntryPointOpt = wrapperClass.getMethodsByName(refactoredMethodName).stream().findFirst();

        // Prendiamo tutti i metodi della classe wrapper, escluso quello originale
        List<MethodDeclaration> allRefactoredMethods = wrapperClass.getMethods().stream()
                .filter(md -> !md.getNameAsString().equals(originalMethodName))
                .collect(Collectors.toList());
        // --- FINE MODIFICA 1 ---

        if (!originalMethodOpt.isPresent() || !refactoredEntryPointOpt.isPresent()) {
            LOGGER.log(Level.SEVERE,"ERRORE: Impossibile trovare i metodi {0} e/o {1} nel file.", new Object[]{originalMethodName, refactoredMethodName});
            return;
        }

        try (FileWriter fileWriter = new FileWriter(outputFile);
             PrintWriter writer = new PrintWriter(fileWriter)) {

            writer.println("MethodName,Version,LOC,NumParameters,NumBranches,NestingDepth,NumCodeSmells,NumLocalVariables");

            printMetrics(originalMethodOpt.get(), "Original", writer);

            printRefactoredMetrics(refactoredEntryPointOpt.get(), allRefactoredMethods, writer);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"ERRORE: Impossibile scrivere il file CSV.");
            e.printStackTrace();
        }

        Console.info("Analisi completata. Report CSV generato con successo.");
    }

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

    private static void printRefactoredMetrics(MethodDeclaration mainRefactored, List<MethodDeclaration> allRefactored, PrintWriter writer) {
        int totalLoc = 0;
        int totalBranches = 0;
        int maxNesting = 0;
        int totalSmells = 0;
        int totalVars = 0;

        writer.println();
        writer.println("// --- Dettaglio Metodi Refattorizzati ---");

        for (MethodDeclaration md : allRefactored) {
            String versionTag = md.getNameAsString().equals(mainRefactored.getNameAsString()) ? "Refactored_EntryPoint" : "Refactored_Helper";
            printMetrics(md, versionTag, writer);

            int currentLoc = MetricsCalculator.calculateLOC(md);
            int currentBranches = MetricsCalculator.calculateNumBranches(md);
            int currentNesting = MetricsCalculator.calculateNestingDepth(md);
            int currentParams = md.getParameters().size();
            totalLoc += currentLoc;
            totalBranches += currentBranches;
            if (currentNesting > maxNesting) maxNesting = currentNesting;
            totalSmells += MetricsCalculator.calculateCodeSmells(md, currentBranches + 1, currentLoc, currentNesting, currentParams);
            totalVars += MetricsCalculator.calculateNumLocalVariables(md);
        }

        int mainParams = mainRefactored.getParameters().size();

        writer.println();
        writer.println("// --- Riepilogo Aggregato per Confronto (Feature 1 vs Feature 2) ---");

        writer.printf("%s (refactored system),%s,%d,%d,%d,%d,%d,%d%n",
                mainRefactored.getNameAsString(), "Refactored_Aggregate", totalLoc, mainParams, totalBranches, maxNesting, totalSmells, totalVars);
    }
}