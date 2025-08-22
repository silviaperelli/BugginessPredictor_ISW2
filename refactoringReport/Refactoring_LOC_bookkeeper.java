//bookkeeper-benchmark/src/main/java/org/apache/bookkeeper/benchmark/BenchThroughputLatency.java/main(String[])
//Buggy method from last release of the dataset (release ID --> 5 / release 4.2.2) with Max LOC
// LOC: 117


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
    options.addOption("help", false, "This message");

    CommandLineParser parser = new PosixParser();
    CommandLine cmd = parser.parse(options, args);

    if (cmd.hasOption("help")) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("BenchThroughputLatency <options>", options);
        System.exit(-1);
    }

    long runningTime = Long.valueOf(cmd.getOptionValue("time", "60"));
    String servers = cmd.getOptionValue("zookeeper", "localhost:2181");
    int entrysize = Integer.valueOf(cmd.getOptionValue("entrysize", "1024"));

    int ledgers = Integer.valueOf(cmd.getOptionValue("ledgers", "1"));
    int ensemble = Integer.valueOf(cmd.getOptionValue("ensemble", "3"));
    int quorum = Integer.valueOf(cmd.getOptionValue("quorum", "2"));
    int ackQuorum = quorum;
    if (cmd.hasOption("ackQuorum")) {
        ackQuorum = Integer.valueOf(cmd.getOptionValue("ackQuorum"));
    }
    int throttle = Integer.valueOf(cmd.getOptionValue("throttle", "10000"));
    int sendLimit = Integer.valueOf(cmd.getOptionValue("sendlimit", "20000000"));

    final int sockTimeout = Integer.valueOf(cmd.getOptionValue("sockettimeout", "5"));

    String coordinationZnode = cmd.getOptionValue("coordnode");
    final byte[] passwd = cmd.getOptionValue("password", "benchPasswd").getBytes();

    String latencyFile = cmd.getOptionValue("latencyFile", "latencyDump.dat");

    Timer timeouter = new Timer();
    if (cmd.hasOption("timeout")) {
        final long timeout = Long.valueOf(cmd.getOptionValue("timeout", "360")) * 1000;

        timeouter.schedule(new TimerTask() {
            public void run() {
                System.err.println("Timing out benchmark after " + timeout + "ms");
                System.exit(-1);
            }
        }, timeout);
    }

    LOG.warn("(Parameters received) running time: " + runningTime +
            ", entry size: " + entrysize + ", ensemble size: " + ensemble +
            ", quorum size: " + quorum +
            ", throttle: " + throttle +
            ", number of ledgers: " + ledgers +
            ", zk servers: " + servers +
            ", latency file: " + latencyFile);

    long totalTime = runningTime*1000;

    // Do a warmup run
    Thread thread;

    byte data[] = new byte[entrysize];
    Arrays.fill(data, (byte)'x');

    ClientConfiguration conf = new ClientConfiguration();
    conf.setThrottleValue(throttle).setReadTimeout(sockTimeout).setZkServers(servers);

    if (!cmd.hasOption("skipwarmup")) {
        long throughput;
        LOG.info("Starting warmup");

        throughput = warmUp(data, ledgers, ensemble, quorum, passwd, conf);
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
    for(int i = 0; i < bench.latencies.length; i++) {
        if (bench.latencies[i] > 0) {
            numlat++;
        }
    }
    int numcompletions = numlat;
    numlat = Math.min(bench.sendLimit, numlat);
    long[] latency = new long[numlat];
    int j =0;
    for(int i = 0; i < bench.latencies.length && j < numlat; i++) {
        if (bench.latencies[i] > 0) {
            latency[j++] = bench.latencies[i];
        }
    }
    Arrays.sort(latency);

    long tp = (long)((double)(numcompletions*1000.0)/(double)bench.getDuration());

    LOG.info(numcompletions + " completions in " + bench.getDuration() + " seconds: " + tp + " ops/sec");

    if (zk != null) {
        zk.create(coordinationZnode + "/worker-",
                ("tp " + tp + " duration " + bench.getDuration()).getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
        zk.close();
    }

    // dump the latencies for later debugging (it will be sorted by entryid)
    OutputStream fos = new BufferedOutputStream(new FileOutputStream(latencyFile));

    for(Long l: latency) {
        fos.write((Long.toString(l)+"\t"+(l/1000000)+ "ms\n").getBytes());
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
public static void main2(String[] args)
        throws KeeperException, IOException, InterruptedException, ParseException, BKException {

    // Fase 1: Parsing degli argomenti della riga di comando
    CommandLine cmd = parseCommandLineArguments(args);

    // Fase 2: Caricamento della configurazione dagli argomenti
    BenchmarkConfig config = loadConfigurationFrom(cmd);
    LOG.warn("(Parameters received) " + config.toString());

    // Fase 3: Setup del benchmark, incluso il timeout
    Timer timeouter = setupTimeout(config);
    ClientConfiguration clientConf = buildClientConfiguration(config);

    // Fase 4: Esecuzione del benchmark (con warmup)
    BenchThroughputLatency bench = executeBenchmark(config, clientConf);

    // Fase 5: Elaborazione e salvataggio dei risultati
    processAndReportResults(bench, config);

    // Fase 6: Pulizia delle risorse
    bench.close();
    timeouter.cancel();
}

private static CommandLine parseCommandLineArguments(String[] args) throws ParseException {
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
    options.addOption("help", false, "This message");

    CommandLineParser parser = new PosixParser();
    CommandLine cmd = parser.parse(options, args);

    if (cmd.hasOption("help")) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("BenchThroughputLatency <options>", options);
        System.exit(-1);
    }
    return cmd;
}

/**
 * Estrae i parametri dal CommandLine e popola un oggetto di configurazione.
 */
private static BenchmarkConfig loadConfigurationFrom(CommandLine cmd) {
    return new BenchmarkConfig(cmd);
}

/**
 * Imposta un timer globale per terminare il benchmark se supera il timeout specificato.
 */
private static Timer setupTimeout(BenchmarkConfig config) {
    Timer timeouter = new Timer();
    if (config.timeout > 0) {
        timeouter.schedule(new TimerTask() {
            public void run() {
                System.err.println("Timing out benchmark after " + config.timeout + "ms");
                System.exit(-1);
            }
        }, config.timeout);
    }
    return timeouter;
}

/**
 * Crea la configurazione del client BookKeeper basata sui parametri.
 */
private static ClientConfiguration buildClientConfiguration(BenchmarkConfig config) {
    ClientConfiguration conf = new ClientConfiguration();
    conf.setThrottleValue(config.throttle)
            .setReadTimeout(config.sockTimeout)
            .setZkServers(config.servers);
    return conf;
}

/**
 * Orchestra l'intera esecuzione del benchmark, includendo il warmup,
 * la sincronizzazione con ZooKeeper e l'esecuzione del thread principale.
 */
private static BenchThroughputLatency executeBenchmark(BenchmarkConfig config, ClientConfiguration clientConf)
        throws InterruptedException, KeeperException, IOException, BKException {

    byte[] data = new byte[config.entrySize];
    Arrays.fill(data, (byte) 'x');

    if (!config.skipWarmup) {
        LOG.info("Starting warmup");
        long throughput = warmUp(data, config.ledgers, config.ensemble, config.quorum, config.passwd, clientConf);
        LOG.info("Warmup tp: " + throughput);
        LOG.info("Warmup phase finished");
    }

    BenchThroughputLatency bench = new BenchThroughputLatency(config.ensemble, config.quorum, config.ackQuorum,
            config.passwd, config.ledgers, config.sendLimit, clientConf);
    bench.setEntryData(data);
    Thread thread = new Thread(bench);

    ZooKeeper zk = synchronizeWithZooKeeper(config);

    thread.start();
    Thread.sleep(config.totalTime);
    thread.interrupt();
    thread.join();

    if (zk != null) {
        publishResultsToZooKeeper(zk, bench, config);
        zk.close();
    }
    return bench;
}

/**
 * Gestisce la logica di connessione e attesa su un nodo di coordinazione ZooKeeper.
 */
private static ZooKeeper synchronizeWithZooKeeper(BenchmarkConfig config)
        throws IOException, InterruptedException, KeeperException {
    if (config.coordinationZnode == null) {
        return null;
    }

    final CountDownLatch connectLatch = new CountDownLatch(1);
    ZooKeeper zk = new ZooKeeper(config.servers, 15000, event -> {
        if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
            connectLatch.countDown();
        }
    });

    if (!connectLatch.await(10, TimeUnit.SECONDS)) {
        LOG.error("Couldn't connect to zookeeper at " + config.servers);
        zk.close();
        System.exit(-1);
    }

    final CountDownLatch startLatch = new CountDownLatch(1);
    LOG.info("Waiting for " + config.coordinationZnode);
    if (zk.exists(config.coordinationZnode, event -> {
        if (event.getType() == Watcher.Event.EventType.NodeCreated) {
            startLatch.countDown();
        }
    }) != null) {
        startLatch.countDown();
    }
    startLatch.await();
    LOG.info("Coordination znode created, starting benchmark.");
    return zk;
}

/**
 * Calcola le metriche finali (throughput, percentile), le stampa a log
 * e scrive il file con i dati di latenza.
 */
private static void processAndReportResults(BenchThroughputLatency bench, BenchmarkConfig config) throws IOException {
    LOG.info("Calculating percentiles");

    long[] latency = getSortedLatencies(bench);
    long numCompletions = latency.length;

    if (bench.getDuration() > 0) {
        long tp = (long) ((double) (numCompletions * 1000.0) / (double) bench.getDuration());
        LOG.info(numCompletions + " completions in " + bench.getDuration() + " seconds: " + tp + " ops/sec");
    } else {
        LOG.info(numCompletions + " completions in " + bench.getDuration() + " seconds");
    }

    dumpLatenciesToFile(latency, config.latencyFile);

    LOG.info("99th percentile latency: {}", percentile(latency, 99));
    LOG.info("95th percentile latency: {}", percentile(latency, 95));
}

/**
 * Estrae, filtra e ordina i valori di latenza validi dall'oggetto benchmark.
 */
private static long[] getSortedLatencies(BenchThroughputLatency bench) {
    int validLatencyCount = 0;
    for (long l : bench.latencies) {
        if (l > 0) {
            validLatencyCount++;
        }
    }
    int finalCount = Math.min(bench.sendLimit, validLatencyCount);
    long[] latency = new long[finalCount];
    int j = 0;
    for (long l : bench.latencies) {
        if (j >= finalCount) break;
        if (l > 0) {
            latency[j++] = l;
        }
    }
    Arrays.sort(latency);
    return latency;
}

/**
 * Scrive i risultati di latenza su un file di output.
 */
private static void dumpLatenciesToFile(long[] latencies, String filePath) throws IOException {
    try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(filePath))) {
        for (Long l : latencies) {
            fos.write((Long.toString(l) + "\t" + (l / 1000000) + "ms\n").getBytes());
        }
    }
}


private static void publishResultsToZooKeeper(ZooKeeper zk, BenchThroughputLatency bench, BenchmarkConfig config)
        throws KeeperException, InterruptedException {
    long numCompletions = getSortedLatencies(bench).length;
    long tp = (long) ((double) (numCompletions * 1000.0) / (double) bench.getDuration());

    zk.create(config.coordinationZnode + "/worker-",
            ("tp " + tp + " duration " + bench.getDuration()).getBytes(),
            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
}