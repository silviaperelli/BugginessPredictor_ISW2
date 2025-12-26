package controller;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import model.JavaMethod;
import model.Release;
import model.Ticket;
import org.eclipse.jgit.api.Git;
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
import utils.MetricsCalculator;
import utils.NestingDepthVisitor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Classe responsabile dell'estrazione e dell'analisi dei dati da un repository Git.
 * Integra le informazioni di Jira per calcolare metriche statiche e di processo
 * sui metodi Java e per etichettare la loro "bugginess"
 */
public class GitDataExtractor {
    private static final Logger LOGGER = Logger.getLogger(GitDataExtractor.class.getName());
    private static String directoryTest = "/test/";
    private static String javaExtension = ".java";

    private List<Ticket> ticketList;
    private List<Release> releaseList;
    private List<Release> fullReleaseList;
    private List<RevCommit> commitList;
    private final Git git;
    private final Repository repository;
    private final NestingDepthVisitor nestingVisitor;

    /**
     * Costruttore che inizializza l'estrattore.
     * @param projName Nome del progetto.
     * @param allReleases Lista completa delle release del progetto.
     * @param ticketList Lista dei ticket di bug estratti da Jira.
     */
    public GitDataExtractor(String projName, List<Release> allReleases, List<Ticket> ticketList) throws IOException {
        // Configurazioni iniziali
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        StaticJavaParser.setConfiguration(parserConfiguration);

        File repoDir = new File("/Users/silviaperelli/Desktop/" + projName.toLowerCase() + "_isw2");
        File gitDir = new File(repoDir, ".git");

        if (!gitDir.exists()) {
            String errorMessage = "Git directory does not exist: " + repoDir.getAbsolutePath();
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        try {
            this.git = Git.open(repoDir);
        } catch (IOException e) {
            throw new IOException("Unable to open Git repository at " + repoDir.getAbsolutePath(), e);
        }

        this.repository = git.getRepository();
        this.fullReleaseList = new ArrayList<>(allReleases);
        this.fullReleaseList.sort(Comparator.comparing(Release::getDate));
        this.releaseList = new ArrayList<>();
        this.ticketList = ticketList;
        this.commitList = new ArrayList<>();
        this.nestingVisitor = new NestingDepthVisitor();
    }

    public List<Ticket> getTicketList() { return ticketList; }
    public List<Release> getReleaseList() { return releaseList; }
    public List<Release> getFullReleaseList() { return fullReleaseList; }
    public void setTicketList(List<Ticket> ticketList) { this.ticketList = ticketList; }

    /**
     * Filtra la lista completa delle release per considerare solo la prima porzione (34%) per l'analisi.
     * Assegna inoltre un ID numerico progressivo alle release analizzate
     */
    public void setReleaseListForAnalysis() {
        if (this.fullReleaseList == null || this.fullReleaseList.isEmpty()) {
            return;
        }
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

    /**
     * Recupera tutti i commit dalla cronologia del repository e li assegna alla rispettiva release in base alla data del commit
     */
    public List<RevCommit> getAllCommitsAndAssignToReleases() throws GitAPIException, IOException {
        if (this.ticketList == null) {
            LOGGER.warning("Ticket list not initialized");
            return Collections.emptyList();
        }
        if (!commitList.isEmpty()) {
            return commitList;
        }

        // Recupera tutti i commit e li ordina cronologicamente
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Iterable<RevCommit> commitsIterable = git.log().all().call();
        commitsIterable.forEach(commitList::add);
        commitList.sort(Comparator.comparing(c -> c.getCommitterIdent().getWhen()));

        // Assegna ogni commit alla sua release
        for (RevCommit commit : commitList) {
            LocalDate commitDate = LocalDate.parse(formatter.format(commit.getCommitterIdent().getWhen()));
            LocalDate lowerBoundDate = LocalDate.parse(formatter.format(new Date(0))); // Data di inizio per il confronto

            for (Release release : this.fullReleaseList) {
                LocalDate releaseDate = release.getDate();
                // Se la data del commit è compresa tra l'inizio del periodo e la data della release, il commit appartiene a questa release
                if (!commitDate.isBefore(lowerBoundDate) && !commitDate.isAfter(releaseDate)) {
                    release.addCommit(commit);
                }
                // Aggiorna l'inizio del periodo per la prossima release
                lowerBoundDate = releaseDate;
            }
        }

        filterAndRenumberReleases();
        setReleaseListForAnalysis();
        return commitList;
    }

    /**
     * Filtra i commit per mantenere solo quelli che sono legati a un ticket di bug.
     * Un commit è considerato "di fix" se il suo messaggio contiene l'ID di un ticket
     * e se la sua data è coerente con le date di creazione e risoluzione del ticket
     */
    public List<RevCommit> filterCommitsOfIssues() {
        List<RevCommit> filteredCommits = new ArrayList<>();
        if (commitList.isEmpty()) {
            LOGGER.warning("Empty commit list");
            return filteredCommits;
        }

        for (RevCommit commit : commitList) {
            for (Ticket ticket : this.ticketList) {
                String commitMessage = commit.getFullMessage();
                String ticketKey = ticket.getTicketID();
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                LocalDate commitDate = LocalDate.parse(formatter.format(commit.getCommitterIdent().getWhen()));

                // Il messaggio del commit deve contenere l'ID del ticket e la data del commit deve essere compresa tra la creazione e la risoluzione del ticket
                if (ticketKey != null && !ticketKey.isEmpty() &&
                        commitMessage.contains(ticketKey) &&
                        ticket.getResolutionDate() != null && !commitDate.isAfter(ticket.getResolutionDate()) &&
                        ticket.getCreationDate() != null && !commitDate.isBefore(ticket.getCreationDate())) {

                    if (!filteredCommits.contains(commit)) {
                        filteredCommits.add(commit);
                    }
                    ticket.addCommit(commit); // Aggiunge il commit di fix al ticket
                }
            }
        }

        // Rimuove dalla lista i ticket per cui non abbiamo trovato nessun commit di fix
        this.ticketList.removeIf(ticket -> ticket.getCommitList().isEmpty());
        return filteredCommits;
    }

    /**
     * Estrae tutti i metodi Java dalle release selezionate e calcola le loro metriche
     */
    public List<JavaMethod> getMethodsFromReleases() throws IOException {
        List<JavaMethod> allMethods = new ArrayList<>();
        Map<String, JavaMethod> methodCache = new HashMap<>(); // Cache per evitare di ricalcolare metodi identici

        for (Release release : this.releaseList) {
            List<RevCommit> releaseCommits = release.getCommitList();
            if (releaseCommits.isEmpty()) continue;

            // Ordina i commit della release per trovare il più recente
            releaseCommits.sort(Comparator.comparing(c -> c.getCommitterIdent().getWhen()));
            RevCommit lastCommitOfRelease = releaseCommits.get(releaseCommits.size() - 1);

            // Usa un TreeWalk per navigare nell'albero dei file di questo commit
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(lastCommitOfRelease.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    if (treeWalk.getPathString().endsWith(javaExtension) && !treeWalk.getPathString().contains(directoryTest)) {
                        processJavaFile(treeWalk, release, methodCache);
                    }
                }
            }
        }

        allMethods.addAll(methodCache.values());
        // Calcola le metriche
        addCommits(allMethods, this.commitList);
        calculateHasFixHistory(allMethods);
        return allMethods;
    }

    /**
     * Processa un singolo file Java per estrarre metodi e calcolare metriche statiche
     */
    private void processJavaFile(TreeWalk treeWalk, Release release, Map<String, JavaMethod> methodCache) throws IOException {
        String filePath = treeWalk.getPathString();

        // Legge il contenuto del file dal repository Git
        ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
        String fileContent;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            loader.copyTo(output);
            fileContent = output.toString(StandardCharsets.UTF_8.name());
        }

        try {
            // Analizza il codice sorgente per trovare le dichiarazioni dei metodi
            CompilationUnit cu = StaticJavaParser.parse(fileContent);
            cu.findAll(MethodDeclaration.class).forEach(md -> {
                String methodSignature = JavaMethod.getSignature(md);
                String fqn = filePath + "/" + methodSignature;
                String cacheKey = fqn + "@" + release.getId();

                // Se questo metodo (in questa release) non è ancora stato analizzato, si delega il calcolo
                // delle metriche alla classe di utilità
                if (!methodCache.containsKey(cacheKey)) {
                    JavaMethod javaMethod = new JavaMethod(fqn, release);

                    int loc = MetricsCalculator.calculateLOC(md);
                    int numParams = md.getParameters().size();
                    int numBranches = MetricsCalculator.calculateNumBranches(md);
                    int cyclomaticComplexity = numBranches + 1;
                    int nestingDepth = MetricsCalculator.calculateNestingDepth(md);
                    int numLocalVars = MetricsCalculator.calculateNumLocalVariables(md);
                    int codeSmells = MetricsCalculator.calculateCodeSmells(md, cyclomaticComplexity, loc, nestingDepth, numParams);

                    // Imposta i valori calcolati sull'oggetto JavaMethod
                    javaMethod.setLoc(loc);
                    javaMethod.setNumParameters(numParams);
                    javaMethod.setNumBranches(numBranches);
                    javaMethod.setNestingDepth(nestingDepth);
                    javaMethod.setNumLocalVariables(numLocalVars);
                    javaMethod.setNumCodeSmells(codeSmells);

                    methodCache.put(cacheKey, javaMethod);
                }
            });
        } catch (ParseProblemException | StackOverflowError e) {
            LOGGER.log(Level.SEVERE, "Parsing error for file: {0}",filePath);
        }
    }

