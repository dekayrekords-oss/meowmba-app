package com.example.meowmba.core.neural

/** Identifies one continuous channel: a sense reporting one kind of state. */
data class ChannelKey(val source: SensorySource, val kind: SensoryKind)

/** What the encoder receives on one tick. */
class SensoryBatch(
    /** Fresh continuous channels only; a stale channel is simply absent. */
    val continuous: Map<ChannelKey, SensoryEvent>,
    /** Transition impulses in arrival order, including synthesised [SensoryKind.SENSOR_LOST]. */
    val transitions: List<SensoryEvent>,
    /** Channels that went stale since the last drain, for diagnostics. */
    val lostChannels: List<ChannelKey>,
) {
    val isEmpty: Boolean get() = continuous.isEmpty() && transitions.isEmpty()

    companion object {
        val EMPTY = SensoryBatch(emptyMap(), emptyList(), emptyList())
    }
}

/**
 * Bounded, coalescing hand-off between the senses (any thread) and the neural
 * tick (one thread).
 *
 * Continuous kinds keep only the latest value per channel, so a camera running
 * at 30 fps costs one slot, not a queue. Transition kinds keep order in a
 * bounded FIFO; when it overflows the oldest impulses are dropped and counted,
 * never silently. An event older than its own freshness limit on arrival is
 * rejected: reacting now to something that happened two seconds ago would be
 * worse than not reacting.
 *
 * When a continuous channel stops updating past its freshness limit the bus
 * synthesises one [SensoryKind.SENSOR_LOST] impulse for it. Sensor failure is
 * then an explicit input the network can learn to treat as loss of evidence,
 * rather than a value that quietly freezes at its last reading.
 */
class SensoryEventBus(
    private val transitionCapacity: Int = 64,
) {
    init {
        require(transitionCapacity > 0)
    }

    private val latest = LinkedHashMap<ChannelKey, SensoryEvent>()
    private val transitions = ArrayDeque<SensoryEvent>()
    private val lostReported = HashSet<ChannelKey>()

    var droppedTransitions: Long = 0L
        private set
    var rejectedStale: Long = 0L
        private set
    var accepted: Long = 0L
        private set

    /**
     * Offer an event observed at [nowMs] (controller clock). Unstamped events are
     * stamped with [nowMs]. Returns false when the event was rejected as stale.
     */
    @Synchronized
    fun offer(event: SensoryEvent, nowMs: Long): Boolean {
        val stamped = event.stamped(nowMs).sanitized()
        if (stamped.isStale(nowMs)) {
            rejectedStale++
            return false
        }
        accepted++
        if (stamped.kind.continuous) {
            val key = ChannelKey(stamped.source, stamped.kind)
            latest[key] = stamped
            lostReported.remove(key)
        } else {
            if (transitions.size >= transitionCapacity) {
                transitions.removeFirst()
                droppedTransitions++
            }
            transitions.addLast(stamped)
        }
        return true
    }

    /**
     * Take everything the tick should see at [nowMs]. Transitions are removed;
     * continuous channels stay for the next drain until they go stale.
     */
    @Synchronized
    fun drain(nowMs: Long): SensoryBatch {
        val fresh = LinkedHashMap<ChannelKey, SensoryEvent>()
        val lost = ArrayList<ChannelKey>()
        for ((key, event) in latest) {
            if (event.isStale(nowMs)) {
                if (lostReported.add(key)) lost += key
            } else {
                fresh[key] = event
            }
        }
        val impulses = ArrayList<SensoryEvent>(transitions.size + lost.size)
        impulses.addAll(transitions)
        transitions.clear()
        for (key in lost) {
            impulses += SensoryEvent(
                kind = SensoryKind.SENSOR_LOST,
                source = key.source,
                values = floatArrayOf(1f),
                confidence = 1f,
                timestampMs = nowMs,
            )
        }
        return SensoryBatch(fresh, impulses, lost)
    }

    /** Discard everything, including the stale-channel memory. Used after a suspension. */
    @Synchronized
    fun clear() {
        latest.clear()
        transitions.clear()
        lostReported.clear()
    }

    @Synchronized
    fun pendingTransitions(): Int = transitions.size

    @Synchronized
    fun continuousChannels(): Int = latest.size
}
