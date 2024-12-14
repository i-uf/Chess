package com.i_uf

import com.i_uf.Piece.*
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.Graphics
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun main() {
    app.init()
}
val app = App()
var scroll = 0
var highlightPos = Position(-1, -1)
var rotate = false
var drag = false
var promote = Position(-1, -1) to Position(-1, -1)
var clicked = Position(-1, -1)
var piece = Position(-1, -1)
var level = Level(Board(Piece.default, Player.WHITE))
var engineMove = ""

val highlight = HashSet<Position>()
var editor = false

var animation = false
var accumulation = 0L
var prev = 0L

var liveGame: Player? = Player.NONE
private const val animationTime = 100f

class App : JPanel() {
    private val big = Font("arial", Font.PLAIN, 50)
    private val small = Font("arial", Font.PLAIN, 25)
    var mouse = 0 to 0
    private val tile = {min(width, height) / 10}
    fun init() {
        isFocusable = true
        font = big
        alignmentY = JLabel.CENTER_ALIGNMENT
        val frame = JFrame("Chess")
        frame.setSize(1000, 800)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane = this
        frame.extendedState = JFrame.MAXIMIZED_BOTH
        frame.background = Color(0x242424)
        frame.isVisible = true
        val make: (MouseEvent) -> Position = {
            Position(if(rotate) it.y / tile() - 1 else 8 - it.y / tile(),
                if(rotate) 8 - it.x / tile() else it.x / tile() - 1)
        }
        var cancel = false
        addMouseWheelListener(object : MouseAdapter() {
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                scroll = max(0, scroll + e.wheelRotation)
                SwingUtilities.invokeLater(this@App::repaint)
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val pos = make(e)
                if(e.y in tile()..tile()*2 && e.button == 1) {
                    when(e.x/tile()) {
                        10 -> {
                            engineMove = ""
                            Thread{
                                engineMove = engine(level)
                                SwingUtilities.invokeLater(this@App::repaint)
                            }.start()
                        }
                        11 -> if(liveGame == Player.NONE) ChessServer(level)
                        12 -> if(liveGame == Player.NONE) ChessClient(level)
                        13 -> liveGame = Player.NONE
                    }
                }
                if(min(pos.rank, pos.file) >= 0 && max(pos.rank, pos.file) < 8) {
                    val f = {
                        Position(pos.rank, pos.file) in canMoves(
                            piece.rank, piece.file, level.curr(), liveGame!!.next())
                    }
                    if (e.button == 1) {
                        if(promote != Position(-1, -1) to Position(-1, -1) && pos.file == promote.second.file) {
                            val white = level.curr().player == Player.WHITE
                            val (rank, file) = promote.first
                            val (newRank, newFile) = promote.second
                            val move: (Piece) -> Unit = { piece ->
                                level.curr().moveApply(rank, file, newRank, newFile, liveGame!!.next(), piece)?.let{ level.push(it) }
                                request = notation(level.curr())
                                animation()
                            }
                            when (if (white) 7 - pos.rank else pos.rank) {
                                1 -> move(if (white) W_QUEEN else B_QUEEN)
                                2 -> move(if (white) W_ROOK else B_ROOK)
                                3 -> move(if (white) W_KNIGHT else B_KNIGHT)
                                4 -> move(if (white) W_BISHOP else B_BISHOP)
                            }
                            piece = Position(-1, -1)
                            highlight.clear()
                            highlightPos = Position(-1, -1)
                        } else if (cancel && f()) {
                            level.curr().moveApply(piece.rank, piece.file,
                                pos.rank, pos.file, liveGame!!.next())?.let{level.push(it)}
                            request = notation(level.curr())
                            highlight.clear()
                            if(promote == Position(-1, -1) to Position(-1, -1)) {
                                animation()
                                highlightPos = Position(pos.rank, pos.file)
                                highlight.addAll(canMoves(pos.rank, pos.file, level.curr(), liveGame!!.next()))
                            }
                            piece = Position(-1, -1)
                            cancel = false
                            SwingUtilities.invokeLater(this@App::repaint)
                            return
                        } else if (level.curr().get(pos.rank, pos.file) != NONE) {
                            piece = Position(pos.rank, pos.file)
                            highlight.clear()
                            highlightPos = Position(pos.rank, pos.file)
                            drag = true
                            highlight.addAll(canMoves(pos.rank, pos.file, level.curr(), liveGame!!.next()))
                        } else {
                            piece = Position(-1, -1)
                            highlight.clear()
                            highlightPos = Position(-1, -1)
                        }
                    }
                }
                promote = Position(-1, -1) to Position(-1, -1)
                SwingUtilities.invokeLater(this@App::repaint)
            }
            override fun mouseReleased(e: MouseEvent) {
                if(e.button == 3 || liveGame == null) return
                val pos = make(e)
                val d = level.curr().get(pos.rank, pos.file)
                var l = Position(pos.rank, pos.file)
                cancel =
                    if(cancel && pos == clicked) {
                        l = Position(-1, -1)
                        highlight.clear()
                        highlightPos = Position(-1, -1)
                        false
                    } else if (pos in canMoves(piece.rank, piece.file, level.curr(), liveGame!!.next())) {
                        level.curr().moveApply(piece.rank, piece.file, pos.rank, pos.file, liveGame!!.next())?.let{level.push(it)}
                        request = notation(level.curr())
                        highlight.clear()
                        highlightPos = Position(pos.rank, pos.file)
                        l = Position(-1, -1)
                        false
                    } else {
                        cursor = if (d != NONE) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        else Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
                        l == Position(pos.rank, pos.file)
                    }
                drag = false
                clicked = l
                SwingUtilities.invokeLater(this@App::repaint)
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val (rank, file) = make(e)
                cursor = Cursor.getPredefinedCursor(if (level.curr().get(rank, file) != NONE) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
                mouse = e.x to e.y
            }
            override fun mouseDragged(e: MouseEvent) {
                if(drag) SwingUtilities.invokeLater(this@App::repaint)
                mouse = e.x to e.y
            }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if(e.keyCode == KeyEvent.VK_LEFT) {
                    println("asdfasdfsdaf")
                    level.undo()
                }
                if(e.keyCode == KeyEvent.VK_RIGHT) level.redo()
            }
        })
    }
    override fun paintComponent(g: Graphics) {
        val fill: (Int, Int, Int, Int, Int) -> Unit = {x, y, width, height, color ->
            g.color = Color(color)
            g.fillRect(x, y, width, height)
        }
        val text: (String, Int, Int, Int, Int) -> Unit = { text, x, y, alignment, width ->
            g.color = Color.WHITE
            g.font = big
            val metrics = g.fontMetrics
            val modifiedX = x + when (alignment % 3) {
                1 -> (width - metrics.stringWidth(text)) / 2
                2 -> width - metrics.stringWidth(text)
                else -> 0
            }
            var modifiedY = y + when (alignment / 3) {
                1 -> (tile() - metrics.height) / 2 + metrics.ascent;
                2 -> tile() - metrics.descent
                else -> metrics.ascent
            }
            for(text2 in text.split("\n")) {
                g.drawString(
                    text2,
                    modifiedX,
                    modifiedY
                )
                modifiedY += tile()
            }
        }
        val small: (String, Int, Int, Boolean) -> Unit = { text1, x, y, middle ->
            g.color = Color.WHITE
            g.font = small
            val metrics = g.fontMetrics
            var modifiedY = y +  (tile() - metrics.height) / 2 + metrics.ascent
            val modifiedX = x + if(middle) (tile() - metrics.stringWidth(text1)) / 2 else 0
            for(text2 in text1.split("\n")) {
                g.drawString(text2, modifiedX, modifiedY)
                modifiedY += tile() / 2
            }
        }
        fill(0, 0, width, height, 0x242424)
        for(rank in 0 until 8) for(file in 0 until 8) {
            val x = tile() + tile() * if(rotate) 7-file else file
            val y = tile() + tile() * if(rotate) rank else 7-rank
            val c = arrayOf(highlightPos, *level.curr()
                .move.flatMap{ listOf(it.first, it.second) }.toTypedArray())
            val a = Position(rank, file) in c
            val b = Position(rank, file) in highlight
            val d = Position(rank, file) in level.curr().move.map { it.first }
            g.color = if(rank%2 != file %2) if(a) Color(0xFFCC55) else Color(0xFFCC88)
            else if(a) Color(0xEE9944) else Color(0xCC7722)
            g.fillRect(x, y, tile(), tile())
            if(!d && (!drag || piece != Position(rank, file)))
                g.drawImage((if(animation) level.curr().prev else level.curr()).get(rank, file).image, x, y, tile(), tile(), null)
            if(b)
                g.drawImage((if(level.curr().get(rank, file) == NONE) IamJustSquare else IamJustTake).image, x, y, tile(), tile(), null)
            if(drag && (x..x+tile()).contains(mouse.first) && (y..y+tile()).contains(mouse.second))
                g.drawImage(IamJustDragSquare.image, x, y, tile(), tile(), null)
            if(rank == 6) {
                text("${if(rotate) '1' + file else '8' - file}", tile()*9, x, 3, tile())
                text("${if(rotate) 'h' - file else 'a' + file}", x, tile()*9, 1, tile())
                text("${if(rotate) '1' + file else '8' - file}", 0, x, 5, tile())
                text("${if(rotate) 'h' - file else 'a' + file}", x, 0, 7, tile())
            }
        }
        if(animation) {
            for(move in level.curr().move) {
                val x = tile() + tile() * if(rotate) 7-move.first.file else move.first.file
                val y = tile() + tile() * if(rotate) move.first.rank else 7-move.first.rank
                val x1 = tile() + tile() * if(rotate) 7-move.second.file else move.second.file
                val y1 = tile() + tile() * if(rotate) move.second.rank else 7-move.second.rank

                g.drawImage(
                    level.curr().prev.get(move.first.rank, move.first.file).image,
                    lerp(x, x1, accumulation / animationTime).toInt(),
                    lerp(y, y1, accumulation / animationTime).toInt(),
                    tile(), tile(),
                    null
                )
            }
        }
        if(promote != Position(-1, -1) to Position(-1, -1)) {
            val white = level.curr().player == Player.WHITE
            val (rank, file) = promote.second
            val x = tile() + tile() * if(rotate) 7-file else file
            val y = tile() + tile() * if(rotate) rank else 7-rank
            val unit = if(white) tile() else -tile()
            fill(x, min(y+unit, y+unit*4), tile(), tile()*4, 0xFFFFFF)
            g.drawImage((if(white) W_QUEEN else B_QUEEN).image, x, y+unit*1, tile(), tile(), null)
            g.drawImage((if(white) W_ROOK else B_ROOK).image, x, y+unit*2, tile(), tile(), null)
            g.drawImage((if(white) W_KNIGHT else B_KNIGHT).image, x, y+unit*3, tile(), tile(), null)
            g.drawImage((if(white) W_BISHOP else B_BISHOP).image, x, y+unit*4, tile(), tile(), null)
        }
        val s = (tile() * 1.125).toInt()
        if(drag) {
            g.drawImage(level.curr().get(piece.rank, piece.file).image,
                mouse.first - s/2, mouse.second - s/2, s, s, null)
        }
        fill(tile()*10, tile(), tile(), tile(), 0x7F7F7F)
        fill(tile()*11, tile(), tile(), tile(), if(liveGame == Player.NONE) 0x7F7F7F else 0x303030)
        fill(tile()*12, tile(), tile(), tile(), if(liveGame == Player.NONE) 0x7F7F7F else 0x303030)
        fill(tile()*13, tile(), tile(), tile(), if(liveGame == Player.NONE) 0x303030 else 0x7F7F7F)
        small(engineMove, tile()*10, 0, true)
        small("Engine", tile()*10, tile(), true)
        small("Server", tile()*11, tile(), true)
        small("Client", tile()*12, tile(), true)
        small("Exit", tile()*13, tile(), true)
        small("${level.white}(${level.whiteElo}) vs ${level.black}(${level.blackElo})", tile()*10, tile()*2, false)
        small(level.result, tile()*10, tile()*5/2, false)
        small(level.notation().split("\n").let {it.subList(scroll, min(it.size, scroll+12))}.joinToString("\n"), tile()*10, tile()*3, false)
    }
}
enum class Player(val next: ()->Player) {
    BLACK({WHITE}), WHITE({BLACK}), NONE({NONE})
}
enum class Piece(id: String, val player: Player) {
    NONE("", Player.NONE), IamJustSquare("/square.png", Player.NONE),
    IamJustTake("/take.png", Player.NONE), IamJustDragSquare("/drag_square.png", Player.NONE),
    B_PAWN("/b_pawn.png", Player.BLACK), B_ROOK("/b_rook.png", Player.BLACK),
    B_KNIGHT("/b_knight.png", Player.BLACK), B_BISHOP("/b_bishop.png", Player.BLACK),
    B_QUEEN("/b_queen.png", Player.BLACK), B_KING("/b_king.png", Player.BLACK),
    W_PAWN("/w_pawn.png", Player.WHITE), W_ROOK("/w_rook.png", Player.WHITE),
    W_KNIGHT("/w_knight.png", Player.WHITE), W_BISHOP("/w_bishop.png", Player.WHITE),
    W_QUEEN("/w_queen.png", Player.WHITE), W_KING("/w_king.png", Player.WHITE);
    val image: BufferedImage? = ImageIO.read(App::class.java.getResource(id));
    companion object {
        val default = arrayOf(
            arrayOf(W_ROOK, W_KNIGHT, W_BISHOP, W_QUEEN, W_KING, W_BISHOP, W_KNIGHT, W_ROOK),
            Array(8){ W_PAWN }, *Array(4){ Array(8){ NONE } }, Array(8){ B_PAWN },
            arrayOf(B_ROOK, B_KNIGHT, B_BISHOP, B_QUEEN, B_KING, B_BISHOP, B_KNIGHT, B_ROOK)
        )
        val test = arrayOf(
            Array(8){ NONE },
            Array(8){ if(it%2==0) B_PAWN else B_ROOK },
            *Array(4){ Array(8){ NONE } },
            Array(8){ if(it%2==0) W_PAWN else W_ROOK },
            Array(8){ NONE },
        )
        val empty = Array(8) { Array(8) { NONE } }
    }
}
data class Position(val rank: Int, val file: Int)
typealias Move = Pair<Position, Position>
class Board(val board: Array<Array<Piece>>, var player: Player,
            val move: Array<Move> = arrayOf(Position(-1, -1) to Position(-1, -1)),
            val enPassant: Position = Position(-1, -1),
            val castleW: Boolean = true,
            val castleB: Boolean = true,
            val largeCastleW: Boolean = true,
            val largeCastleB: Boolean = true,
            val halfMove: Int = 0,
            val fullMove: Int = 1
    ){
    var prev = this
    fun find(piece: Piece, scopeRank: Int = 8, scopeFile: Int = 8, newRank: Int, newFile: Int, condition: (Int, Int) -> Boolean = {
            i, j -> Position(newRank, newFile) in canMoves(i, j, this, Player.NONE)
    }) : Position {
        if(scopeRank != 8 && scopeFile != 8) return Position(scopeRank, scopeFile)
        if(scopeRank == 8 && scopeFile == 8) for(i in 0 until 8) for(j in 0 until 8)
            if(get(i, j) == piece && condition(i, j)) return Position(i, j)
        if(scopeRank != 8) for(i in 0 until 8) {
            if(get(scopeRank, i) == piece && condition(scopeRank, i)) return Position(scopeRank, i)
        }
        if(scopeFile != 8) for(i in 0 until 8) {
            if(get(i, scopeFile) == piece && condition(i, scopeFile)) return Position(i, scopeFile)
        }
        return Position(-1, -1)
    }
    fun get(rank: Int, file: Int) : Piece { return if(checkPosition(rank, file)) board[rank][file] else NONE }
    fun move(rank: Int, file: Int, newRank: Int, newFile: Int) : Board {
        if(checkPosition(rank, file) && checkPosition(newRank, newFile)) {
            val result = board.map { it.clone() }.toTypedArray()
            result[newRank][newFile] = get(rank, file)
            result[rank][file] = NONE
            return Board(result, player.next())
        } else return this
    }
    fun moveApply(rank: Int, file: Int, newRank: Int, newFile: Int, opponent: Player, promotion: Piece = NONE) : Board? {
        if(checkPosition(rank, file) && checkPosition(newRank, newFile) &&
            canMoves(rank, file, this, opponent).any{it.rank == newRank && it.file == newFile}) {
            animation = false
            val takes = get(newRank, newFile) != NONE
            val piece = get(rank, file)
            val newBoard = board.map { it.clone() }.toTypedArray()
            var newMove = if (get(newRank, newFile) != NONE)
                arrayOf(
                    Position(rank, file) to Position(newRank, newFile),
                    Position(-1, -1) to Position(-1, -1)
                )
            else arrayOf(Position(rank, file) to Position(newRank, newFile))
            val newHalfMove = if(takes) 0 else halfMove+1
            val newFullMove = if(piece.player == Player.BLACK) fullMove+1 else fullMove
            if (piece == B_PAWN && newRank == 0 || piece == W_PAWN && newRank == 7) {
                if(promotion == NONE) {
                    promote = Position(rank, file) to Position(newRank, newFile)
                    return null
                } else {
                    return Board(newBoard.apply {
                        this[newRank][newFile] = promotion ; this[rank][file] = NONE},
                        player.next(),
                        newMove,
                        Position(-1, -1),
                        castleW,
                        castleB,
                        largeCastleW,
                        largeCastleB,
                        newHalfMove,
                        newFullMove,
                    ).apply { prev = this@Board }
                }
            }
            if ((piece == B_KING || piece == W_KING) && (newFile == 2 || newFile == 6)) {
                if (newFile == 2 && if (player == Player.WHITE) largeCastleW else largeCastleB) {
                    newBoard[rank][3] = if (player == Player.WHITE) W_ROOK else B_ROOK
                    newBoard[rank][0] = NONE
                    newMove = arrayOf(
                        Position(rank, file) to Position(rank, newFile),
                        Position(-1, -1) to Position(-1, -1),
                        Position(-1, -1) to Position(-1, -1)
                    )
                } else if (newFile == 6 && if (player == Player.WHITE) castleW else castleB) {
                    newBoard[rank][5] = if (player == Player.WHITE) W_ROOK else B_ROOK
                    newBoard[rank][7] = NONE
                    newMove = arrayOf(
                        Position(rank, file) to Position(rank, newFile),
                        Position(-1, -1) to Position(-1, -1),
                        Position(-1, -1) to Position(-1, -1)
                    )
                }
            }
            if (piece == B_PAWN || piece == W_PAWN) {
                if (Position(newRank, newFile) == enPassant)
                    newBoard[if (newRank == 5) 4 else 3][newFile] = NONE
            }
            val newEnPassant = if ((piece == W_PAWN || piece == B_PAWN) && abs(newRank - rank) == 2)
                Position(if (piece == W_PAWN) 2 else 5, file)
            else Position(-1, -1)
            newBoard[newRank][newFile] = piece
            newBoard[rank][file] = NONE
            val newLargeCastleW = largeCastleW && newBoard[0][0] == W_ROOK && newBoard[0][4] == W_KING
            val newCastleW = castleW && newBoard[0][7] == W_ROOK && newBoard[0][4] == W_KING
            val newLargeCastleB = largeCastleB && newBoard[7][0] == B_ROOK && newBoard[7][4] == B_KING
            val newCastleB = castleB && newBoard[7][7] == B_ROOK && newBoard[7][4] == B_KING
            return Board(newBoard, player.next(), newMove,
                newEnPassant,
                newCastleW,
                newCastleB,
                newLargeCastleW,
                newLargeCastleB,
                newHalfMove,
                newFullMove
            ).apply { prev = this@Board }
        }
        return null
    }
    fun stalemate() : Boolean {
        for(i in 0 until 8) for(j in 0 until 8)
            if(canMoves(i, j, this, Player.NONE).isNotEmpty()) return false
        return true
    }
    private fun canGo(rank: Int, file: Int, piece: Piece, condition: (Int, Int) -> Boolean = { _, _ -> true},
                      ignore: Boolean = false) : Boolean {
        for(i in 0 until 8) for(j in 0 until 8)
            if(Position(rank, file) in canMoves(i, j, this, Player.NONE, ignore)
                && (piece == NONE || piece == get(i, j)) && condition(i, j)) return true
        return false
    }
    fun canMoveGo(move: Move, piece: Piece, condition: (Int, Int) -> Boolean = {_, _ -> true}) : Boolean {
        for(i in 0 until 8) for(j in 0 until 8)
            if(move.second in canMoves(i, j, this, Player.NONE) && piece == get(i, j) &&
                !(move.first.rank == i && move.first.file == j) && condition(i, j)) return true
        return false
    }
    fun kingSafety() : Boolean {
        val white = player == Player.WHITE
        return !find(if(white) B_KING else W_KING, newRank = -1, newFile = -1){_,_->true}
            .let { canGo(it.rank, it.file, NONE, ignore = true) }
    }
}
fun canMoves(rank: Int, file: Int, board: Board, opponent: Player, ignore: Boolean = false) : Set<Position> {
    val result = HashSet<Position>()
    val piece = board.get(rank, file)
    val player = piece.player
    val white = player == Player.WHITE
    if(board.player != player || !checkPosition(rank, file) || opponent == board.player) return result
    val add: (Int, Int) -> Boolean = { r, f ->
        if(checkPosition(r, f) && board.get(r, f).player != player
            && (ignore || board.move(rank, file, r, f).kingSafety())) result.add(Position(r, f))
        board.get(r, f) == NONE;
    }
    when(piece) {
        B_PAWN, W_PAWN -> {
            val pushRank = rank + if(white) 1 else -1
            val push2Rank = rank + if(white) 2 else -2
            if(board.get(pushRank, file) == NONE) {
                add(pushRank, file)
                if(board.get(push2Rank, file) == NONE && rank == (if(white) 1 else 6)) add(push2Rank, file)
            }
            if(board.get(pushRank, file - 1) != NONE || board.enPassant == Position(pushRank, file - 1))
                add(pushRank, file - 1)
            if(board.get(pushRank, file + 1) != NONE || board.enPassant == Position(pushRank, file + 1))
                add(pushRank, file + 1)
        }
        B_ROOK, W_ROOK -> for(i in -1..1) for(j in -1..1)
            for(k in 1 until 8) if(i and j == 0 && !add(rank+i*k, file+j*k)) break
        B_KNIGHT, W_KNIGHT -> for(i in -1..1) for(j in -1..1)
            if(i != 0 && j != 0 && (add(rank+i*2, file+j) or add(rank+i, file+j*2))) continue
        B_BISHOP, W_BISHOP -> for(i in -1..1) for(j in -1..1)
            for(k in 1 until 8) if(i != 0 && j != 0 && !add(rank+i*k, file+j*k)) break
        B_QUEEN, W_QUEEN -> for(i in -1..1) for(j in -1..1)
            for(k in 1 until 8) if(!add(rank+i*k, file+j*k)) break
        B_KING, W_KING -> {
            for(i in -1 ..1) for(j in -1..1) add(rank+i, file+j)
            if(!ignore && Board(board.board, board.player.next()).kingSafety()) {
                if(white && board.largeCastleW &&
                    board.get(0, 3) == NONE && board.get(0, 2) == NONE
                    && board.move(0, 4, 0, 3).kingSafety()
                    && board.get(0, 1) == NONE) add(0, 2)
                if(white && board.castleW &&
                    board.get(0, 5) == NONE && board.get(0, 6) == NONE
                    && board.move(0, 4, 0, 5).kingSafety()) add(0, 6)
                if(!white && board.largeCastleB &&
                    board.get(7, 3) == NONE && board.get(7, 2) == NONE
                    && board.move(7, 4, 7, 3).kingSafety()
                    && board.get(7, 1) == NONE) add(7, 2)
                if(!white && board.castleB &&
                    board.get(7, 5) == NONE && board.get(7, 6) == NONE
                    && board.move(7, 4, 7, 5).kingSafety()) add(7, 6)
            }
        }
        else -> {}
    }
    return result
}
fun animation() {
    animation = true
    prev = System.currentTimeMillis()
    accumulation = 0L
    Timer(1) {
        val now = System.currentTimeMillis()
        accumulation += minOf(100, now - prev)
        prev = now
        if (accumulation >= animationTime || !animation) {
            animation = false
            (it.source as Timer).stop()
        }
        SwingUtilities.invokeLater(app::repaint)
    }.start()
}

fun checkPosition(rank: Int, file: Int) : Boolean { return min(rank, file) >= 0 && max(rank, file) < 8 }
fun lerp(a: Int, b: Int, t: Float): Float {
    require(t in 0.0..1.0) { "t must be in the range [0, 1]" }
    return a * (1f - t) + b * t
}