    /**
     * Itera su tutti i commit del progetto in ordine cronologico e, per ciascuno,
     * aggiorna le metriche dei metodi che sono stati modificati
     */
    public void addCommits(List<JavaMethod> allMethods, List<RevCommit> allCommits) throws IOException {
        // Raggruppa tutte le versioni di un metodo per il suo nome qualificato
        Map<String, List<JavaMethod>> methodMap = allMethods.stream()
                .collect(Collectors.groupingBy(JavaMethod::getFullyQualifiedName));

        // Ordina i commit per data
        List<RevCommit> sortedCommits = new ArrayList<>(allCommits);
        sortedCommits.sort(Comparator.comparing(RevCommit::getCommitTime));

        for (RevCommit commit : sortedCommits) {
            if (commit.getParentCount() == 0) continue;
            processCommitForMethodMetrics(commit, methodMap);
        }

        // Calcola NAuth e AvgChurn dopo aver processato tutti i commit
        calculateFinalMethodMetrics(allMethods);
    }

    /**
     * Processa un singolo commit per aggiornare le metriche dei metodi.
     * Calcola le differenze (diff) rispetto al suo genitore
     * e delega l'analisi di ogni file modificato al metodo successivo
     */
    private void processCommitForMethodMetrics(RevCommit commit, Map<String, List<JavaMethod>> methodMap) throws IOException {
        RevCommit parent = commit.getParent(0);

        List<DiffEntry> diffs;
        try {
            // Ottiene la lista di tutti i file aggiunti, modificati o eliminati nel commit
            diffs = getDiffEntries(parent, commit);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to calculate difference for commit {0} {1}", new Object[]{commit.getName(), e});
            return;
        }

        // Recupera il contenuto "vecchio" e "nuovo" dei file modificati
        Map<String, String> oldFileContents = getFileContents(diffs, true);
        Map<String, String> newFileContents = getFileContents(diffs, false);

        for (DiffEntry diff : diffs) {
            processDiffEntryForMethodMetrics(diff, commit, methodMap, oldFileContents, newFileContents);
        }
    }

