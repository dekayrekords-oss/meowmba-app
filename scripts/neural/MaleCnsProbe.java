import com.fruitfly.brain.Connectome;
import com.fruitfly.brain.LifConfig;
import com.fruitfly.brain.LifNetwork;
import com.fruitfly.brain.PopulationIndex;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/** Offline probe of a pinned external model; never sends robot or phone commands. */
public final class MaleCnsProbe {
    private static final String[] REPORTS = {"MN9", "DNp01", "TTMn", "DNg62", "DNge078", "prefix:DNg02", "DNa02/L", "DNa02/R"};

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    private static Connectome load(String path) throws Exception {
        Connectome c;
        try (var stream = new FileInputStream(path)) { c = Connectome.load(stream); }
        require(c.dataset.equals("male-cns:v1.0"), "Dataset mismatch");
        require(c.n == 176422 && c.nEdges == 6287749, "Graph counts changed");
        long synapses = 0;
        require(c.rowPtr[0] == 0, "Invalid first CSR offset");
        for (int i = 0; i < c.n; i++) {
            require(i == 0 || c.bodyId[i] > c.bodyId[i - 1], "IDs not strictly sorted");
            require(c.rowPtr[i] <= c.rowPtr[i + 1], "Invalid CSR ordering");
            for (int k = c.rowPtr[i]; k < c.rowPtr[i + 1]; k++) {
                require(c.postIdx[k] >= 0 && c.postIdx[k] < c.n && c.postIdx[k] != i, "Invalid edge");
                require(c.synapseCount(k) >= 5, "Threshold mismatch");
                synapses += c.synapseCount(k);
            }
        }
        require(synapses == 90296905, "Anatomical count mismatch");
        return c;
    }

    private static void stimulate(LifNetwork net, PopulationIndex pi, String scenario) {
        switch (scenario) {
            case "quiet": break;
            case "sugar":
            case "sugar_bitter":
                drive(net, pi, "LB3b,LB3c", 120);
                drive(net, pi, "PhG1a,PhG1b,PhG1c", 100);
                drive(net, pi, "LgLG3", 80);
                if (scenario.equals("sugar_bitter")) drive(net, pi, "LB1a,LB1b,LB1c,LB1d", 120);
                break;
            case "loom":
                drive(net, pi, "LC4/R,LPLC2/R", 150); break;
            case "groom":
                drive(net, pi, "subclass:wind_gravity,subclass:grooming", 150); break;
            default: throw new IllegalArgumentException("Unknown scenario");
        }
    }

    private static void drive(LifNetwork net, PopulationIndex pi, String spec, double hz) {
        int[] ids = pi.resolve(spec);
        require(ids.length > 0, "Missing stimulus population: " + spec);
        net.setStimulusRate(ids, hz);
    }

    private static void hashInt(MessageDigest hash, int value) {
        hash.update((byte) (value >>> 24)); hash.update((byte) (value >>> 16));
        hash.update((byte) (value >>> 8)); hash.update((byte) value);
    }

    private static void run(LifNetwork net, PopulationIndex pi, String scenario, String label, int duration) throws Exception {
        stimulate(net, pi, scenario);
        int[][] pops = new int[REPORTS.length][];
        for (int i = 0; i < REPORTS.length; i++) {
            pops[i] = pi.resolve(REPORTS[i]);
            require(pops[i].length > 0, "Missing report population: " + REPORTS[i]);
        }
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        double[] sums = new double[pops.length];
        long[] nanos = new long[duration / 50];
        int maxActive = 0;
        for (int t = 0; t < nanos.length; t++) {
            long start = System.nanoTime();
            net.runMs(50);
            // Include the cost of observation/reset, which an avatar loop would also pay.
            for (int j = 0; j < pops.length; j++) sums[j] += net.populationRateHz(pops[j], 50);
            maxActive = Math.max(maxActive, net.activeNeurons());
            // Hash every neuron's spike count and membrane before tick counters are cleared.
            // Hashing cost is excluded from simulator timing.
            long beforeHash = System.nanoTime();
            for (int i = 0; i < net.n; i++) {
                require(Float.isFinite(net.membrane(i)), "Non-finite membrane");
                hashInt(hash, net.spikesThisTick(i));
                hashInt(hash, Float.floatToIntBits(net.membrane(i)));
            }
            long afterHash = System.nanoTime();
            net.endTick(50);
            nanos[t] = System.nanoTime() - start - (afterHash - beforeHash);
        }
        double wallMs = Arrays.stream(nanos).sum() / 1e6;
        Arrays.sort(nanos);
        System.out.printf(Locale.ROOT,
            "{\"label\":\"%s\",\"scenario\":\"%s\",\"duration_ms\":%d,\"spikes\":%d,\"max_active\":%d,\"state_sha256\":\"%s\",\"compute_ms\":%.4f,\"tick_p95_ms\":%.4f,\"real_time_factor\":%.4f,\"mean_population_hz\":{",
            label, scenario, duration, net.totalSpikes(), maxActive,
            HexFormat.of().formatHex(hash.digest()), wallMs,
            nanos[(int) Math.ceil(nanos.length * .95) - 1] / 1e6, duration / wallMs);
        for (int j = 0; j < pops.length; j++) {
            if (j > 0) System.out.print(",");
            System.out.printf(Locale.ROOT, "\"%s\":%.5f", REPORTS[j], sums[j] / nanos.length);
        }
        System.out.println("}}");
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 4, "Expected data path, scenario, mode, duration ms");
        String scenario = args[1], mode = args[2];
        int duration = Integer.parseInt(args[3]);
        require(duration >= 600 && duration <= 5000 && duration % 50 == 0, "Invalid duration");
        require(mode.equals("off") || mode.equals("on") || mode.equals("reset") || mode.equals("ablated"), "Invalid mode");
        Connectome c = load(args[0]);
        if (mode.equals("ablated")) Arrays.fill(c.weight, (short) 0);
        LifConfig cfg = new LifConfig();
        cfg.dtMs = .5; cfg.threads = 1; cfg.seed = 20260903L;
        cfg.spikeLogCapacity = mode.equals("on") ? 8192 : 0;
        PopulationIndex pi = new PopulationIndex(c);
        LifNetwork net = new LifNetwork(c, cfg);
        run(net, pi, scenario, mode, duration);
        if (mode.equals("reset")) {
            net.reset();
            run(net, pi, scenario, "after_reset", duration);
        }
    }
}
