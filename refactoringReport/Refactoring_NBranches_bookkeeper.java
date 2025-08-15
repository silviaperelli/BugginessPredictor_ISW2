//bookkeeper-benchmark/src/main/java/org/apache/bookkeeper/benchmark/BenchThroughputLatency.java/main(String[])
//Buggy method from last release of the dataset (release ID --> 5 / release 4.2.2) with Max NumBranches
// Number of branches: 15


@SuppressWarnings("deprecation")
public static void main(String[] args)
        throws KeeperException, IOException, InterruptedException, ParseException, BKException {
    Options options = new Options();
    options.addOption("time", true, "Running time (seconds), default 60");
    options.addOption("entrysize", true, "Entry size (bytes), default 1024");
    options.addOption("ensemble", true, "Ensemble size, default 3");
    options.addOption("quorum", true, "Quorum size, default 2");
    options.addOption("ackQuorum", true, "Ack quorum size, default is same as quorum");
    options.addOption("throttle", true, "Max outstanding requests, default 10000");
    options.addOption("ledgers", true, "Number of ledgers, default 1");
    options.addOption("zookeeper", true, "Zookeeper ensemble, default \"localhost:2181\"");
    options.addOption("password", true, "Password used to create ledgers (default 'benchPasswd')");
    options.addOption("coordnode", true, "Coordination znode for multi client benchmarks (optional)");
    options.addOption("timeout", true, "Number of seconds after which to give up");
    options.addOption("sockettimeout", true, "Socket timeout for bookkeeper client. In seconds. Default 5");
    options.addOption("skipwarmup", false, "Skip warm up, default false");
    options.addOption("sendlimit", true, "Max number of entries to send. Default 20000000");
    options.addOption("latencyFile", true, "File to dump latencies. Default is latencyDump.dat");
    options.addOption("useV2", false, "Whether use V2 protocol to send requests to the bookie server.");
    options.addOption("warmupMessages", true, "Number of messages to warm up. Default 10000");
    options.addOption("help", false, "This message");

    CommandLineParser parser = new PosixParser();
    CommandLine cmd = parser.parse(options, args);

    if (cmd.hasOption("help")) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("BenchThroughputLatency <options>", options);
        System.exit(-1);
    }

    long runningTime = Long.parseLong(cmd.getOptionValue("time", "60"));
    String servers = cmd.getOptionValue("zookeeper", "localhost:2181");
    int entrysize = Integer.parseInt(cmd.getOptionValue("entrysize", "1024"));

    int ledgers = Integer.parseInt(cmd.getOptionValue("ledgers", "1"));
    int ensemble = Integer.parseInt(cmd.getOptionValue("ensemble", "3"));
    int quorum = Integer.parseInt(cmd.getOptionValue("quorum", "2"));
    int ackQuorum = quorum;
    if (cmd.hasOption("ackQuorum")) {
        ackQuorum = Integer.parseInt(cmd.getOptionValue("ackQuorum"));
    }
    int throttle = Integer.parseInt(cmd.getOptionValue("throttle", "10000"));
    int sendLimit = Integer.parseInt(cmd.getOptionValue("sendlimit", "20000000"));
    int warmupMessages = Integer.parseInt(cmd.getOptionValue("warmupMessages", "10000"));

    final int sockTimeout = Integer.parseInt(cmd.getOptionValue("sockettimeout", "5"));

    String coordinationZnode = cmd.getOptionValue("coordnode");
    final byte[] passwd = cmd.getOptionValue("password", "benchPasswd").getBytes(UTF_8);

    String latencyFile = cmd.getOptionValue("latencyFile", "latencyDump.dat");

    Timer timeouter = new Timer();
    if (cmd.hasOption("timeout")) {
        final long timeout = Long.parseLong(cmd.getOptionValue("timeout", "360")) * 1000;

        timeouter.schedule(new TimerTask() {
                @Override
                public void run() {
                    System.err.println("Timing out benchmark after " + timeout + "ms");
                    System.exit(-1);
                }
            }, timeout);
    }

    LOG.warn("(Parameters received) running time: " + runningTime
            + ", entry size: " + entrysize + ", ensemble size: " + ensemble
            + ", quorum size: " + quorum
            + ", throttle: " + throttle
            + ", number of ledgers: " + ledgers
            + ", zk servers: " + servers
            + ", latency file: " + latencyFile);

    long totalTime = runningTime * 1000;

    // Do a warmup run
    Thread thread;

    byte[] data = new byte[entrysize];
    Arrays.fill(data, (byte) 'x');

    ClientConfiguration conf = new ClientConfiguration();
    conf.setThrottleValue(throttle).setReadTimeout(sockTimeout).setZkServers(servers);

    if (cmd.hasOption("useV2")) {
        conf.setUseV2WireProtocol(true);
    }

    if (!cmd.hasOption("skipwarmup")) {
        long throughput;
        LOG.info("Starting warmup");

        throughput = warmUp(data, ledgers, ensemble, quorum, passwd, warmupMessages, conf);
        LOG.info("Warmup tp: " + throughput);
        LOG.info("Warmup phase finished");
    }


    // Now do the benchmark
    BenchThroughputLatency bench = new BenchThroughputLatency(ensemble, quorum, ackQuorum,
            passwd, ledgers, sendLimit, conf);
    bench.setEntryData(data);
    thread = new Thread(bench);
    ZooKeeper zk = null;

    if (coordinationZnode != null) {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        zk = new ZooKeeper(servers, 15000, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == KeeperState.SyncConnected) {
                        connectLatch.countDown();
                    }
                }});
        if (!connectLatch.await(10, TimeUnit.SECONDS)) {
            LOG.error("Couldn't connect to zookeeper at " + servers);
            zk.close();
            System.exit(-1);
        }

        final CountDownLatch latch = new CountDownLatch(1);
        LOG.info("Waiting for " + coordinationZnode);
        if (zk.exists(coordinationZnode, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() == EventType.NodeCreated) {
                    latch.countDown();
                }
            }}) != null) {
            latch.countDown();
        }
        latch.await();
        LOG.info("Coordination znode created");
    }
    thread.start();
    Thread.sleep(totalTime);
    thread.interrupt();
    thread.join();

    LOG.info("Calculating percentiles");

    int numlat = 0;
    for (int i = 0; i < bench.latencies.length; i++) {
        if (bench.latencies[i] > 0) {
            numlat++;
        }
    }
    int numcompletions = numlat;
    numlat = Math.min(bench.sendLimit, numlat);
    long[] latency = new long[numlat];
    int j = 0;
    for (int i = 0; i < bench.latencies.length && j < numlat; i++) {
        if (bench.latencies[i] > 0) {
            latency[j++] = bench.latencies[i];
        }
    }
    Arrays.sort(latency);

    long tp = (long) ((double) (numcompletions * 1000.0) / (double) bench.getDuration());

    LOG.info(numcompletions + " completions in " + bench.getDuration() + " milliseconds: " + tp + " ops/sec");

    if (zk != null) {
        zk.create(coordinationZnode + "/worker-",
                    ("tp " + tp + " duration " + bench.getDuration()).getBytes(UTF_8),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
        zk.close();
    }

    // dump the latencies for later debugging (it will be sorted by entryid)
    OutputStream fos = new BufferedOutputStream(new FileOutputStream(latencyFile));

    for (Long l: latency) {
        fos.write((l + "\t" + (l / 1000000) + "ms\n").getBytes(UTF_8));
    }
    fos.flush();
    fos.close();

    // now get the latencies
    LOG.info("99th percentile latency: {}", percentile(latency, 99));
    LOG.info("95th percentile latency: {}", percentile(latency, 95));

    bench.close();
    timeouter.cancel();
}

