package dev.tomng.alarme.feature.missions.engine

import dev.tomng.alarme.core.model.mission.Mission
import kotlin.random.Random

/**
 * The colour-tile memory game. A difficulty² grid is generated with 3/5/8/9 highlighted tiles
 * (difficulty 3 to 7); the grid flashes them, then the player reproduces the set from memory.
 * Correct taps latch, a wrong tap latches as a visible mistake, and clearing every tile finishes
 * the round.
 */
class MemoryMissionEngine(private val random: Random = Random.Default) {

    enum class Phase {
        /** The grid flashes the correct tiles. */
        MEMORIZING,

        /** A short prompt before the tiles hide. */
        HINTING,

        /** The player is tapping tiles. */
        PLAYING,

        /** Every tile found this round. */
        ROUND_CLEAR,

        /** All rounds finished. */
        COMPLETE,
    }

    enum class TileState {
        NORMAL,
        CORRECT,
        WRONG,
    }

    data class Tile(
        val isCorrect: Boolean,
        val state: TileState = TileState.NORMAL,
    )

    data class Board(
        val difficulty: Int,
        val tiles: List<Tile>,
    ) {
        val correct: List<Int> = tiles.indices.filter { tiles[it].isCorrect }
        val correctIndices: IntRange = tiles.indices
        val remaining: Int
            get() = correct.count { tiles[it].state != TileState.CORRECT }
    }

    data class State(
        val round: Int,
        val rounds: Int,
        val phase: Phase,
        val board: Board,
        val mistakes: Int = 0,
    )

    fun start(mission: Mission.Memory): State =
        State(
            round = 0,
            rounds = mission.rounds.coerceAtLeast(1),
            phase = Phase.MEMORIZING,
            board = generateBoard(mission.difficulty),
        )

    /** The flash and hint phases are done: the player may now tap. */
    fun beginPlaying(state: State): State =
        if (state.phase == Phase.MEMORIZING || state.phase == Phase.HINTING) {
            state.copy(phase = Phase.PLAYING)
        } else {
            state
        }

    fun tap(state: State, index: Int): State {
        if (state.phase != Phase.PLAYING || index !in state.board.tiles.indices) return state
        val tile = state.board.tiles[index]
        if (tile.state != TileState.NORMAL) return state
        val tiles =
            if (tile.isCorrect) {
                state.board.tiles.mapIndexed { i, t ->
                    if (i == index) t.copy(state = TileState.CORRECT) else t
                }
            } else {
                state.board.tiles.mapIndexed { i, t ->
                    if (i == index) t.copy(state = TileState.WRONG) else t
                }
            }
        val board = state.board.copy(tiles = tiles)
        val mistakes = state.mistakes + if (tile.isCorrect) 0 else 1
        return if (board.remaining == 0) {
            state.copy(phase = Phase.ROUND_CLEAR, board = board, mistakes = mistakes)
        } else {
            state.copy(board = board, mistakes = mistakes)
        }
    }

    fun nextRound(state: State): State {
        if (state.phase != Phase.ROUND_CLEAR) return state
        val next = state.round + 1
        return if (next < state.rounds) {
            State(
                round = next,
                rounds = state.rounds,
                phase = Phase.MEMORIZING,
                board = generateBoard(state.board.difficulty),
                mistakes = state.mistakes,
            )
        } else {
            State(
                round = state.round,
                rounds = state.rounds,
                phase = Phase.COMPLETE,
                board = Board(state.board.difficulty, emptyList()),
                mistakes = state.mistakes,
            )
        }
    }

    private fun generateBoard(difficulty: Int): Board {
        val clamped = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
        val size = clamped * clamped
        val correctCount = CORRECT_BY_DIFFICULTY[clamped] ?: 9
        val indices = (0 until size).shuffled(random).take(correctCount).toSet()
        return Board(
            difficulty = clamped,
            tiles = List(size) { Tile(isCorrect = it in indices) },
        )
    }

    companion object {
        const val MIN_DIFFICULTY = 3
        const val MAX_DIFFICULTY = 7

        private val CORRECT_BY_DIFFICULTY = mapOf(3 to 3, 4 to 5, 5 to 8, 6 to 9, 7 to 9)
    }
}
