package com.example.meowmba.core.audio

/** Only skip information-free zero PCM before a phrase. Once audio begins,
 * preserve even zero trailing frames so the recognizer can endpoint normally.
 * This is not energy-based VAD: a sample of +/-1 still opens the gate.
 */
internal class DigitalSilenceGate {
    private var heardSignal = false

    fun shouldDecode(pcm: ShortArray, count: Int): Boolean {
        require(count in 0..pcm.size)
        if (!heardSignal) {
            for (i in 0 until count) if (pcm[i] != 0.toShort()) {
                heardSignal = true
                break
            }
        }
        return heardSignal
    }

    fun reset() { heardSignal = false }
}