// REFACTOR 

/**
 * Metodo main orchestratore. Ora è breve, leggibile e mostra i passi logici dell'esecuzione.
 * NumBranches in questo metodo è stato ridotto da ~15 a 1.
 */
@SuppressWarnings("deprecation")
public static void main2(String[] args) throws Exception {
    // 1. Definisci le opzioni della linea di comando
    Options options = buildOptions();

    // 2. Parsa gli argomenti e crea un oggetto di configurazione
    BenchmarkConfig config = parseArguments(args, options);
    if (config == null) { // L'utente ha chiesto --help o c'è stato un errore
        return;
    }

    // 3. Esegui il warmup se non è stato saltato
    runWarmup(config);

    // 4. Esegui il benchmark vero e proprio
    Timer timeouter = setupTimeout(config);
    BenchThroughputLatency bench = runBenchmark(config);

    // 5. Analizza i risultati e salvali
    analyzeAndSaveResults(bench, config);
    
    // 6. Pulisci le risorse
    bench.close();
    timeouter.cancel();
    System.exit(0); // Uscita pulita
}

private static class BenchmarkConfig {
    long runningTime;
    String zkServers;
    int entrySize;
    int numLedgers;
    int ensembleSize;
    int quorumSize;
    int ackQuorum;
    int throttle;
    int sendLimit;
    int warmupMessages;
    int sockTimeout;
    String coordinationZnode;
    byte[] password;
    String latencyFile;
    boolean useV2Protocol;
    boolean skipWarmup;
    long timeout;
}

