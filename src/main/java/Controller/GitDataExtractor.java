package Controller;

import Model.JavaMethod;
import Model.Release;
import Model.Ticket;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import utils.GitUtils;
import utils.NestingDepthVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class GitDataExtractor {
    private List<Ticket> ticketList;
    private List<Release> releaseList; //first 34% of releases
    private List<Release> fullReleaseList;
    private List<RevCommit> commitList;
    private Git git;
    private Repository repository;
    //java parser to take method into Java Classes
    private JavaParser javaParser;
    private NestingDepthVisitor nestingVisitor;

    public GitDataExtractor(String projName,  List<Release> allReleases, List<Ticket> ticketList) throws IOException {

        ParserConfiguration parserConfiguration = new ParserConfiguration();
        this.javaParser = new JavaParser(parserConfiguration);

        InitCommand init = Git.init();
        File repoDir = new File("/Users/silviaperelli/Desktop/"+projName.toLowerCase()+"_isw2");
        File gitDir = new File(repoDir, ".git");

        if(!gitDir.exists()){
            System.err.println("Git directory does not exist: " + repoDir.getAbsolutePath());
        }

        try {
            this.git = Git.open(repoDir);
        } catch (IOException e) {
            throw new IOException("Impossible to open the repository Git in " + repoDir.getAbsolutePath(), e);
        }

        this.repository = git.getRepository();
        this.fullReleaseList = new ArrayList<>(allReleases);
        this.fullReleaseList.sort(Comparator.comparing(Release::getDate));
        this.releaseList = new ArrayList<>();
        this.ticketList = ticketList;
        this.commitList = new ArrayList<>();
        this.nestingVisitor = new NestingDepthVisitor();
    }

    public List<Ticket> getTicketList() {
        return ticketList;
    }

    public List<Release> getReleaseList() {
        return releaseList;
    }

    public List<Release> getFullReleaseList() {
        return fullReleaseList;
    }

    public void setTicketList(List<Ticket> ticketList) {
        this.ticketList = ticketList;
    }

    //method to ignore the last 66% of releases
    public void setReleaseListForAnalysis() {
        if (this.fullReleaseList == null || this.fullReleaseList.isEmpty()) {
            return;
        }

        //ignore last 66% of releases
        int releasesToConsider = (int) Math.ceil(this.fullReleaseList.size() * 0.34);
        if (releasesToConsider == 0 && !this.fullReleaseList.isEmpty()) {
            releasesToConsider = 1;
        }

        this.releaseList = new ArrayList<>(this.fullReleaseList.subList(0, releasesToConsider));

        int i = 0;
        for (Release release : this.releaseList) {
            release.setId(++i);
        }
    }

    public List<RevCommit> getAllCommitsAndAssignToReleases() throws GitAPIException, IOException {

        if (this.ticketList == null) {
            System.err.println("Ticket list non inizializzata.");
            return null;
        }

        if (!commitList.isEmpty()) {
            return commitList;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Iterable<RevCommit> commitsIterable = git.log().all().call();
        commitsIterable.forEach(commitList::add);
        // sort commits by date from the latest to the most recent
        commitList.sort(Comparator.comparing(c -> c.getCommitterIdent().getWhen()));

        for (RevCommit commit : commitList) {
            LocalDate commitDate = LocalDate.parse(formatter.format(commit.getCommitterIdent().getWhen()));
            LocalDate lowerBoundDate = LocalDate.parse(formatter.format(new Date(0)));

            //assign commits to release
            for (Release release : this.fullReleaseList) {
                LocalDate releaseDate = release.getDate();
                //add the commit to a release only if the commit date is before the release date and after the previous release.
                if (!commitDate.isBefore(lowerBoundDate) && !commitDate.isAfter(releaseDate)) {
                    release.addCommit(commit); // Aggiunge a fullReleaseList
                }
                lowerBoundDate = releaseDate;
            }
        }

        filterAndRenumberReleases();

        setReleaseListForAnalysis();

        return commitList;
    }

    // assign commit to ticket buggy and delete ticket list with an empty commit list
    public List<RevCommit> filterCommitsOfIssues() {
        List<RevCommit> filteredCommits = new ArrayList<>();
        if (commitList.isEmpty()) {
            System.err.println("Lista commit vuota. Chiamare prima getAllCommitsAndAssignToReleases().");
            return filteredCommits;
        }

        for (RevCommit commit : commitList) {
            for (Ticket ticket : this.ticketList) {
                String commitMessage = commit.getFullMessage();
                String ticketKey = ticket.getTicketID();

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                LocalDate commitDate = LocalDate.parse(formatter.format(commit.getCommitterIdent().getWhen()));

                if (ticketKey != null && !ticketKey.isEmpty() &&
                        commitMessage.contains(ticketKey) &&
                        ticket.getResolutionDate() != null && !commitDate.isAfter(ticket.getResolutionDate()) &&
                        ticket.getCreationDate() != null && !commitDate.isBefore(ticket.getCreationDate())) {

                    if (!filteredCommits.contains(commit)) {
                        filteredCommits.add(commit);
                    }
                    ticket.addCommit(commit);
                }
            }
        }

        //remove tickets that don't have associated commit
        this.ticketList.removeIf(ticket -> ticket.getCommitList().isEmpty());
        return filteredCommits;
    }

    public List<JavaMethod> getMethodsFromReleases() throws IOException, GitAPIException {
        List<JavaMethod> allMethods = new ArrayList<>();
        Set<String> processedMethodsForRelease = new HashSet<>(); // Per evitare duplicati FQN per singola release

        for (Release release : this.releaseList) { // Solo release per analisi
            processedMethodsForRelease.clear();
            List<RevCommit> releaseCommits = release.getCommitList();
            if (releaseCommits.isEmpty()) continue;

            // Prendi l'ultimo commit della release per avere lo snapshot dei file
            // Assumendo che i commit in release.getCommitList() siano ordinati per data
            releaseCommits.sort(Comparator.comparing(c -> c.getCommitterIdent().getWhen()));
            RevCommit lastCommitOfRelease = releaseCommits.get(releaseCommits.size() - 1);

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(lastCommitOfRelease.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    String filePath = treeWalk.getPathString();
                    if (filePath.endsWith(".java") && !filePath.contains("/test/")) {
                        ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        loader.copyTo(output);
                        String fileContent = output.toString();

                        try {
                            CompilationUnit cu = StaticJavaParser.parse(fileContent);
                            cu.findAll(MethodDeclaration.class).forEach(md -> {
                                String methodSignature = JavaMethod.getSignature(md);
                                String fqn = filePath + "/" + methodSignature;

                                if (!processedMethodsForRelease.contains(fqn)) {
                                    JavaMethod javaMethod = new JavaMethod(fqn, release);
                                    // Calcola LOC statico qui
                                    javaMethod.setLoc(calculateLOC(md));
                                    javaMethod.setNumParameters(md.getParameters().size());
                                    javaMethod.setNumBranches(calculateNumBranches(md));
                                    javaMethod.setNestingDepth(calculateNestingDepth(md));
                                    javaMethod.setNumLocalVariables(calculateNumLocalVariables(md));

                                    allMethods.add(javaMethod);
                                    release.addMethod(javaMethod); // Associa il metodo alla sua release
                                    javaMethod.setBodyHash(calculateBodyHash(md));
                                    processedMethodsForRelease.add(fqn);
                                }
                            });
                        } catch (ParseProblemException | StackOverflowError e) {
                            System.err.println("Errore di parsing per il file: " + filePath + " nel commit " + lastCommitOfRelease.getName() + ". " + e.getMessage());
                        }
                    }
                }
            }
        }
        // Associazione commit ai metodi (storico)
        addCommitsToMethods(allMethods, this.commitList);
        calculateHasFixHistory(allMethods);
        return allMethods;
    }

    private int calculateNestingDepth(MethodDeclaration md) {
        if (md == null || !md.getBody().isPresent()) {
            return 0;
        }
        // Usa il campo della classe, resettandolo prima di ogni uso
        this.nestingVisitor.reset();
        md.getBody().get().accept(this.nestingVisitor, null);
        return this.nestingVisitor.getMaxDepth();
    }

    private int calculateNumLocalVariables(MethodDeclaration md) {
        if (md == null || !md.getBody().isPresent()) {
            return 0;
        }
        // findAll(VariableDeclarator.class) trova tutte le dichiarazioni di variabili.
        // Esempi: "int x", "int y=0, z", "String s"
        return md.getBody().get().findAll(VariableDeclarator.class).size();
    }

    private String calculateBodyHash(MethodDeclaration md) {
        if (md == null) return null;
        String normalizedBody = normalizeMethodBody(md);
        if (normalizedBody.isEmpty()) return "EMPTY_BODY_HASH"; // O un altro placeholder
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(normalizedBody.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 Hashing error", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String normalizeMethodBody(MethodDeclaration md) {
        if (md == null || !md.getBody().isPresent()) {
            return "";
        }
        // Rimuovi commenti, spazi bianchi eccessivi, ecc.
        // Questo è un esempio MOLTO SEMPLICE. Una normalizzazione robusta è complessa.
        String body = md.getBody().get().toString();
        body = body.replaceAll("//.*|/\\*(?s:.*?)\\*/", ""); // Rimuovi commenti
        body = body.replaceAll("\\s+", " "); // Sostituisci spazi multipli con uno singolo
        return body.trim();
    }


    private int calculateLOC(MethodDeclaration md) {
        if (md.getBody().isPresent()) {
            String[] lines = md.getBody().get().toString().split("\r\n|\r|\n");
            boolean inMultiLineComment = false;
            int locCount = 0;

            for (String line : lines) {
                String trimmedLine = line.trim();

                if (trimmedLine.startsWith("/*")) {
                    inMultiLineComment = true;
                    // Se il commento finisce sulla stessa riga:
                    if (trimmedLine.endsWith("*/") && trimmedLine.length() > 2) { // >2 per evitare "/*/"
                        inMultiLineComment = false;
                        // Se c'è codice prima del commento /* ... */ sulla stessa riga,
                        // questa logica non lo conta. È una semplificazione.
                        // Per contarlo, dovresti splittare la riga o usare regex più complesse.
                    }
                    // In ogni caso, la riga che inizia con /* non conta come LOC
                    continue;
                }

                if (trimmedLine.endsWith("*/")) {
                    inMultiLineComment = false;
                    // La riga che finisce con */ non conta come LOC
                    continue;
                }

                if (inMultiLineComment) {
                    // Se siamo dentro un commento multiriga, la riga non conta
                    continue;
                }

                // Ora applica i filtri rimanenti su righe non di commento
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


    public void calculateHasFixHistory(List<JavaMethod> allMethods) {
        Map<String, Ticket> commitNameToTicketMap = new HashMap<>();
        for (Ticket ticket : this.ticketList) {
            // Assicurati che ticketList contenga solo i bug (lo fa già dalla query JIRA)
            for (RevCommit commit : ticket.getCommitList()) {
                commitNameToTicketMap.put(commit.getName(), ticket);
            }
        }

        for (JavaMethod method : allMethods) {
            Release currentMethodRelease = method.getRelease();

            for (RevCommit commit : method.getCommits()) {
                // Controlla se il commit è un "fix commit" (associato a un ticket di bug)
                if (commitNameToTicketMap.containsKey(commit.getName())) {

                    Release commitRelease = GitUtils.getReleaseOfCommit(commit, this.fullReleaseList);
                    if (commitRelease != null && commitRelease.getId() < currentMethodRelease.getId()) {
                        method.setHasFixHistory(1);
                        break;
                    }
                }
            }
        }
    }

    public void addCommitsToMethods(List<JavaMethod> allMethods, List<RevCommit> allCommits) throws IOException, GitAPIException {
        // Ordina i commit per processarli cronologicamente
        List<RevCommit> sortedCommits = new ArrayList<>(allCommits);
        sortedCommits.sort(Comparator.comparing(RevCommit::getCommitTime));

        for (RevCommit commit : sortedCommits) {
            if (commit.getParentCount() == 0) continue; // Salta il primo commit (nessun genitore per il diff)

            RevCommit parent = commit.getParent(0);
            List<DiffEntry> diffs = getDiffEntries(parent, commit);

            Map<String, String> oldFileContents = getFileContents(parent, diffs, true);
            Map<String, String> newFileContents = getFileContents(commit, diffs, false);

            for (DiffEntry diff : diffs) {
                String filePath;
                boolean isDelete = diff.getChangeType() == DiffEntry.ChangeType.DELETE;

                if (isDelete) {
                    filePath = diff.getOldPath();
                } else {
                    filePath = diff.getNewPath();
                }

                if (!filePath.endsWith(".java") || filePath.contains("/test/")) {
                    continue;
                }

                String oldContent = oldFileContents.getOrDefault(diff.getOldPath(), "");
                String newContent = newFileContents.getOrDefault(diff.getNewPath(), "");

                Map<String, MethodDeclaration> oldMethods = parseMethods(oldContent);
                Map<String, MethodDeclaration> newMethods = parseMethods(newContent);

                // Metodi aggiunti o modificati
                for (Map.Entry<String, MethodDeclaration> newMethodEntry : newMethods.entrySet()) {
                    String signature = newMethodEntry.getKey();
                    MethodDeclaration newMd = newMethodEntry.getValue();
                    MethodDeclaration oldMd = oldMethods.get(signature); //might be null if methods is new

                    String newBodyHash = calculateBodyHash(newMd);
                    String oldBodyHash = (oldMd != null) ? calculateBodyHash(oldMd) : null;


                    boolean changed = (oldMd == null) || (oldBodyHash != null && !oldBodyHash.equals(newBodyHash));

                    if (changed) {
                        updateMethodMetricsForCommit(allMethods, filePath, newMd, commit, oldMd, newMd, newBodyHash);
                    }
                }
            }
        }

        // Calcola NAuth
        for (JavaMethod method : allMethods) {
            if (method.getCommits() != null && !method.getCommits().isEmpty()) { // Assicura che la lista non sia null o vuota
                Set<String> authors = method.getCommits().stream()
                        .map(c -> c.getAuthorIdent().getName()) // Assumendo che AuthorIdent e Name non siano null
                        .filter(Objects::nonNull) // Filtra nomi null se possibile
                        .collect(Collectors.toSet());
                method.setNumAuthors(authors.size());
            } else {
                method.setNumAuthors(0); // Nessun commit, nessun autore
            }

            if (method.getNumRevisions() > 0) {
                double avgChurn = (double) (method.getTotalStmtAdded() + method.getTotalStmtDeleted()) / method.getNumRevisions();
                method.setAvgChurn(avgChurn);
            } else {
                method.setAvgChurn(0.0);
            }
        }



    }

    private List<DiffEntry> getDiffEntries(RevCommit parent, RevCommit commit) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setContext(0); // Nessuna linea di contesto, solo le differenze
            return diffFormatter.scan(parent.getTree(), commit.getTree());
        }
    }

    private Map<String, String> getFileContents(RevCommit commit, List<DiffEntry> diffs, boolean useOldPath) throws IOException {
        Map<String, String> contents = new HashMap<>();
        try (ObjectReader reader = repository.newObjectReader()) {
            for (DiffEntry diff : diffs) {
                String path = useOldPath ? diff.getOldPath() : diff.getNewPath();
                ObjectId id = useOldPath ? diff.getOldId().toObjectId() : diff.getNewId().toObjectId();

                if (DiffEntry.DEV_NULL.equals(path)) continue; // Skip /dev/null

                try {
                    ObjectLoader loader = reader.open(id);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    loader.copyTo(output);
                    contents.put(path, output.toString());
                } catch (org.eclipse.jgit.errors.MissingObjectException e) {
                    // Oggetto non trovato, potrebbe essere un file binario o un problema
                    System.err.println("Missing object: " + id + " for path " + path + " in commit " + commit.getName());
                }
            }
        }
        return contents;
    }

    private Map<String, MethodDeclaration> parseMethods(String content) {
        Map<String, MethodDeclaration> methods = new HashMap<>();
        if (content == null || content.isEmpty()) return methods;
        try {
            CompilationUnit cu = StaticJavaParser.parse(content);
            cu.findAll(MethodDeclaration.class).forEach(md -> {
                methods.put(JavaMethod.getSignature(md), md);
            });
        } catch (ParseProblemException | StackOverflowError e) {
            // Ignora errori di parsing per file singoli durante il diff, ma logga
            //System.err.println("Errore di parsing durante il diff: " + e.getMessage());
        }
        return methods;
    }

    private void updateMethodMetricsForCommit(List<JavaMethod> allProjectMethods, String filePath, MethodDeclaration currentMdAst, RevCommit commit,  MethodDeclaration oldMdAst, MethodDeclaration newMdAst, String newBodyHash) {
        String signature = JavaMethod.getSignature(currentMdAst);
        String fqn = filePath + "/" + signature;
        Release releaseOfCommit = GitUtils.getReleaseOfCommit(commit, this.fullReleaseList); // Usa full per trovare la release corretta del commit

        if (releaseOfCommit == null) return; // Commit non appartiene a nessuna release tracciata

        // Trova le istanze del metodo nelle release ANALIZZATE che sono UGUALI alla release del commit
        for (JavaMethod projectMethod : allProjectMethods) {
            if (projectMethod.getFullyQualifiedName().equals(fqn) && projectMethod.getRelease().getId() >= releaseOfCommit.getId()) { // Il metodo in questa release o future è affetto

                projectMethod.addCommit(commit); // Aggiunge il commit allo storico del metodo
                projectMethod.incrementNumRevisions();
                projectMethod.setBodyHash(newBodyHash);

                int currentCommitStmtAdded = 0;
                int currentCommitStmtDeleted = 0;

                // Calcola StmtAdded/Deleted per QUESTO commit specifico
                if (oldMdAst != null && newMdAst != null) { // Modifica
                    int locOld = calculateLOC(oldMdAst);
                    int locNew = calculateLOC(newMdAst);
                    if (locNew > locOld) {
                        currentCommitStmtAdded = locNew - locOld;
                        projectMethod.addStmtAdded(currentCommitStmtAdded);
                    }
                    if (locOld > locNew) {
                        currentCommitStmtDeleted = locOld - locNew;
                        projectMethod.addStmtDeleted(currentCommitStmtDeleted);
                    }
                } else if (newMdAst != null) { // Aggiunta
                    currentCommitStmtAdded = calculateLOC(newMdAst);
                    projectMethod.addStmtAdded(calculateLOC(newMdAst));
                }
                // La rimozione è più complessa da gestire qui perché l'oggetto JavaMethod potrebbe non esistere più
                int currentCommitChurn = currentCommitStmtAdded + currentCommitStmtDeleted;
                if (currentCommitChurn > projectMethod.getMaxChurn()) {
                    projectMethod.setMaxChurn(currentCommitChurn);
                }
            }
        }
    }

    private boolean methodBodiesEqual(MethodDeclaration md1, MethodDeclaration md2) {
        String body1 = md1.getBody().map(Object::toString).orElse("");
        String body2 = md2.getBody().map(Object::toString).orElse("");
        return body1.equals(body2);
    }


    public void setMethodBuggyness(List<JavaMethod> allProjectMethods) {
        if (this.ticketList == null) {
            System.err.println("Ticket list non inizializzata per setMethodBuggyness.");
            return;
        }

        for (JavaMethod projectMethod : allProjectMethods) {
            projectMethod.setBuggy(false); // Reset iniziale
        }

        for (Ticket ticket : this.ticketList) {
            Release injectedVersion = ticket.getIv();
            if (injectedVersion == null) continue; // IV non definito per questo ticket

            for (RevCommit fixCommit : ticket.getCommitList()) {
                Release fixedVersion = GitUtils.getReleaseOfCommit(fixCommit, this.fullReleaseList);
                if (fixedVersion == null) continue; // Commit di fix non appartiene a una release tracciata

                try {
                    if (fixCommit.getParentCount() == 0) continue;
                    RevCommit parentOfFix = fixCommit.getParent(0);
                    List<DiffEntry> diffs = getDiffEntries(parentOfFix, fixCommit);

                    Map<String, String> newFileContentsInFix = getFileContents(fixCommit, diffs, false);

                    for (DiffEntry diff : diffs) {
                        String filePath = diff.getNewPath();
                        if (!filePath.endsWith(".java") || filePath.contains("/test/")) continue;

                        String newContent = newFileContentsInFix.getOrDefault(filePath, "");
                        Map<String, MethodDeclaration> newMethodsInFix = parseMethods(newContent);

                        // Per determinare quali metodi sono stati *effettivamente* modificati dal fix
                        String oldContentInFix = getFileContents(parentOfFix, Collections.singletonList(diff), true).getOrDefault(diff.getOldPath(), "");
                        Map<String, MethodDeclaration> oldMethodsInFix = parseMethods(oldContentInFix);


                        for (Map.Entry<String, MethodDeclaration> fixedMethodEntry : newMethodsInFix.entrySet()) {
                            String signature = fixedMethodEntry.getKey();
                            MethodDeclaration fixedMd = fixedMethodEntry.getValue();
                            MethodDeclaration preFixMd = oldMethodsInFix.get(signature);

                            String hashFixed = calculateBodyHash(fixedMd);
                            String hashPreFix = calculateBodyHash(preFixMd);

                            boolean actuallyChangedByFix = (preFixMd == null && fixedMd != null) ||
                                    (hashPreFix != null && hashFixed != null && !hashPreFix.equals(hashFixed)) ||
                                    (hashPreFix == null && hashFixed != null && preFixMd != null) ||
                                    (hashPreFix != null && hashFixed == null && fixedMd != null);

                            if (actuallyChangedByFix) {
                                String fqn = filePath + "/" + signature;
                                labelBuggyMethods(fqn, injectedVersion, fixedVersion, fixCommit, allProjectMethods);
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Errore durante l'analisi del commit di fix " + fixCommit.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void labelBuggyMethods(String fixedMethodFQN, Release injectedVersion, Release fixedVersion, RevCommit fixCommit, List<JavaMethod> allProjectMethods) {
        for (JavaMethod projectMethod : allProjectMethods) {
            if (projectMethod.getFullyQualifiedName().equals(fixedMethodFQN)) {
                // Aggiungi il commit di fix se il metodo appartiene alla FV e il commit lo ha toccato
                if (projectMethod.getRelease().getId() == fixedVersion.getId() && projectMethod.getCommits().contains(fixCommit) ) { // Assumiamo che getCommits contenga tutti i commit che hanno toccato il metodo in quella release
                    projectMethod.addFixCommit(fixCommit);
                }
                // Etichetta come buggy se la release del metodo è tra IV (inclusa) e FV (esclusa)
                if (projectMethod.getRelease().getId() >= injectedVersion.getId() && projectMethod.getRelease().getId() < fixedVersion.getId()) {
                    projectMethod.setBuggy(true);
                }
            }
        }
    }

    private void filterAndRenumberReleases() {
        // Remove releases with 0 commit
        this.fullReleaseList.removeIf(release -> release.getCommitList().isEmpty());

        int idCounter = 1;
        for (Release r : this.fullReleaseList) {
            r.setId(idCounter++);
        }

    }

    private int calculateNumBranches(MethodDeclaration md) {
        if (md == null || !md.getBody().isPresent()) {
            return 0;
        }
        int branches = 0;
        // Conditionals (if, ?: operator)
        branches += md.findAll(IfStmt.class).size();
        branches += md.findAll(ConditionalExpr.class).size();

        // Loops (for, foreach, while, do-while)
        branches += md.findAll(ForStmt.class).size();
        branches += md.findAll(ForEachStmt.class).size();
        branches += md.findAll(WhileStmt.class).size();
        branches += md.findAll(DoStmt.class).size();

        // Switch cases
        for (SwitchStmt switchStmt : md.findAll(SwitchStmt.class)) {
            // Ogni SwitchEntry (case X: o default:) è un ramo potenziale.
            branches += switchStmt.getEntries().size();
        }

        // Exception handling (try-catch blocks)
        branches += md.findAll(CatchClause.class).size();

        return branches;
    }

}