    /**
     * Processa una singola voce di diff per aggiornare le metriche dei metodi.
     * Estrae i metodi dalla versione vecchia e nuova del file e, se un metodo è stato
     * aggiunto o modificato, invoca l'aggiornamento delle sue metriche
     */
    private void processDiffEntryForMethodMetrics(DiffEntry diff, RevCommit commit, Map<String, List<JavaMethod>> methodMap, Map<String, String> oldFileContents, Map<String, String> newFileContents) {
        String filePath = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
        if (!filePath.endsWith(javaExtension) || filePath.contains(directoryTest)) return;

        // Estrae i metodi da entrambe le versioni del file
        Map<String, MethodDeclaration> oldMethods = parseMethods(oldFileContents.getOrDefault(diff.getOldPath(), ""));
        Map<String, MethodDeclaration> newMethods = parseMethods(newFileContents.getOrDefault(diff.getNewPath(), ""));

        // Itera sui metodi della nuova versione del file
        for (Map.Entry<String, MethodDeclaration> newMethodEntry : newMethods.entrySet()) {
            String signature = newMethodEntry.getKey();
            MethodDeclaration newMd = newMethodEntry.getValue();
            MethodDeclaration oldMd = oldMethods.get(signature); // Cerca il metodo corrispondente nella vecchia versione

            // Calcola l'hash del corpo del metodo per un confronto robusto
            String newBodyHash = calculateBodyHash(newMd);
            String oldBodyHash = (oldMd != null) ? calculateBodyHash(oldMd) : null;

            // Se il metodo è nuovo (oldMd == null) o se il suo corpo è cambiato, allora è stato modificato da questo commit
            if (oldMd == null || !newBodyHash.equals(oldBodyHash)) {
                String fqn = filePath + "/" + signature;
                if (methodMap.containsKey(fqn)) {
                    // Prende tutte le istanze future di questo metodo e aggiorna le metriche
                    List<JavaMethod> methodsToUpdate = methodMap.get(fqn);
                    updateMethodMetricsForCommit(methodsToUpdate, commit, newMd, oldMd, newBodyHash);
                }
            }
        }
    }

