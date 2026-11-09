package dev.tomng.alarme.feature.missions.engine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.random.Random
import org.junit.Test

/** The colour-tile game: a difficulty² grid, 3/5/8/9 highlighted tiles to reproduce, rounds. */
class MemoryMissionEngineTest {

    private val engine = MemoryMissionEngine()

    @Test
    fun `the grid size and correct-tile count follow the difficulty`() {
        assertThat(boardSpec(3)).isEqualTo(9 to 3)
        assertThat(boardSpec(4)).isEqualTo(16 to 5)
        assertThat(boardSpec(5)).isEqualTo(25 to 8)
        assertThat(boardSpec(6)).isEqualTo(36 to 9)
        assertThat(boardSpec(7)).isEqualTo(49 to 9)
    }

    @Test
    fun `a fresh board is untouched with all correct tiles hidden`() {
        val state = engine.start(mission(rounds = 1, difficulty = 3))
        assertThat(state.phase).isEqualTo(MemoryMissionEngine.Phase.MEMORIZING)
        assertThat(state.board.tiles.none { it.state != MemoryMissionEngine.TileState.NORMAL })
            .isTrue()
    }

    @Test
    fun `deterministic with a seeded random`() {
        val a = MemoryMissionEngine(Random(7)).start(mission(2, 4))
        val b = MemoryMissionEngine(Random(7)).start(mission(2, 4))
        assertThat(a.board.correct).isEqualTo(b.board.correct)
    }

    @Test
    fun `a correct tap latches and counts down`() {
        val state = engine.start(mission(rounds = 1, difficulty = 3))
        val playing = engine.beginPlaying(state)
        val target = playing.board.correct.first()

        val after = engine.tap(playing, target)

        assertThat(after.board.tiles[target].state).isEqualTo(MemoryMissionEngine.TileState.CORRECT)
        assertThat(after.board.remaining).isEqualTo(2)
    }

    @Test
    fun `a wrong tap latches wrong without counting down`() {
        val state = engine.start(mission(rounds = 1, difficulty = 3))
        val playing = engine.beginPlaying(state)
        val innocent = (0 until 9).first { it !in playing.board.correct }

        val after = engine.tap(playing, innocent)

        assertThat(after.board.tiles[innocent].state).isEqualTo(MemoryMissionEngine.TileState.WRONG)
        assertThat(after.board.remaining).isEqualTo(3)
        assertThat(after.mistakes).isEqualTo(1)
    }

    @Test
    fun `taps before the playing phase or on latched tiles are ignored`() {
        val state = engine.start(mission(rounds = 1, difficulty = 3))
        assertThat(engine.tap(state, state.board.correct.first())).isEqualTo(state)

        val playing = engine.beginPlaying(state)
        val target = playing.board.correct.first()
        val once = engine.tap(playing, target)
        val twice = engine.tap(once, target)
        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun `clearing every tile finishes the round, then the next starts fresh`() {
        val state = engine.start(mission(rounds = 2, difficulty = 3))
        var current = engine.beginPlaying(state)
        for (index in current.board.correct.toList()) {
            current = engine.tap(current, index)
        }
        assertThat(current.phase).isEqualTo(MemoryMissionEngine.Phase.ROUND_CLEAR)

        val next = engine.nextRound(current)
        assertThat(next.round).isEqualTo(1)
        assertThat(next.phase).isEqualTo(MemoryMissionEngine.Phase.MEMORIZING)
        assertThat(next.board.remaining).isEqualTo(3)
        assertThat(next.board.tiles.none { it.state != MemoryMissionEngine.TileState.NORMAL })
            .isTrue()
    }

    @Test
    fun `after the last round the mission is complete`() {
        var state = engine.start(mission(rounds = 1, difficulty = 3))
        state = engine.beginPlaying(state)
        for (index in state.board.correct.toList()) {
            state = engine.tap(state, index)
        }
        state = engine.nextRound(state)
        assertThat(state.phase).isEqualTo(MemoryMissionEngine.Phase.COMPLETE)
        assertThat(state.board.tiles).isEmpty()
    }

    private fun boardSpec(difficulty: Int): Pair<Int, Int> {
        val state = engine.start(mission(rounds = 1, difficulty = difficulty))
        return state.board.tiles.size to state.board.correct.size
    }

    private fun mission(rounds: Int, difficulty: Int) =
        Mission.Memory(rounds = rounds, difficulty = difficulty)
}
