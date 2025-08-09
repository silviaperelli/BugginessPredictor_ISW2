package utils;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.stmt.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class MetricsAnalyzerFromFile {

    public static void main(String[] args) throws IOException {

        String projectName = "BOOKKEEPER";
        String inputFile = "/Users/silviaperelli/Desktop/Refactoring_AFMethod_" + projectName.toLowerCase() + ".java";

        // Crea la cartella "refactoringReport" sulla Scrivania se non esiste
        String outputDir = "refactoringReport";
        Files.createDirectories(Paths.get(outputDir));
        String outputFile = outputDir + "/feature_comparison.csv";

        System.out.println("Analizzando il file: " + inputFile);
        System.out.println("Salvando il report in: " + outputFile + "\n");

        String codeToAnalyze = new String(Files.readAllBytes(Paths.get(inputFile)));
        String classWrapper = "class DummyWrapperClass { \n" + codeToAnalyze + "\n }";
        CompilationUnit cu = StaticJavaParser.parse(classWrapper);

        Optional<MethodDeclaration> originalMethodOpt = cu.findFirst(MethodDeclaration.class, md -> md.getNameAsString().equals("readEntry"));
        Optional<MethodDeclaration> refactoredMainMethodOpt = cu.findFirst(MethodDeclaration.class, md -> md.getNameAsString().equals("readEntry2"));
        List<MethodDeclaration> allRefactoredMethods = cu.findAll(MethodDeclaration.class, md -> !md.getNameAsString().equals("readEntry"));

        if (!originalMethodOpt.isPresent() || !refactoredMainMethodOpt.isPresent()) {
            System.err.println("ERRORE: Impossibile trovare i metodi 'readEntry' e/o 'readEntry2' nel file.");
            return;
        }

        // try-with-resources per garantire la chiusura automatica del file
        try (FileWriter fileWriter = new FileWriter(outputFile);
             PrintWriter writer = new PrintWriter(fileWriter)) {

            // Stampa l'header del CSV nel file
            writer.println("MethodName,Version,LOC,NumParameters,NumBranches,NestingDepth,NumCodeSmells,NumLocalVariables");

            // Analizza e stampa le metriche per il metodo originale NEL FILE
            printOriginalMetrics(originalMethodOpt.get(), writer);

            // Analizza e stampa le metriche aggregate per il sistema refattorizzato NEL FILE
            printRefactoredMetrics(refactoredMainMethodOpt.get(), allRefactoredMethods, writer);

        } catch (IOException e) {
            System.err.println("ERRORE: Impossibile scrivere il file CSV.");
            e.printStackTrace();
        }

        System.out.println("Analisi completata. Report CSV generato con successo.");
    }

    private static void printOriginalMetrics(MethodDeclaration md, PrintWriter writer) {
        int loc = calculateLOC(md);
        int numParams = md.getParameters().size();
        int numBranches = calculateNumBranches(md);
        int cyclomaticComplexity = numBranches + 1;
        int nestingDepth = calculateNestingDepth(md);
        int numSmells = calculateCodeSmells(md, cyclomaticComplexity, loc, nestingDepth, numParams);
        int numVars = calculateNumLocalVariables(md);

        // Scrive la riga CSV sul file
        writer.printf("%s,%s,%d,%d,%d,%d,%d,%d%n",
                "readEntry", "Original", loc, numParams, numBranches, nestingDepth, numSmells, numVars);
    }

    private static void printRefactoredMetrics(MethodDeclaration mainRefactored, List<MethodDeclaration> allRefactored, PrintWriter writer) {
        int totalLoc = 0;
        int totalBranches = 0;
        int maxNesting = 0;
        int totalSmells = 0;
        int totalVars = 0;

        writer.println();
        // --- STAMPA IL DETTAGLIO DI OGNI METODO ESTRATTO ---
        writer.println("// --- Dettaglio Metodi Refattorizzati ---");

        for (MethodDeclaration md : allRefactored) {
            int loc = calculateLOC(md);
            int numParams = md.getParameters().size();
            int numBranches = calculateNumBranches(md);
            int cyclomaticComplexity = numBranches + 1;
            int nestingDepth = calculateNestingDepth(md);
            int numSmells = calculateCodeSmells(md, cyclomaticComplexity, loc, nestingDepth, numParams);
            int numVars = calculateNumLocalVariables(md);

            // Stampa la riga per il singolo metodo
            // Aggiungiamo un suffisso per distinguerli
            String versionTag = md.getNameAsString().equals("readEntry2") ? "Refactored_EntryPoint" : "Refactored_Helper";
            writer.printf("%s,%s,%d,%d,%d,%d,%d,%d%n",
                    md.getNameAsString(), versionTag, loc, numParams, numBranches, nestingDepth, numSmells, numVars);

            // Aggregazione delle metriche per riepilogo
            totalLoc += loc; //somma loc di tutti i metodi estratti
            totalBranches += numBranches; //somma numero di branches di tutti i metodi estratti
            if (nestingDepth > maxNesting) maxNesting = nestingDepth; //massimo valore di nestingDepth tra tutti i metodi estratti
            totalSmells += numSmells; // //somma numero di code smells di tutti i metodi estratti
            totalVars += numVars; //somma numero di variabili locali di tutti i metodi estratti
        }

        // Il numero di parametri è quello del metodo principale del sistema refattorizzato
        int mainParams = mainRefactored.getParameters().size();

        // Stampa una riga vuota e l'header per il riepilogo, per chiarezza
        writer.println();
        writer.println("// --- Riepilogo Aggregato per Confronto (Feature 1 vs Feature 2) ---");
        writer.println("MethodName,Version,LOC,NumParameters,NumBranches,NestingDepth,NumCodeSmells,NumLocalVariables");

        // Stampa la riga CSV aggregata sul file
        writer.printf("%s,%s,%d,%d,%d,%d,%d,%d%n",
                "readEntry (refactored system)", "Refactored_Aggregate", totalLoc, mainParams, totalBranches, maxNesting, totalSmells, totalVars);
    }

    // ===========================================================================
    // LOGICA DI CALCOLO (IDENTICA A GitDataExtractor)
    // ===========================================================================

    private static int calculateLOC(MethodDeclaration md) {
        if (md.getBody().isPresent()) {
            String[] lines = md.getBody().get().toString().split("\r\n|\r|\n");
            boolean inMultiLineComment = false;
            int locCount = 0;

            for (String line : lines) {
                String trimmedLine = line.trim();

                if (trimmedLine.startsWith("/*")) {
                    inMultiLineComment = true;
                    if (trimmedLine.endsWith("*/") && trimmedLine.length() > 2) {
                        inMultiLineComment = false;
                    }
                    continue;
                }

                if (trimmedLine.endsWith("*/")) {
                    inMultiLineComment = false;
                    continue;
                }

                if (inMultiLineComment) {
                    continue;
                }

                if (!trimmedLine.isEmpty() &&
                        !trimmedLine.startsWith("//") &&
                        !(trimmedLine.equals("{") || trimmedLine.equals("}"))) {
                    locCount++;
                }
            }
            return locCount;
        }
        return 0;
    }


    private static int calculateNumBranches(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        int branches = 0;
        branches += md.findAll(IfStmt.class).size();
        branches += md.findAll(ConditionalExpr.class).size();
        branches += md.findAll(ForStmt.class).size();
        branches += md.findAll(ForEachStmt.class).size();
        branches += md.findAll(WhileStmt.class).size();
        branches += md.findAll(DoStmt.class).size();
        for (SwitchStmt switchStmt : md.findAll(SwitchStmt.class)) {
            branches += switchStmt.getEntries().size();
        }
        branches += md.findAll(CatchClause.class).size();
        return branches;
    }

    private static int calculateNestingDepth(MethodDeclaration md) {
        NestingDepthVisitor nestingVisitor = new NestingDepthVisitor();
        if (!md.getBody().isPresent()) return 0;
        nestingVisitor.reset();
        md.getBody().get().accept(nestingVisitor, null);
        return nestingVisitor.getMaxDepth();
    }

    private static int calculateNumLocalVariables(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        return md.getBody().get().findAll(VariableDeclarator.class).size();
    }

    private static int calculateCodeSmells(MethodDeclaration md, int cyclomaticComplexity, int loc, int nestingDepth, int numParameters) {
        if (!md.getBody().isPresent()) return 0;
        int smellCount = 0;
        BlockStmt body = md.getBody().get();

        if (cyclomaticComplexity > 7) smellCount++;
        if (loc > 30) smellCount++;
        if (nestingDepth > 4) smellCount++;
        if (numParameters > 5) smellCount++;

        for (SwitchStmt switchStmt : body.findAll(SwitchStmt.class)) {
            if (switchStmt.getEntries().stream().noneMatch(entry -> entry.getLabels().isEmpty())) {
                smellCount++;
            }
        }
        for (CatchClause catchClause : body.findAll(CatchClause.class)) {
            if (catchClause.getBody().getStatements().isEmpty()) {
                smellCount++;
            }
        }
        if (body.findAll(InstanceOfExpr.class).size() > 2) {
            smellCount++;
        }
        String methodName = md.getNameAsString();
        if (methodName.equals("equals") || methodName.equals("hashCode") || methodName.equals("toString")) {
            if (md.getAnnotations().stream().noneMatch(a -> a.getNameAsString().equals("Override"))) {
                smellCount++;
            }
        }
        long magicNumberCount = body.findAll(IntegerLiteralExpr.class).stream()
                .filter(n -> { try { int val = n.asInt(); return val != 0 && val != 1 && val != -1; } catch (Exception e) { return true; } })
                .filter(n -> n.getParentNode().map(p -> !(p instanceof VariableDeclarator)).orElse(true))
                .count();
        if (magicNumberCount > 1) {
            smellCount++;
        }
        return smellCount;
    }

}