    /**
     * Calcola le metriche finali (NAuth, AvgChurn) per tutti i metodi dopo che tutti i commit sono stati processati
     */
    private void calculateFinalMethodMetrics(List<JavaMethod> allMethods) {
        for (JavaMethod method : allMethods) {
            // Calcolo NAuth: numero di autori unici che hanno modificato il metodo
            if (!method.getCommits().isEmpty()) {
                Set<String> authors = method.getCommits().stream()
                        .map(c -> c.getAuthorIdent().getName())
                        .collect(Collectors.toSet());
                method.setNumAuthors(authors.size());
            } else {
                method.setNumAuthors(0);
            }

            // Calcolo AvgChurn: churn totale (righe aggiunte + rimosse) diviso per il numero di revisioni
            if (method.getNumRevisions() > 0) {
                double avgChurn = (double) (method.getTotalStmtAdded() + method.getTotalStmtDeleted()) / method.getNumRevisions();
                method.setAvgChurn(avgChurn);
            } else {
                method.setAvgChurn(0.0);
            }
        }
    }


    /**
     * Aggiorna le metriche di un metodo basate su un singolo commit.
     * Questa funzione viene chiamata per tutte le release FUTURE a partire da quella del commit
     */
    private void updateMethodMetricsForCommit(List<JavaMethod> methodsToUpdate, RevCommit commit, MethodDeclaration currentMdAst, MethodDeclaration oldMdAst, String newBodyHash) {
        Release releaseOfCommit = GitUtils.getReleaseOfCommit(commit, this.fullReleaseList);
        if (releaseOfCommit == null) return;

        for (JavaMethod projectMethod : methodsToUpdate) {
            if (projectMethod.getRelease().getId() < releaseOfCommit.getId()) {
                continue;
            }

            // Aggiorna le metriche di base
            projectMethod.addCommit(commit);
            projectMethod.incrementNumRevisions();
            projectMethod.setBodyHash(newBodyHash);

            // Calcola il churn di questo specifico commit (righe aggiunte/rimosse)
            int currentCommitStmtAdded = 0;
            int currentCommitStmtDeleted = 0;

            if (oldMdAst != null) {
                int locOld = MetricsCalculator.calculateLOC(oldMdAst);
                int locNew = MetricsCalculator.calculateLOC(currentMdAst);

                if (locNew > locOld) {
                    currentCommitStmtAdded = locNew - locOld;
                    projectMethod.addStmtAdded(currentCommitStmtAdded);
                } else if (locOld > locNew) {
                    currentCommitStmtDeleted = locOld - locNew;
                    projectMethod.addStmtDeleted(currentCommitStmtDeleted);
                }
            } else {
                // Se il metodo è nuovo, tutte le sue righe sono considerate "aggiunte"
                currentCommitStmtAdded = MetricsCalculator.calculateLOC(currentMdAst);
                projectMethod.addStmtAdded(currentCommitStmtAdded);
            }

            // Aggiorna il churn massimo se quello di questo commit è più alto
            int currentCommitChurn = currentCommitStmtAdded + currentCommitStmtDeleted;
            if (currentCommitChurn > projectMethod.getMaxChurn()) {
                projectMethod.setMaxChurn(currentCommitChurn);
            }
        }
    }

