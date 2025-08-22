package controller;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.stmt.*;
import utils.NestingDepthVisitor;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MetricsAnalyzerFromFile {

    public static void main(String[] args) throws IOException {

        String projectName = "SYNCOPE";
        String originalMethodName;
        String refactoredMethodName;

        // Cambia questa variabile per decidere quale refactoring analizzare
        String feature = "NSmell"; // Opzioni: "NSmell" o "LOC"

        if ("BOOKKEEPER".equals(projectName)){
            if ("NSmell".equals(feature)){
                originalMethodName = "readEntry";
                refactoredMethodName = "readEntry2";
            } else {
                originalMethodName = "main";
                refactoredMethodName = "main2";
            }
        }
        else {
            feature = "NSmell";
            originalMethodName = "getTaskTO";
            refactoredMethodName = "getTaskTO2";
        }

        String dir = "refactoringReport";
        String inputFile = String.format("%s/Refactoring_%s_%s.java", dir, feature, projectName.toLowerCase());
        String outputFile = String.format("%s/feature_comparison_%s_%s.csv", dir, feature, projectName.toLowerCase());

        Files.createDirectories(Paths.get(dir));

        System.out.println("Analizzando il file: " + inputFile);
        System.out.println("Salvando il report in: " + outputFile + "\n");

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
            System.err.println("ERRORE GRAVE DI PARSING: Controlla che il file " + inputFile + " contenga codice Java valido.");
            e.printStackTrace();
            return;
        }

        // --- INIZIO MODIFICA 1: Ricerca metodi più precisa ---
        // Prima troviamo la classe wrapper che abbiamo creato
        Optional<ClassOrInterfaceDeclaration> wrapperClassOpt = cu.findFirst(ClassOrInterfaceDeclaration.class, c -> c.getNameAsString().equals("DummyWrapperClass"));
        if (!wrapperClassOpt.isPresent()) {
            System.err.println("ERRORE: Impossibile trovare la classe wrapper 'DummyWrapperClass'.");
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
            System.err.printf("ERRORE: Impossibile trovare i metodi '%s' e/o '%s' nel file.%n", originalMethodName, refactoredMethodName);
            return;
        }

        try (FileWriter fileWriter = new FileWriter(outputFile);
             PrintWriter writer = new PrintWriter(fileWriter)) {

            writer.println("MethodName,Version,LOC,NumParameters,NumBranches,NestingDepth,NumCodeSmells,NumLocalVariables");

            printMetrics(originalMethodOpt.get(), "Original", writer);

            printRefactoredMetrics(refactoredEntryPointOpt.get(), allRefactoredMethods, writer);

        } catch (IOException e) {
            System.err.println("ERRORE: Impossibile scrivere il file CSV.");
            e.printStackTrace();
        }

        System.out.println("Analisi completata. Report CSV generato con successo.");
    }

    private static void printMetrics(MethodDeclaration md, String version, PrintWriter writer) {
        int loc = calculateLOC(md);
        int numParams = md.getParameters().size();
        int numBranches = calculateNumBranches(md);
        int cyclomaticComplexity = numBranches + 1;
        int nestingDepth = calculateNestingDepth(md);
        int numSmells = calculateCodeSmells(md, cyclomaticComplexity, loc, nestingDepth, numParams);
        int numVars = calculateNumLocalVariables(md);

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
            printMetrics(md, versionTag, writer); // Riutilizziamo il metodo di stampa

            totalLoc += calculateLOC(md);
            totalBranches += calculateNumBranches(md);
            int nestingDepth = calculateNestingDepth(md);
            if (nestingDepth > maxNesting) maxNesting = nestingDepth;
            int cyclomaticComplexity = calculateNumBranches(md) + 1;
            totalSmells += calculateCodeSmells(md, cyclomaticComplexity, calculateLOC(md), nestingDepth, md.getParameters().size());
            totalVars += calculateNumLocalVariables(md);
        }

        int mainParams = mainRefactored.getParameters().size();

        writer.println();
        writer.println("// --- Riepilogo Aggregato per Confronto (Feature 1 vs Feature 2) ---");

        // --- INIZIO MODIFICA 2: Rimozione dell'header duplicato ---
        // La riga seguente è stata rimossa per evitare un header duplicato nel CSV
        // writer.println("MethodName,Version,LOC,NumParameters,NumBranches,NestingDepth,NumCodeSmells,NumLocalVariables");
        // --- FINE MODIFICA 2 ---

        writer.printf("%s (refactored system),%s,%d,%d,%d,%d,%d,%d%n",
                mainRefactored.getNameAsString(), "Refactored_Aggregate", totalLoc, mainParams, totalBranches, maxNesting, totalSmells, totalVars);
    }

    // ... (tutti i metodi di calcolo delle metriche rimangono invariati) ...

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