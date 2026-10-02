package com.jovi.photoai.ui.reference

import com.jovi.photoai.reference.DirectorCard

/** Same assertions are executed by JUnit and the SDK-free runner; no fake implementation. */
internal object DirectorPreparationCases {
    fun run(): Int {
        var count = 0
        fun verify(value: Boolean) { check(value) { "Preparation assertion ${count + 1}" }; count++ }
        val card = DirectorCard("环境", "主体", "情绪", "机位")
        val scope = directorPreparationScope(card, "离线知识包")
        verify(scope.length == 64)
        verify(scope == directorPreparationScope(card, "离线知识包"))
        verify(scope != directorPreparationScope(card, "本机 VLM"))
        for (changed in listOf(card.copy(environment = "其他环境"), card.copy(subject = "其他主体"),
                card.copy(emotion = "其他情绪"), card.copy(camera = "其他机位"))) {
            verify(scope != directorPreparationScope(changed, "离线知识包"))
        }
        var state = DirectorPreparation(scope)
        verify(state.countFor(scope) == 0)
        for ((index, step) in PreparationStep.values().withIndex()) {
            state = state.toggle(scope, step)
            verify(state.countFor(scope) == index + 1)
            verify(state.maskFor(scope) and step.bit != 0)
        }
        verify(state.maskFor(scope) == 31)
        verify(state.maskFor("other") == 0)
        verify(state.countFor("other") == 0)
        verify(state.toggle("other", PreparationStep.CAMERA) == DirectorPreparation("other", 16))
        for (step in PreparationStep.values()) {
            state = state.toggle(scope, step)
            verify(state.maskFor(scope) and step.bit == 0)
        }
        verify(state.countFor(scope) == 0)
        verify(DirectorPreparation(scope, 255).maskFor(scope) == 31)
        verify(DirectorPreparation(scope, 255).countFor(scope) == 5)
        verify(DirectorPreparation("restored-old", 31).maskFor(scope) == 0)
        verify(directorPreparationScope(DirectorCard("a", "bc", "d", "e"), "s") !=
            directorPreparationScope(DirectorCard("ab", "c", "d", "e"), "s"))
        verify(DIRECTOR_REFERENCE_NOTICE.contains("不是实时"))
        verify(!DIRECTOR_REFERENCE_NOTICE.contains("固定示例"))
        return count
    }

    @JvmStatic fun main(args: Array<String>) { println("PASS directorPreparation=${run()} assertions HOST_ONLY") }
}
