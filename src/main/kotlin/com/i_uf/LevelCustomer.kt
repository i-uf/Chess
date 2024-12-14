package com.i_uf

import com.i_uf.Piece.*
import java.util.*

object LevelCustomer {
    private var board = Board(Piece.default.clone(), Player.WHITE)
    fun createLevel() = Level(board)
}
private val f2p = mapOf(
    'R' to arrayOf(W_ROOK),
    'N' to arrayOf(W_KNIGHT),
    'B' to arrayOf(W_BISHOP),
    'Q' to arrayOf(W_QUEEN),
    'K' to arrayOf(W_KING),
    'P' to arrayOf(W_PAWN),
    'r' to arrayOf(B_ROOK),
    'n' to arrayOf(B_KNIGHT),
    'b' to arrayOf(B_BISHOP),
    'q' to arrayOf(B_QUEEN),
    'k' to arrayOf(B_KING),
    'p' to arrayOf(B_PAWN),
    '1' to Array(1) { NONE },
    '2' to Array(2) { NONE },
    '3' to Array(3) { NONE },
    '4' to Array(4) { NONE },
    '5' to Array(5) { NONE },
    '6' to Array(6) { NONE },
    '7' to Array(7) { NONE },
    '8' to Array(8) { NONE }
)
private fun fileCasting() {

}
fun loadWithFEN(fen: String) : Level {
    val split = fen.split(" ")
    val board = mutableListOf<LinkedList<Piece>>()
    for(pieces in split[0].split("/").asReversed()) {
        val line = LinkedList<Piece>()
        pieces.forEach { line.addAll(f2p[it]!!) }
        board.add(line)
    }
    val player = if(split[1] == "w") Player.WHITE else Player.BLACK
    println(split)
    return Level(Board(
        board.map { it.toTypedArray() }.toTypedArray(),
        player).let
    {Board(
        it.board, it.player,
        arrayOf(Position(-1, -1) to Position(-1, -1)),
        if(split[3] == "-") Position(-1, -1) else Position(split[3][1] - '1', split[3][0] - 'a'),
        "K" in split[2],
        "k" in split[2],
        "Q" in split[2],
        "q" in split[2],
        split[4].toInt(),
        split[5].toInt()
    )})
}