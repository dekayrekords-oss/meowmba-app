import com.fruitfly.brain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Request-driven, bounded neural worker. Its only interface is its SSH stdin/stdout. */
public final class MaleCnsWorker {
    private static final String HASH = "e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3";
    // SHA-256 of the sorted body IDs as little-endian uint64 values, from the reviewed retained asset.
    private static final String ROSTER_HASH = "a21dcf2576c352eda0f4d455683b648ec28272fd7f4918fdfbf2c97aff9df465";
    private static final String[] POPS = {"MN9", "DNp01", "TTMn", "DNg62", "DNge078", "prefix:DNg02", "DNa02/L", "DNa02/R",
        "DNp09", "MDN", "DNg60", "DNp07,DNp10", "DNp11,DNp02,DNp04"};
    private static final String[] KEYS = {"feed_hz", "escape_hz", "jump_hz", "groom_hz", "groom2_hz", "wing_hz", "left_hz", "right_hz",
        "forward_hz", "backward_hz", "halt_hz", "landing_hz", "takeoff_hz"};
    private final Profile retained, full;
    private Profile current;
    private Connectome c;
    private PopulationIndex pi;
    private LifNetwork net;
    private int[][] populations;
    private String session = "", lastLine = "", lastResponse = "";
    private long seq = 0;