    /**
     * Calcola la metrica 'HasFixHistory' per ogni metodo.
     * Un metodo ha una "storia di fix" se è stato modificato da un commit di fix
     * in una release precedente a quella in cui il metodo è stato analizzato
     */
    public void calculateHasFixHistory(List<JavaMethod> allMethods) {
        // Crea una mappa per un accesso rapido: ID del commit -> Ticket associato
        Map<String, Ticket> commitNameToTicketMap = new HashMap<>();
        for (Ticket ticket : this.ticketList) {
            for (RevCommit commit : ticket.getCommitList()) {
                commitNameToTicketMap.put(commit.getName(), ticket);
            }
        }

        // Itera su ogni versione di ogni metodo
        for (JavaMethod method : allMethods) {
            Release currentMethodRelease = method.getRelease();
            // Controlla tutti i commit che hanno modificato questo metodo
            for (RevCommit commit : method.getCommits()) {
                // Se il commit è di fix e se la release del commit di fix
                // è antecedente a quella del metodo attuale, il metodo ha una storia di fix
                if (commitNameToTicketMap.containsKey(commit.getName())) {
                    Release commitRelease = GitUtils.getReleaseOfCommit(commit, this.fullReleaseList);
                    if (commitRelease != null && commitRelease.getId() < currentMethodRelease.getId()) {
                        method.setHasFixHistory(1);
                        break; // Trovato un fix, non serve continuare a cercare per questo metodo
                    }
                }
            }
        }
    }

    /**
     * Itera su tutti i ticket di bug ed etichetta i metodi appropriati come "buggy"
     */
    public void setMethodBuggyness(List<JavaMethod> allProjectMethods) {
        if (this.ticketList == null) {
            LOGGER.warning("Ticket list not initialized");
            return;
        }

        // Itera su ogni ticket considerato come bug
        for (Ticket ticket : this.ticketList) {
            Release injectedVersion = ticket.getIv();
            if (injectedVersion == null) continue;

            // Itera sui commit che hanno risolto questo bug
            for (RevCommit fixCommit : ticket.getCommitList()) {
                // Gestione del singolo commit di fix
                processSingleFixCommit(fixCommit, injectedVersion, allProjectMethods);
            }
        }
    }

    /**
     * Analizza un singolo commit di fix per trovare i metodi che ha modificato
     */
    private void processSingleFixCommit(RevCommit fixCommit, Release injectedVersion, List<JavaMethod> allProjectMethods) {
        Release fixedVersion = GitUtils.getReleaseOfCommit(fixCommit, this.fullReleaseList);
        if (fixedVersion == null) return;

        try {
            if (fixCommit.getParentCount() == 0) return;
            RevCommit parentOfFix = fixCommit.getParent(0);
            // Ottiene la lista dei file modificati nel commit di fix
            List<DiffEntry> diffs = getDiffEntries(parentOfFix, fixCommit);

            Map<String, String> newFileContentsInFix = getFileContents(diffs, false);
            Map<String, String> oldFileContentsInFix = getFileContents(diffs, true);

            for (DiffEntry diff : diffs) {
                // Analisi dei file modificati
                processDiffForBuggyness(diff, newFileContentsInFix, oldFileContentsInFix, injectedVersion, fixedVersion, allProjectMethods);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Error parsing fix commit " + fixCommit.getName());
        }
    }

    /**
     * Analizza un singolo file modificato da un commit di fix.
     * Identifica quali metodi all'interno del file sono stati aggiunti o cambiati
     * e li passa al metodo di etichettatura finale
     */
    private void processDiffForBuggyness(DiffEntry diff, Map<String, String> newFileContents, Map<String, String> oldFileContents,
                                         Release injectedVersion, Release fixedVersion, List<JavaMethod> allProjectMethods) {
        String filePath = diff.getNewPath();
        if (!filePath.endsWith(javaExtension) || filePath.contains(directoryTest)) return;

        String newContent = newFileContents.getOrDefault(filePath, "");
        Map<String, MethodDeclaration> newMethodsInFix = parseMethods(newContent);

        String oldContent = oldFileContents.getOrDefault(diff.getOldPath(), "");
        Map<String, MethodDeclaration> oldMethodsInFix = parseMethods(oldContent);

        for (Map.Entry<String, MethodDeclaration> newMethodEntry : newMethodsInFix.entrySet()) {
            String signature = newMethodEntry.getKey();
            MethodDeclaration newMd = newMethodEntry.getValue();
            MethodDeclaration oldMd = oldMethodsInFix.get(signature);

            // Confronta l'hash del corpo per determinare se il metodo è cambiato
            String newHash = calculateBodyHash(newMd);
            String oldHash = calculateBodyHash(oldMd);

            // Se il metodo è nuovo o il corpo è cambiato rispetto alla versione precedente, allora è stato toccato dal fix
            if (oldMd == null || !newHash.equals(oldHash)) {
                String fqn = filePath + "/" + signature;
                labelBuggyMethods(fqn, injectedVersion, fixedVersion, allProjectMethods);
            }
        }
    }

    /**
     * Metodo ausiliario per etichettare le istanze di un metodo come buggy
     */
    private void labelBuggyMethods(String fixedMethodFQN, Release injectedVersion, Release fixedVersion, List<JavaMethod> allProjectMethods) {
        for (JavaMethod projectMethod : allProjectMethods) {
            // Controlla se la release del metodo rientra nell'intervallo [IV, FV)
            if (projectMethod.getFullyQualifiedName().equals(fixedMethodFQN) &&
                    projectMethod.getRelease().getId() >= injectedVersion.getId() &&
                    projectMethod.getRelease().getId() < fixedVersion.getId()) {
                projectMethod.setBuggy(true);
            }
        }
    }


    /**
     * Calcola un hash SHA-256 del corpo di un metodo dopo averlo normalizzato
     */
    private String calculateBodyHash(MethodDeclaration md) {
        if (md == null) return "NULL_METHOD_HASH";
        // "Pulisce" il corpo del metodo prima di calcolare l'hash
        String normalizedBody = normalizeMethodBody(md);
        if (normalizedBody.isEmpty()) return "EMPTY_BODY_HASH";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(normalizedBody.getBytes(StandardCharsets.UTF_8));
            // Converte il risultato binario dell'hash in una stringa leggibile
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 Hashing error", e);
        }
    }

