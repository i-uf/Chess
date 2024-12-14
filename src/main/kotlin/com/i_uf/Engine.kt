package com.i_uf

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import com.i_uf.Piece.*

fun engine(level: Level): String {
    val stockfishPath = File(App::class.java.getResource("/stockfish-windows-x86-64.exe")!!.toURI()).absolutePath
    val process = ProcessBuilder(stockfishPath)
        .redirectErrorStream(true)
        .start()
    val input = BufferedReader(InputStreamReader(process.inputStream))
    val output = OutputStreamWriter(process.outputStream)
    sendCommand(output, "ucinewgame")
    println("position startpos ${levelToMove(level)}")
    sendCommand(output, "position startpos ${levelToMove(level)}")
    sendCommand(output, "go depth 27")
    val result = readOutput(level.curr(), input)
    sendCommand(output, "quit")
    return result
}
private fun levelToMove(level: Level): String {
    val result = level.allMoves().apply { pop() }.map {
        "" + ('a' + it.move[0].first.file) + ('1' + it.move[0].first.rank) +
                ('a' + it.move[0].second.file) + ('1' + it.move[0].second.rank)
    }
    println(result)
    return if(result.isEmpty()) "" else "moves " + result.joinToString(" ")
}
private fun readMove(board: Board, string: String): String {
    require(string.matches(Regex("([a-h][1-8]){2}[qrnb]?")))
    val piece = (if(string.length == 5) when(string[4]) {
        'b' -> W_BISHOP to B_BISHOP
        'r' -> W_ROOK to B_ROOK
        'n' -> W_KNIGHT to B_KNIGHT
        else -> W_QUEEN to B_QUEEN
    } else W_QUEEN to B_QUEEN).let { if(board.player==Player.WHITE) it.first else it.second }
    return board.moveApply(string[1] - '1', string[0] - 'a', string[3] - '1', string[2] - 'a', Player.NONE, piece)?.let { notation(it) }?:""
}
private fun sendCommand(output: OutputStreamWriter, command: String) {
    output.write("$command\n")
    output.flush()
}
private fun readOutput(board: Board, input: BufferedReader): String {
    var line: String?
    while (true) {
        line = input.readLine()
        if(line == null) break;
        if(line.contains("bestmove")) return readMove(board, Regex("([a-h][1-8]){2}").find(line)!!.value)
    }
    return ""
}