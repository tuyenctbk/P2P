package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier.size(200.dp),
    qrColor: Color = Color.Black,
    backgroundColor: Color = Color.White
) {
    val modules = remember(data) {
        generateQrMatrix(data, 25)
    }

    Canvas(modifier = modifier) {
        val sizePx = size.width
        val moduleSize = sizePx / 25f

        drawRect(color = backgroundColor, size = size)

        for (row in 0 until 25) {
            for (col in 0 until 25) {
                if (modules[row][col]) {
                    drawRect(
                        color = qrColor,
                        topLeft = Offset(col * moduleSize, row * moduleSize),
                        size = Size(moduleSize + 0.5f, moduleSize + 0.5f)
                    )
                }
            }
        }
    }
}

private fun generateQrMatrix(data: String, n: Int): Array<BooleanArray> {
    val matrix = Array(n) { BooleanArray(n) { false } }
    
    fun drawFinder(startRow: Int, startCol: Int) {
        for (r in 0..6) {
            for (c in 0..6) {
                val isOuter = r == 0 || r == 6 || c == 0 || c == 6
                val isInner = r in 2..4 && c in 2..4
                matrix[startRow + r][startCol + c] = isOuter || isInner
            }
        }
    }

    drawFinder(0, 0)
    drawFinder(0, n - 7)
    drawFinder(n - 7, 0)

    for (i in 8 until n - 8) {
        matrix[6][i] = (i % 2 == 0)
        matrix[i][6] = (i % 2 == 0)
    }

    val hash = data.hashCode()
    var seed = if (hash == 0) 12345 else Math.abs(hash)
    
    for (r in 0 until n) {
        for (c in 0 until n) {
            val inTopLeft = r < 9 && c < 9
            val inTopRight = r < 9 && c >= n - 9
            val inBottomLeft = r >= n - 9 && c < 9
            val isTiming = r == 6 || c == 6
            
            if (!inTopLeft && !inTopRight && !inBottomLeft && !isTiming) {
                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                matrix[r][c] = (seed % 3 == 0)
            }
        }
    }
    return matrix
}