    /**
     * Normalizza il corpo di un metodo rimuovendo commenti e spazi bianchi multipli
     */
    private String normalizeMethodBody(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return ""; // Restituisce stringa vuota se il metodo non ha un corpo
        String body = md.getBody().get().toString();
        body = body.replaceAll("//.*|/\\*(?s).*?\\*/", "");
        body = body.replaceAll("\\s+", " "); // Sostituisci spazi multipli con uno singolo
        return body.trim(); // Rimuove spazi iniziali e finali
    }


    /**
     * Metodo di utilità per convertire un array di byte in una stringa esadecimale
     */
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

    /**
     * Pulisce la lista completa delle release, rimuovendo quelle che non hanno commit associati.
     * Successivamente, ri-numera gli ID delle release rimanenti in modo che siano sequenziali.
     * Questo garantisce coerenza nei dati utilizzati per l'analisi.
     */
    private void filterAndRenumberReleases() {
        this.fullReleaseList.removeIf(release -> release.getCommitList().isEmpty());
        int idCounter = 1;
        for (Release r : this.fullReleaseList) {
            r.setId(idCounter++);
        }
    }

    /**
     * Wrapper per la libreria JGit. Calcola le differenze (diff) tra un commit e il suo genitore
     */
    private List<DiffEntry> getDiffEntries(RevCommit parent, RevCommit commit) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setContext(0);
            return diffFormatter.scan(parent.getTree(), commit.getTree());
        }
    }

    /**
     * Recupera il contenuto testuale dei file specificati da una lista di `DiffEntry`
     */
    private Map<String, String> getFileContents(List<DiffEntry> diffs, boolean useOldPath) throws IOException {
        Map<String, String> contents = new HashMap<>();
        try (ObjectReader reader = repository.newObjectReader()) {
            for (DiffEntry diff : diffs) {
                String path = useOldPath ? diff.getOldPath() : diff.getNewPath();
                ObjectId id = useOldPath ? diff.getOldId().toObjectId() : diff.getNewId().toObjectId();
                if (DiffEntry.DEV_NULL.equals(path)) continue;

                try {
                    ObjectLoader loader = reader.open(id);
                    contents.put(path, new String(loader.getBytes(), StandardCharsets.UTF_8));
                } catch (org.eclipse.jgit.errors.MissingObjectException e) {
                    LOGGER.log(Level.WARNING, "Missing object: {0} for path {1} {2}", new Object[]{id, path, e});
                }
            }
        }
        return contents;
    }

    /**
     * Esegue il parsing di una stringa contenente codice Java e restituisce una mappa
     * di tutti i metodi trovati, indicizzati per la loro firma
     */
    private Map<String, MethodDeclaration> parseMethods(String content) {
        Map<String, MethodDeclaration> methods = new HashMap<>();
        if (content == null || content.isEmpty()) return methods;
        try {
            StaticJavaParser.parse(content).findAll(MethodDeclaration.class)
                    .forEach(md -> methods.put(JavaMethod.getSignature(md), md));
        } catch (Exception e) {
            // Ignora errori di parsing
        }
        return methods;
    }
}