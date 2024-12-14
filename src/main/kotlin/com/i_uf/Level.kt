package com.i_uf

import java.util.*
import com.i_uf.Piece.*
import java.util.regex.Pattern

private val p2p = mapOf(
    'R' to (W_ROOK to B_ROOK),
    'N' to (W_KNIGHT to B_KNIGHT),
    'B' to (W_BISHOP to B_BISHOP),
    'Q' to (W_QUEEN to B_QUEEN),
    'K' to (W_KING to B_KING)
)

class Level(board: Board, var white: String = "", var black: String = "",
            var whiteElo: Int = -1, var blackElo: Int = -1, var result: String = "*") {
    private val stack = Stack<Board>().apply { add(board) }
    private val undid = Stack<Board>()
    val push = { item: Board ->
        stack.push(item)
        if(curr().stalemate()) result = if(Board(curr().board, curr().player.next()).kingSafety()) "1/2-1/2"
        else if(item.player != Player.WHITE) "1-0" else "0-1"
        undid.clear()
    }
    val notation = { solution(curr()) }
    private fun solution(board: Board): String =
        if (board == board.prev) ""
        else solution(board.prev) + (if (board.player == Player.BLACK || board.prev.prev == board.prev) "" +
                board.prev.fullMove + if (board.player == Player.WHITE) "..." else ". "
        else "") + notation(board) + if (board.player == Player.WHITE) '\n' else ' '
    val curr = { stack.peek() }
    val undo = { if(stack.size > 1) undid.push(stack.pop()) }
    val redo = { if(undid.size > 1) stack.push(undid.pop()) }
    val allMoves = { LinkedList<Board>().apply { addAll(stack) ; addAll(undid) } }
}
private fun shortestWay(board: Board, piece: Piece, move: Move) : String {
    val found = board.canMoveGo(move, piece)
    val foundSameRank = board.canMoveGo(move, piece) { a, _ -> a == move.first.rank }
    val foundSameFile = board.canMoveGo(move, piece) { _, b -> b == move.first.file }
    val isFileNeeds = found && !foundSameFile || foundSameRank
    return "" + (if(isFileNeeds) 'a' + move.first.file else "") + (if (foundSameFile) '1' + move.first.rank else "")
}
fun notation(board: Board) : String {
    val result = StringBuilder()
    val move = board.move[0]
    result.append(
        if(board.move.size == 3) if (move.second.file==2) "O-O-O" else "O-O"
        else {
            when(board.prev.get(move.first.rank, move.first.file)) {
                W_PAWN, B_PAWN -> {
                    (if(board.move.size == 2) "${'a' + move.first.file}" else "" ) +
                    (if(board.move.size == 2) "x" else "") + "${'a' + move.second.file}${'1' + move.second.rank}" +
                    when (board.move[0].second.let { board.get(it.rank, it.file) }) {
                        B_ROOK, W_ROOK -> "=R"
                        B_KNIGHT, W_KNIGHT -> "=N"
                        B_BISHOP, W_BISHOP -> "=B"
                        B_QUEEN, W_QUEEN -> "=Q"
                        else -> ""
                    }
                }
                else -> when(board.prev.get(move.first.rank, move.first.file)) {
                    W_ROOK, B_ROOK -> "R"
                    W_KNIGHT, B_KNIGHT -> "N"
                    W_BISHOP, B_BISHOP -> "B"
                    W_QUEEN, B_QUEEN -> "Q"
                    W_KING, B_KING -> "K"
                    else -> ""
                } + shortestWay(board.prev, board.prev.get(move.first.rank, move.first.file), move) +
                        (if(board.move.size == 2) "x" else "") + "${'a' + move.second.file}${'1' + move.second.rank}"
            }
        }
    )
    if(!Board(board.board, board.player.next()).kingSafety()) result.append(if(board.stalemate()) '#' else '+');
    return result.toString()
}
fun load(level: Level, notation: String, opponent: Player) {
    if(notation.isBlank()) return
    if(notation.startsWith("O-O-O")) {
        val rank = if(level.curr().player == Player.WHITE) 0 else 7
        level.curr().moveApply(rank, 4, rank, 2, opponent)?.let { level.push(it) }
        return
    }
    if(notation.startsWith("O-O")) {
        val rank = if(level.curr().player == Player.WHITE) 0 else 7
        level.curr().moveApply(rank, 4, rank, 6, opponent)?.let { level.push(it) }
        return
    }
    val getPiece: (Pair<Piece, Piece>) -> Piece = { if(level.curr().player == Player.WHITE) it.first else it.second }
    var piece = getPiece(W_PAWN to B_PAWN)
    p2p[notation[0]]?.let { piece = getPiece(it) }
    val rank = Stack<Int>()
    val file = Stack<Int>()
    for(c in notation) {
        if(c in 'a' .. 'h') file.push(c - 'a')
        if(c in '1' .. '8') rank.push(c - '1')
    }
    val p = Regex("=(?<P>[RNBQ])")
    val promotion = getPiece(p2p[((p.find(notation)?.groups?.get("P")?.value)?:"a")[0]] ?: (NONE to NONE))
    val newRank = rank.pop()
    val newFile = file.pop()
    val scopeRank = if(rank.empty()) 8 else rank.pop()
    val scopeFile = if(file.empty()) 8 else file.pop()
    level.curr().find(piece, scopeRank, scopeFile, newRank, newFile)
        .let { level.curr().moveApply(it.rank, it.file, newRank, newFile, opponent, promotion)?.let{ a -> level.push(a)} }
}
fun loadWithPGN(pgn: String) : Level {
    val tag = Pattern.compile("\\[\\s*(\\w+)\\s+\"([^\"]+)\"\\s*]").matcher(pgn)
    val onlyNotation = pgn.replace(Regex("\\[.*]|\\{.*}|\\d+\\.s*|1-0|0-1|1/2-1/2|[+#*]"), "").trim().split(Regex("\\s+"))
    val tags = mutableMapOf<String, String>()
    while (tag.find()) tags[tag.group(1)] = tag.group(2)
    val level = if(tags["SetUp"] == "1") loadWithFEN(tags["FEN"]!!) else
        Level(Board(Piece.default, Player.WHITE),
            tags["White"]?:"", tags["Black"]?:"",
            tags["WhiteElo"]?.toInt()?:0, tags["BlackElo"]?.toInt()?:0,
            tags["Result"]!!
        )
    for(notation in onlyNotation) load(level, notation, level.curr().player)
    return level
}