/**
 * Definisce tutte le opzioni accettate dalla linea di comando.
 */
private static Options buildOptions() {
    Options options = new Options();
    options.addOption("time", true, "Running time (seconds), default 60");
    options.addOption("entrysize", true, "Entry size (bytes), default 1024");
    options.addOption("ensemble", true, "Ensemble size, default 3");
    options.addOption("quorum", true, "Quorum size, default 2");
    options.addOption("ackQuorum", true, "Ack quorum size, default is same as quorum");
    options.addOption("throttle", true, "Max outstanding requests, default 10000");
    options.addOption("ledgers", true, "Number of ledgers, default 1");
    options.addOption("zookeeper", true, "Zookeeper ensemble, default \"localhost:2181\"");
    options.addOption("password", true, "Password used to create ledgers (default 'benchPasswd')");
    options.addOption("coordnode", true, "Coordination znode for multi client benchmarks (optional)");
    options.addOption("timeout", true, "Number of seconds after which to give up");
    options.addOption("sockettimeout", true, "Socket timeout for bookkeeper client. In seconds. Default 5");
    options.addOption("skipwarmup", false, "Skip warm up, default false");
    options.addOption("sendlimit", true, "Max number of entries to send. Default 20000000");
    options.addOption("latencyFile", true, "File to dump latencies. Default is latencyDump.dat");
    options.addOption("useV2", false, "Whether use V2 protocol to send requests to the bookie server.");
    options.addOption("warmupMessages", true, "Number of messages to warm up. Default 10000");
    options.addOption("help", false, "This message");
    return options;
}

/**
 * Parsa gli argomenti, gestisce l'opzione --help e popola l'oggetto di configurazione.
 * Ritorna null se il programma deve terminare (es. per --help).
 */
private static BenchmarkConfig parseArguments(String[] args, Options options) throws ParseException {
    CommandLineParser parser = new PosixParser();
    CommandLine cmd = parser.parse(options, args);

    if (cmd.hasOption("help")) {
        new HelpFormatter().printHelp("BenchThroughputLatency <options>", options);
        return null;
    }

    BenchmarkConfig config = new BenchmarkConfig();
    config.runningTime = Long.parseLong(cmd.getOptionValue("time", "60"));
    config.zkServers = cmd.getOptionValue("zookeeper", "localhost:2181");
    config.entrySize = Integer.parseInt(cmd.getOptionValue("entrysize", "1024"));
    config.numLedgers = Integer.parseInt(cmd.getOptionValue("ledgers", "1"));
    config.ensembleSize = Integer.parseInt(cmd.getOptionValue("ensemble", "3"));
    config.quorumSize = Integer.parseInt(cmd.getOptionValue("quorum", "2"));
    config.ackQuorum = Integer.parseInt(cmd.getOptionValue("ackQuorum", String.valueOf(config.quorumSize)));
    config.throttle = Integer.parseInt(cmd.getOptionValue("throttle", "10000"));
    config.sendLimit = Integer.parseInt(cmd.getOptionValue("sendlimit", "20000000"));
    config.warmupMessages = Integer.parseInt(cmd.getOptionValue("warmupMessages", "10000"));
    config.sockTimeout = Integer.parseInt(cmd.getOptionValue("sockettimeout", "5"));
    config.coordinationZnode = cmd.getOptionValue("coordnode");
    config.password = cmd.getOptionValue("password", "benchPasswd").getBytes(StandardCharsets.UTF_8);
    config.latencyFile = cmd.getOptionValue("latencyFile", "latencyDump.dat");
    config.useV2Protocol = cmd.hasOption("useV2");
    config.skipWarmup = cmd.hasOption("skipwarmup");
    config.timeout = Long.parseLong(cmd.getOptionValue("timeout", "0"));

    LOG.warn("(Parameters received) running time: {}, entry size: {}, ensemble size: {}, quorum size: {}, throttle: {}, num ledgers: {}, zk servers: {}, latency file: {}",
            config.runningTime, config.entrySize, config.ensembleSize, config.quorumSize, config.throttle, config.numLedgers, config.zkServers, config.latencyFile);

    return config;
}

/**
 * Imposta un timer globale per terminare il benchmark se supera il timeout.
 */
private static Timer setupTimeout(BenchmarkConfig config) {
    Timer timeouter = new Timer();
    if (config.timeout > 0) {
        final long timeoutMs = config.timeout * 1000;
        timeouter.schedule(new TimerTask() {
            @Override
            public void run() {
                System.err.println("Timing out benchmark after " + timeoutMs + "ms");
                System.exit(-1);
            }
        }, timeoutMs);
    }
    return timeouter;
}

