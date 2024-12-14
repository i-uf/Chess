package com.i_uf

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import javax.swing.SwingUtilities

var request: String? = null
class ChessServer(level: Level) {
    init {
        liveGame = null
        Thread {
            val serverSocket = ServerSocket(12012)
            println("Server started, waiting for a client to connect...")

            val clientSocket = serverSocket.accept()
            println("Client connected: ${clientSocket.inetAddress}")
            liveGame = Player.WHITE

            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val output = OutputStreamWriter(clientSocket.getOutputStream())

            Thread {
                while (liveGame != Player.NONE) {
                    request?.let { output.appendLine(it) ; output.flush() ; request = null }
                }
            }.start()
            while (liveGame != Player.NONE) {
                try {
                    val message = input.readLine()
                    load(level, message, liveGame!!)
                    SwingUtilities.invokeLater(app::repaint)
                } catch(a: SocketException) {
                    println("Client disconnected.")
                    liveGame = Player.NONE
                    break
                }            }
            input.close()
            output.close()
            clientSocket.close()
        }.start()
    }
}
class ChessClient(level: Level) {
    init {
        liveGame = null
        Thread {
            val socket = Socket("localhost", 12012)
            println("Server connected: ${socket.inetAddress}")
            liveGame = Player.BLACK

            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = OutputStreamWriter(socket.getOutputStream())

            Thread {
                while (liveGame != Player.NONE) {
                    request?.let { output.appendLine(it) ; output.flush() ; request = null }
                }
            }.start()
            while (liveGame != Player.NONE) {
                try {
                    val message = input.readLine()
                    load(level, message, liveGame!!)
                    SwingUtilities.invokeLater(app::repaint)
                } catch(a: SocketException) {
                    println("Server disconnected.")
                    liveGame = Player.NONE
                    break
                }
            }
            input.close()
            output.close()
            socket.close()
        }.start()
    }
}