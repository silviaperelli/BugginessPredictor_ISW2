package utils;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.stmt.*;
import java.util.Arrays;
import java.util.List;

/**
 * Classe di utilità che centralizza tutti i calcoli delle metriche del codice sorgente
 */
public class MetricsCalculator {

    private MetricsCalculator() {}

    /**
     * Calcola le Linee di Codice (LOC) effettive di un metodo.
     * Vengono escluse le righe vuote e i commenti
     */
    public static int calculateLOC(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        String[] lines = md.getBody().get().toString().split("\r\n|\r|\n");
        boolean inMultiLineComment = false;
        int locCount = 0;
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (inMultiLineComment) {
                if (trimmedLine.endsWith("*/")) inMultiLineComment = false;
            } else if (trimmedLine.startsWith("/*")) {
                inMultiLineComment = true;
                if (trimmedLine.endsWith("*/") && trimmedLine.length() > 2) inMultiLineComment = false;
            } else if (isValidCodeLine(trimmedLine)) {
                locCount++;
            }
        }
        return locCount;
    }

    /**
     * Metodo helper per `calculateLOC`. Verifica se una riga di codice è effettiva
     */
    public static boolean isValidCodeLine(String trimmedLine) {
        return !trimmedLine.isEmpty() &&
                !trimmedLine.startsWith("//") &&
                !(trimmedLine.equals("{") || trimmedLine.equals("}"));
    }

    /**
     * Calcola il numero di decision points in un metodo
     */
    public static int calculateNumBranches(MethodDeclaration md) {
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

    /**
     * Calcola la massima profondità di annidamento dei blocchi di controllo
     */
    public static int calculateNestingDepth(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        NestingDepthVisitor nestingVisitor = new NestingDepthVisitor();
        nestingVisitor.reset();
        md.getBody().get().accept(nestingVisitor, null);
        return nestingVisitor.getMaxDepth();
    }

    /**
     * Calcola il numero totale di variabili locali dichiarate all'interno di un metodo
     */
    public static int calculateNumLocalVariables(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        return md.getBody().get().findAll(VariableDeclarator.class).size();
    }

    /**
     * Calcola un punteggio aggregato di Code Smells basato su diverse euristiche
     */
    public static int calculateCodeSmells(MethodDeclaration md, int cyclomaticComplexity, int loc, int nestingDepth, int numParameters) {
        if (!md.getBody().isPresent()) return 0;
        int smellCount = 0;
        BlockStmt body = md.getBody().get();
        if (cyclomaticComplexity > 7) smellCount++;
        if (loc > 30) smellCount++;
        if (nestingDepth > 4) smellCount++;
        if (numParameters > 5) smellCount++;
        smellCount += countStructuralSmells(body);
        if (isMissingOverride(md)) smellCount++;
        if (hasMagicNumberSmell(body)) smellCount++;
        return smellCount;
    }

    /**
     * Metodo helper per `calculateCodeSmells`. Conta gli smell strutturali come switch senza default,
     * catch vuoti e uso eccessivo di `instanceof`
     */
    private static int countStructuralSmells(BlockStmt body) {
        int count = 0;
        for (SwitchStmt switchStmt : body.findAll(SwitchStmt.class)) {
            if (switchStmt.getEntries().stream().noneMatch(entry -> entry.getLabels().isEmpty())) count++;
        }
        for (CatchClause catchClause : body.findAll(CatchClause.class)) {
            if (catchClause.getBody().getStatements().isEmpty()) count++;
        }
        if (body.findAll(InstanceOfExpr.class).size() > 2) count++;
        return count;
    }

    /**
     * Metodo helper per `calculateCodeSmells`. Verifica se ai metodi standard
     * (`equals`, `hashCode`, `toString`) manca l'annotazione `@Override`
     */
    private static boolean isMissingOverride(MethodDeclaration md) {
        String methodName = md.getNameAsString();
        List<String> standardMethods = Arrays.asList("equals", "hashCode", "toString");
        return standardMethods.contains(methodName) &&
                md.getAnnotations().stream().noneMatch(a -> a.getNameAsString().equals("Override"));
    }

    /**
     * Metodo helper per `calculateCodeSmells`. Identifica la presenza di "Magic Numbers"
     */
    private static boolean hasMagicNumberSmell(BlockStmt body) {
        long magicNumberCount = body.findAll(IntegerLiteralExpr.class).stream()
                .filter(n -> {
                    try {
                        int val = n.asInt();
                        return val != 0 && val != 1 && val != -1;
                    } catch (Exception e) { return true; }
                })
                .filter(n -> n.getParentNode().map(p -> !(p instanceof VariableDeclarator)).orElse(true))
                .count();
        return magicNumberCount > 1;
    }
}