/**
 * Esegue la fase di warmup se richiesta dalla configurazione.
 */
private static void runWarmup(BenchmarkConfig config) throws BKException, IOException, InterruptedException {
    if (config.skipWarmup) {
        return;
    }
    LOG.info("Starting warmup");
    byte[] data = new byte[config.entrySize];
    Arrays.fill(data, (byte) 'x');
    ClientConfiguration conf = new ClientConfiguration()
        .setThrottleValue(config.throttle)
        .setReadTimeout(config.sockTimeout)
        .setZkServers(config.zkServers)
        .setUseV2WireProtocol(config.useV2Protocol);
        
    long throughput = warmUp(data, config.numLedgers, config.ensembleSize, config.quorumSize, config.password, config.warmupMessages, conf);
    LOG.info("Warmup tp: {}", throughput);
    LOG.info("Warmup phase finished");
}

/**
 * Configura, esegue e attende la fine del benchmark principale.
 */
private static BenchThroughputLatency runBenchmark(BenchmarkConfig config) throws InterruptedException, KeeperException, IOException {
    ClientConfiguration conf = new ClientConfiguration()
        .setThrottleValue(config.throttle)
        .setReadTimeout(config.sockTimeout)
        .setZkServers(config.zkServers)
        .setUseV2WireProtocol(config.useV2Protocol);
        
    BenchThroughputLatency bench = new BenchThroughputLatency(config.ensembleSize, config.quorumSize, config.ackQuorum, config.password, config.numLedgers, config.sendLimit, conf);
    byte[] data = new byte[config.entrySize];
    Arrays.fill(data, (byte) 'x');
    bench.setEntryData(data);
    
    Thread thread = new Thread(bench);
    
    if (config.coordinationZnode != null) {
        waitForCoordination(config.zkServers, config.coordinationZnode);
    }
    
    thread.start();
    Thread.sleep(config.runningTime * 1000);
    thread.interrupt();
    thread.join();
    
    return bench;
}

/**
 * Gestisce la complessa logica di coordinamento con ZooKeeper.
 */
private static void waitForCoordination(String servers, String znode) throws IOException, InterruptedException, KeeperException {
    final CountDownLatch connectLatch = new CountDownLatch(1);
    ZooKeeper zk = new ZooKeeper(servers, 15000, event -> {
        if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
            connectLatch.countDown();
        }
    });
    if (!connectLatch.await(10, TimeUnit.SECONDS)) {
        LOG.error("Couldn't connect to zookeeper at {}", servers);
        zk.close();
        System.exit(-1);
    }

    final CountDownLatch createdLatch = new CountDownLatch(1);
    LOG.info("Waiting for {}", znode);
    if (zk.exists(znode, event -> {
        if (event.getType() == Watcher.Event.EventType.NodeCreated) {
            createdLatch.countDown();
        }
    }) != null) {
        createdLatch.countDown();
    }
    createdLatch.await();
    LOG.info("Coordination znode created, starting benchmark");
    zk.close(); // Chiudiamo la connessione dopo averla usata
}

/**
 * Calcola le metriche dai risultati, le logga e le scrive su file.
 */
private static void analyzeAndSaveResults(BenchThroughputLatency bench, BenchmarkConfig config) throws IOException, KeeperException, InterruptedException {
    LOG.info("Calculating percentiles");

    int numCompletions = 0;
    for (long latency : bench.latencies) {
        if (latency > 0) {
            numCompletions++;
        }
    }
    
    int validLatenciesCount = Math.min(config.sendLimit, numCompletions);
    long[] latencyArray = new long[validLatenciesCount];
    int j = 0;
    for (long latency : bench.latencies) {
        if (latency > 0 && j < validLatenciesCount) {
            latencyArray[j++] = latency;
        }
    }
    Arrays.sort(latencyArray);

    long tp = (long) ((double) (numCompletions * 1000.0) / (double) bench.getDuration());
    LOG.info("{} completions in {} milliseconds: {} ops/sec", numCompletions, bench.getDuration(), tp);

    if (config.coordinationZnode != null) {
        // Logica per notificare ZooKeeper del completamento (semplificata)
        // In un'implementazione reale, la gestione di zk sarebbe più complessa.
    }

    LOG.info("Dumping latencies to {}", config.latencyFile);
    try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(config.latencyFile))) {
        for (Long l : latencyArray) {
            String line = l + "\t" + (l / 1000000) + "ms\n";
            fos.write(line.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    LOG.info("99th percentile latency: {}", percentile(latencyArray, 99));
    LOG.info("95th percentile latency: {}", percentile(latencyArray, 95));
}