    /** Trusted local profile metadata; the phone can select a name, never an asset or manifest path. */
    private record Profile(String name, Path asset, String hash, long size, int neurons, int pairs,
                           long synapses, int minPairSynapses) {
        static Profile retained(Path asset) {
            return new Profile("retained", asset, HASH, 22964094, 176422, 6287749, 90296905, 5);
        }

        static Profile full(Path asset, Path manifest) throws Exception {
            if (Files.size(manifest) > 4096) throw new IOException("Oversized graph manifest");
            var values = new HashMap<String, String>();
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] pair = line.split("=", -1);
                if (pair.length != 2 || values.putIfAbsent(pair[0], pair[1]) != null)
                    throw new IOException("Invalid graph manifest entry");
            }
            var keys = Set.of("schema", "graph", "dataset", "asset_sha256", "asset_size", "neurons", "pairs",
                "synapses", "min_pair_synapses", "autapses_removed", "roster_sha256");
            if (!values.keySet().equals(keys) || !"1".equals(values.get("schema")) || !"full".equals(values.get("graph")) ||
                !"male-cns:v1.0".equals(values.get("dataset")) || !"1".equals(values.get("min_pair_synapses")) ||
                !"false".equals(values.get("autapses_removed")) || !ROSTER_HASH.equals(values.get("roster_sha256")))
                throw new IOException("Unreviewed graph profile");
            String hash = values.get("asset_sha256");
            if (!hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid graph identity");
            long size = Long.parseLong(values.get("asset_size"));
            int neurons = Integer.parseInt(values.get("neurons")), pairs = Integer.parseInt(values.get("pairs"));
            long synapses = Long.parseLong(values.get("synapses"));
            if (neurons != 176422 || pairs <= 6287749 || pairs > 30000000 || synapses <= 90296905 || synapses > 200000000 ||
                size <= 22964094 || size > 300000000) throw new IOException("Graph exceeds reviewed capacity");
            return new Profile("full", asset, hash, size, neurons, pairs, synapses, 1);
        }
    }

    MaleCnsWorker(Path asset) { retained = Profile.retained(asset); full = null; }
    MaleCnsWorker(Path retainedAsset, Path fullAsset, Path fullManifest) throws Exception {
        retained = Profile.retained(retainedAsset);
        full = Profile.full(fullAsset, fullManifest);
    }

    private static void check(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }

    private void select(Profile profile) throws Exception {
        if (profile == current) return;
        // An explicit profile/session change releases the old network before loading the new one.
        // The two graphs are never retained together; the process is launched with a bounded heap.
        net = null; c = null; pi = null; populations = null; current = null;
        System.gc();
        check(Files.size(profile.asset) == profile.size, "Asset size mismatch");
        var hash = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(profile.asset)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) >= 0) hash.update(buffer, 0, n);
        }
        check(HexFormat.of().formatHex(hash.digest()).equals(profile.hash), "Asset hash mismatch");
        try (var in = Files.newInputStream(profile.asset)) { c = Connectome.load(in); }
        check(c.dataset.equals("male-cns:v1.0") && c.n == profile.neurons && c.nEdges == profile.pairs, "Wrong graph");
        check(c.rowPtr[0] == 0 && c.rowPtr[c.n] == c.nEdges, "Invalid graph offsets");
        var roster = MessageDigest.getInstance("SHA-256");
        long synapses = 0;
        for (int i = 0; i < c.n; i++) {
            check(c.bodyId[i] > 0 && (i == 0 || c.bodyId[i] > c.bodyId[i - 1]), "Invalid neuron roster");
            long body = c.bodyId[i];
            for (int b = 0; b < 8; b++) roster.update((byte) (body >>> (8 * b)));
            check(c.rowPtr[i] >= 0 && c.rowPtr[i] <= c.rowPtr[i + 1] && c.rowPtr[i + 1] <= c.nEdges, "Invalid graph offsets");
            check(c.ntSign[i] == 1 || c.ntSign[i] == -1, "Invalid transmitter sign");
            int previousTarget = -1;
            for (int k = c.rowPtr[i]; k < c.rowPtr[i + 1]; k++) {
                int target = c.postIdx[k], count = c.synapseCount(k);
                check(target >= 0 && target < c.n && (profile == full || target != i) && target > previousTarget, "Invalid graph edge");
                check(count >= profile.minPairSynapses, "Invalid synapse count");
                previousTarget = target; synapses += count;
            }
        }
        check(HexFormat.of().formatHex(roster.digest()).equals(ROSTER_HASH), "Neuron roster changed");
        check(synapses == profile.synapses, "Synapse total mismatch");
        pi = new PopulationIndex(c);
        populations = Arrays.stream(POPS).map(pi::resolve).toArray(int[][]::new);
        for (int[] p : populations) check(p.length > 0, "Missing motor population");
        var cfg = new LifConfig(); cfg.dtMs = .5; cfg.threads = 1; cfg.spikeLogCapacity = 0;
        net = new LifNetwork(c, cfg);
        current = profile;
    }

    private void drive(String population, double strength, double hz) {
        int[] p = pi.resolve(population);
        if (p.length == 0) throw new IllegalArgumentException("Missing sensory population");
        net.setStimulusRate(p, strength * hz);
    }

    String step(String line) throws Exception {
        String[] f = line.split("\t", -1);
        if ((f.length != 8 && f.length != 9) || !f[0].matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid request");
        Profile requested = retained;
        if (f.length == 9) {
            if (!f[8].equals("full") || full == null) throw new IllegalArgumentException("Full graph unavailable");
            requested = full;
        }
        long nextSeq = Long.parseLong(f[1]);
        if (nextSeq < 1 || nextSeq > 9007199254740991L) throw new IllegalArgumentException("Invalid sequence");
        double[] v = new double[6];
        for (int i = 0; i < v.length; i++) {
            v[i] = Double.parseDouble(f[i + 2]);
            if (!Double.isFinite(v[i]) || v[i] < 0 || v[i] > 1) throw new IllegalArgumentException("Invalid stimulus");
        }
        if (session.equals(f[0])) {
            if (requested != current) throw new IllegalArgumentException("Graph change requires a new session");
            if (nextSeq == seq && line.equals(lastLine)) return lastResponse;
            if (nextSeq <= seq) throw new IllegalArgumentException("Stale sequence");
        } else {
            select(requested); net.reset(); session = f[0]; seq = 0; lastLine = "";
        }
        net.clearStimuli();
        // Abstract habitat sensations; they do not claim to reproduce a fly's physical receptors.
        drive("LB3b,LB3c", v[0], 120); drive("PhG1a,PhG1b,PhG1c", v[0], 100); drive("LgLG3", v[0], 80);
        drive("subclass:wind_gravity,subclass:grooming", v[1], 150);
        drive("LC4/L,LPLC2/L", v[2], 150); drive("LC4/R,LPLC2/R", v[3], 150);
        drive("LC10a/L", v[4], 40); drive("LC10a/R", v[5], 40);
        long start = System.nanoTime(), before = net.totalSpikes();
        net.runMs(50); // Exactly 100 half-millisecond steps; never catch up a wall-clock backlog.
        double[] rates = new double[populations.length];
        for (int i = 0; i < populations.length; i++) {
            rates[i] = net.populationRateHz(populations[i], 50);
            check(Double.isFinite(rates[i]) && rates[i] >= 0 && rates[i] <= 2000, "Invalid motor measurement");
        }
        int active = net.activeNeurons(), silent = 0; double sumHz = 0, maxHz = 0;
        for (int i = 0; i < c.n; i++) {
            check(Float.isFinite(net.membrane(i)), "Non-finite neural state");
            double hz = net.spikesThisTick(i) * 20.0;
            if (hz == 0) silent++; sumHz += hz; maxHz = Math.max(maxHz, hz);
        }
        long spikes = net.totalSpikes() - before;
        net.endTick(50);
        double computeMs = (System.nanoTime() - start) / 1e6;
        var out = new StringBuilder(String.format(Locale.ROOT,
            "{\"protocol\":%d,\"graph\":\"%s\",\"session\":\"%s\",\"seq\":%d,\"asset_sha256\":\"%s\",\"neurons\":%d,\"pairs\":%d,\"synapses\":%d,\"sim_ms\":%.1f,\"compute_ms\":%.3f,\"spikes\":%d,\"active\":%d,\"silent\":%d,\"mean_hz\":%.6f,\"max_hz\":%.3f",
            current == full ? 2 : 1, current.name, session, nextSeq, current.hash, current.neurons, current.pairs,
            current.synapses, net.simTimeMs(), computeMs, spikes, active, silent, sumHz / c.n, maxHz));
        for (int i = 0; i < rates.length; i++) out.append(String.format(Locale.ROOT, ",\"%s\":%.6f", KEYS[i], rates[i]));
        out.append("}");
        seq = nextSeq; lastLine = line; lastResponse = out.toString();
        return lastResponse;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) throw new IllegalArgumentException("Expected retained asset and optional full asset/manifest paths");
        var worker = args.length == 1 ? new MaleCnsWorker(Path.of(args[0])) :
            new MaleCnsWorker(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            if (line.length() > 1024) break;
            try { System.out.println(worker.step(line)); }
            catch (Exception error) { System.out.println("{\"error\":\"Invalid neural request or state\"}"); break; }
            System.out.flush();
        }
    }
}
