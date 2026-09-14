import com.fruitfly.brain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Request-driven, bounded neural worker. Its only interface is its SSH stdin/stdout. */
public final class MaleCnsWorker {
    private static final String HASH = "e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3";
    private static final String[] POPS = {"MN9", "DNp01", "TTMn", "DNg62", "DNge078", "prefix:DNg02", "DNa02/L", "DNa02/R"};
    private static final String[] KEYS = {"feed_hz", "escape_hz", "jump_hz", "groom_hz", "groom2_hz", "wing_hz", "left_hz", "right_hz"};
    private final Connectome c;
    private final PopulationIndex pi;
    private final LifNetwork net;
    private final int[][] populations;
    private String session = "", lastLine = "", lastResponse = "";
    private long seq = 0;

    MaleCnsWorker(Path asset) throws Exception {
        if (Files.size(asset) != 22964094) throw new IOException("Asset size mismatch");
        var hash = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(asset)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) >= 0) hash.update(buffer, 0, n);
        }
        if (!HexFormat.of().formatHex(hash.digest()).equals(HASH)) throw new IOException("Asset hash mismatch");
        try (var in = Files.newInputStream(asset)) { c = Connectome.load(in); }
        if (c.n != 176422 || c.nEdges != 6287749) throw new IOException("Wrong graph");
        pi = new PopulationIndex(c);
        var cfg = new LifConfig(); cfg.dtMs = .5; cfg.threads = 1; cfg.spikeLogCapacity = 0;
        net = new LifNetwork(c, cfg);
        populations = Arrays.stream(POPS).map(pi::resolve).toArray(int[][]::new);
        for (int[] p : populations) if (p.length == 0) throw new IOException("Missing motor population");
    }

    private void drive(String population, double strength, double hz) {
        int[] p = pi.resolve(population);
        if (p.length == 0) throw new IllegalArgumentException("Missing sensory population");
        net.setStimulusRate(p, strength * hz);
    }

    String step(String line) throws Exception {
        String[] f = line.split("\t", -1);
        if (f.length != 8 || !f[0].matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid request");
        long nextSeq = Long.parseLong(f[1]);
        if (nextSeq < 1 || nextSeq > 9007199254740991L) throw new IllegalArgumentException("Invalid sequence");
        double[] v = new double[6];
        for (int i = 0; i < v.length; i++) {
            v[i] = Double.parseDouble(f[i + 2]);
            if (!Double.isFinite(v[i]) || v[i] < 0 || v[i] > 1) throw new IllegalArgumentException("Invalid stimulus");
        }
        if (!session.equals(f[0])) { net.reset(); session = f[0]; seq = 0; lastLine = ""; }
        if (nextSeq == seq && line.equals(lastLine)) return lastResponse;
        if (nextSeq <= seq) throw new IllegalArgumentException("Stale sequence");
        net.clearStimuli();
        // Abstract habitat sensations; they do not claim to reproduce a fly's physical receptors.
        drive("LB3b,LB3c", v[0], 120); drive("PhG1a,PhG1b,PhG1c", v[0], 100); drive("LgLG3", v[0], 80);
        drive("subclass:wind_gravity,subclass:grooming", v[1], 150);
        drive("LC4/L,LPLC2/L", v[2], 150); drive("LC4/R,LPLC2/R", v[3], 150);
        drive("LC10a/L", v[4], 40); drive("LC10a/R", v[5], 40);
        long start = System.nanoTime(), before = net.totalSpikes();
        net.runMs(50); // Exactly 100 half-millisecond steps; never catch up a wall-clock backlog.
        double[] rates = new double[populations.length];
        for (int i = 0; i < populations.length; i++) rates[i] = net.populationRateHz(populations[i], 50);
        int active = net.activeNeurons(), silent = 0; double sumHz = 0, maxHz = 0;
        for (int i = 0; i < c.n; i++) {
            if (!Float.isFinite(net.membrane(i))) throw new IOException("Non-finite neural state");
            double hz = net.spikesThisTick(i) * 20.0;
            if (hz == 0) silent++; sumHz += hz; maxHz = Math.max(maxHz, hz);
        }
        long spikes = net.totalSpikes() - before;
        net.endTick(50);
        double computeMs = (System.nanoTime() - start) / 1e6;
        var out = new StringBuilder(String.format(Locale.ROOT,
            "{\"protocol\":1,\"session\":\"%s\",\"seq\":%d,\"asset_sha256\":\"%s\",\"neurons\":176422,\"pairs\":6287749,\"synapses\":90296905,\"sim_ms\":%.1f,\"compute_ms\":%.3f,\"spikes\":%d,\"active\":%d,\"silent\":%d,\"mean_hz\":%.6f,\"max_hz\":%.3f",
            session, nextSeq, HASH, net.simTimeMs(), computeMs, spikes, active, silent, sumHz / c.n, maxHz));
        for (int i = 0; i < rates.length; i++) out.append(String.format(Locale.ROOT, ",\"%s\":%.6f", KEYS[i], rates[i]));
        out.append("}");
        seq = nextSeq; lastLine = line; lastResponse = out.toString();
        return lastResponse;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected pinned asset path");
        var worker = new MaleCnsWorker(Path.of(args[0]));
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
