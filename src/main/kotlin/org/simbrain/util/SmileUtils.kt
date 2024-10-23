package org.simbrain.util

import org.simbrain.plot.histogram.HistogramModel
import org.simbrain.plot.histogram.HistogramPanel
import smile.math.matrix.Matrix
import kotlin.math.*

/**
 * Make sure the two matrices have the same shape
 */
fun Matrix.validateSameShape(target: Matrix) {
    if (target.nrow() != nrow() || target.ncol() != ncol()) {
        throw IllegalArgumentException("Matrix with shape $shapeString does not match matrix with shape " +
                "${target.shapeString}")
    }
}

fun Matrix.validateColumnVector() {
    if (ncol() != 1) {
        throw Error("Column vector expected, but vector of $shapeString found")
    }
}

/**
 * Copy the entries of [toCopy] into the receiver matrix.
 *
 * @param allowShapeMismatch If true, then smaller arrays are copied into larger ones (and the other entries in the
 *          larger one are ignored), and larger arrays are trimmed and copied to smaller arrays.
 */
@JvmOverloads
fun Matrix.copyFrom(toCopy: Matrix, allowShapeMismatch: Boolean = false) {
    if (!allowShapeMismatch) {
        validateSameShape(toCopy)
    }
    val nrow = min(this.nrow(), toCopy.nrow())
    val ncol = min(this.ncol(), toCopy.ncol())
    for (i in 0 until nrow) {
        for (j in 0 until ncol) {
            set(i,j, toCopy.get(i,j))
        }
    }
}

/**
 * Returns a matrix reshaped to an indicated size, trimming or padding as needed.
 */
fun Matrix.reshape(newNrows: Int, newNcols: Int): Matrix {
    val newMatrix = Matrix(newNrows, newNcols)
    newMatrix.copyFrom(this, allowShapeMismatch = true)
    return newMatrix
}


val Matrix.shapeString get() = "(${nrow()},${ncol()})"

// TODO: Flatten the two arrays so that this can be used for arbitrary matrices (currently works only on vectors)
infix fun Matrix.sse(other: Matrix) = (this.toDoubleArray() sse other.toDoubleArray())

infix fun Matrix.mse(other: Matrix) = (this.toDoubleArray() mse other.toDoubleArray())

infix fun Matrix.rmse(other: Matrix) = sqrt(this mse other)

fun crossEntropy(predictions: Matrix, targets: Matrix): Double {
    targets.validateColumnVector()
    predictions.validateColumnVector()
    var loss = 0.0
    for (i in 0 until targets.nrow()) {
        loss += targets[i, 0] * ln(predictions.get(i, 0).coerceAtLeast(1e-15))
    }
    return -loss
}

/**
 * Returns a specified row of a matrix, transposed so that it is a column vector.
 * A common requirement because Simbrain generally assumes column vectors.
 * A minor performance improvement, but originates in an effort to work around a bug with the MKL implementation.
 */
fun Matrix.rowVectorTransposed(rowIndex: Int): Matrix {
    if (rowIndex !in 0 until nrow()) {
        throw IllegalArgumentException("Invalid row index $rowIndex")
    }
    val ret = Matrix(ncol(),1)
    for (i in 0 until ncol()) {
        ret[i,0] = get(rowIndex, i)
    }
    return ret
}

/**
 * Convert a double array to a Smile Matrix / column vector.
 */
fun DoubleArray.toMatrix(): Matrix = Matrix.column(this)

fun Array<DoubleArray>.toMatrix(): Matrix = Matrix.of(this)

/**
 * Add the entries of a double array in-place to a Smile matrix / column vector. Assumes the matrix has as many rows
 * as the array has entries.
 */
fun Matrix.addi(toAdd: DoubleArray) {
    if (this.nrow() != toAdd.size) {
        throw IllegalArgumentException("Trying to add a double array of length ${toAdd.size} to a matrix with ${nrow
            ()} rows")
    }
    (0 until nrow()).forEach { i -> set(i,0, get(i, 0) + toAdd[i]) }
}

// All matrix operator mutates the original matrix
operator fun Matrix.plus(toAdd: Matrix): Matrix = this.clone().add(toAdd)
operator fun Matrix.minus(toSubtract: Matrix): Matrix = this.clone().sub(toSubtract)
operator fun Matrix.times(scalar: Double): Matrix = this.clone().mul(scalar)
//  Component-wise multiplication
operator fun Matrix.times(toMultiply: Matrix): Matrix = this.clone().mul(toMultiply)
operator fun Double.times(matrix: Matrix): Matrix = matrix.clone().mul(this)

fun Matrix.clip(min: Double, max: Double) {
    for (i in 0 until nrow()) {
        for (j in 0 until ncol()) {
            set(i,j, get(i,j).coerceIn(min, max))
        }
    }
}

operator fun Matrix.get(i: Int) = if (ncol() != 1) throw IllegalStateException("Must be a column vector") else get(i, 0)

fun Matrix.toDoubleArray() = if (ncol() != 1) throw IllegalStateException("Must be a column vector") else col(0)!!

fun Matrix.toSequence(): Sequence<Double> = if (ncol() != 1) throw IllegalStateException("Must be a column vector")
else sequence {
    for (i in (0 until this@Matrix.nrow())) {
        yield(this@Matrix.get(i, 0))
    }
}

/**
 * Returns a new matrix whose entries are shifted to the right by the indicated amount.
 */
fun Matrix.shiftRight(shiftAmount: Int = 1): Matrix {
    val shiftedMatrix = Matrix(nrow(), ncol())
    for (i in 0 until nrow()) {
        for (j in 0 until ncol()) {
            shiftedMatrix[i,j] = this[i, (j + ncol() - shiftAmount) % ncol()]
        }
    }
    return shiftedMatrix
}

fun Matrix.shiftUp(shiftAmount: Int = 1): Matrix {
    val shiftedMatrix = Matrix(nrow(), ncol())
    for (i in 0 until nrow()) {
        for (j in 0 until ncol()) {
            shiftedMatrix[i,j] = this[(i + nrow() + shiftAmount) % nrow(), j]
        }
    }
    return shiftedMatrix
}

/**
 * Set all elements of indicated row to the same provided value
 */
fun Matrix.setRowConstant(rowIndex: Int, value: Double) {
    for (i in 0 until ncol()) {
        this[rowIndex, i] = value
    }
}

fun Matrix.setRow(rowIndex: Int, values: DoubleArray) {
    if (values.size != ncol()) {
        throw IllegalArgumentException("Values array has ${values.size} elements, but matrix has ${ncol()} columns")
    }
    for (i in 0 until ncol()) {
        this[rowIndex, i] = values[i]
    }
}

/**
 * Set all elements of indicated column to the same provided value
 */
fun Matrix.setColConstant(colIndex: Int, value: Double) {
    for (i in 0 until nrow()) {
        this[i, colIndex] = value
    }
}

fun Matrix.shiftUpAndPadEndWithZero(): Matrix {
    val shifted = this.shiftUp()
    shifted.setRowConstant(nrow() - 1, 0.0)
    return shifted
}

/**
 * Element-wise multiplication that broadcasts across columns.
 *
 * Assumes vector is a column vector and that it has as many rows as the matrix.
 */
fun Matrix.broadcastMultiply(vector: Matrix): Matrix {
    if (vector.ncol() != 1) {
        throw IllegalArgumentException("Vector is ${vector.shapeString}, but it must be a column vector")
    }
    if (ncol() != vector.nrow()) {
        throw IllegalArgumentException("Size mismatched. Number of left matrix columns should match number of right vector rows: ${ncol()} columns, vector has ${vector.nrow()} rows.")
    }
    val result = Matrix(nrow(), ncol())
    for (i in 0 until nrow()) {
        for (j in 0 until ncol()) {
            result[i, j] = this[i, j] * vector[j, 0]
        }
    }
    return result
}

fun Matrix.flatten(): DoubleArray = flattenArray(toArray())

/**
 * Display a histogram for the (flattened) matrix.
 * Returns an updater function so that the histogram can be updated when the matrix changes.  That function takes a
 * matrix as an argument in case the matrix to be rendered changes (example: randomizing a weight matrix create a new matrix).
 */
@JvmOverloads
fun Matrix.showHistogram(title: String = "Show Histogram", label: String = ""): (Matrix) -> Unit {
    val histogramPanel = HistogramPanel(HistogramModel())
    histogramPanel.model.resetData(mutableListOf(this.flatten()), mutableListOf(label))
    histogramPanel.displayInDialog().apply { this.title = title }
    return { matrix -> histogramPanel.model.resetData(mutableListOf(matrix.flatten()), mutableListOf(label)) }
}

fun Matrix.maxEigenvalue() = eigen().wr.max()

fun Matrix.setSpectralRadius(spectralRadius: Double): Matrix {
    return mul(spectralRadius/maxEigenvalue())
}

fun Matrix.appendRow(row: DoubleArray = DoubleArray(ncol()) { 0.0 }): Matrix {
    val newMatrix = Matrix(nrow() + 1, ncol())
    for (i in 0 until nrow()) {
        for (j in 0 until ncol()) {
            newMatrix[i,j] = get(i,j)
        }
    }
    for (j in 0 until ncol()) {
        newMatrix[nrow(),j] = row[j]
    }
    return newMatrix
}

fun Matrix.layerNorm(epsilon: Double = 1e-5): Matrix {
    val normalized = Matrix(nrow(), ncol())
    for (i in 0 until nrow()) {
        val row = row(i)
        val mean = row.average()
        val variance = row.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance + epsilon)
        val normRow = row.map { (it - mean) / std }.toDoubleArray()
        normalized.setRow(i, normRow)
    }
    return normalized
}

fun Matrix.relu(): Matrix {
    val activated = Matrix(nrow(), ncol())
    for (i in 0 until nrow()) {
        for (j in 0 until ncol()) {
            activated[i,j] = max(0.0, get(i,j))
        }
    }
    return activated
}

fun Matrix.eigenValuesString(precision: Int = 2) = eigen().sort().let {
    val realParts = it.wr
    val imaginaryParts = it.wi
    fun format(r: Double, i: Double, isPair: Boolean) = if (i == 0.0) {
            r.format(precision)
        } else {
            "${r.format(precision)}${if (isPair) "±" else "+"}${i.format(precision)}i"
        }.replace(Regex("0\\."), ".")
        .replace("+-", "-")

    (realParts zip imaginaryParts)
        .groupBy { (r, i) -> r.roundTo(3) to abs(i.roundTo(3)) }
        .map { (_, values) ->
            if (values.distinctBy { (_, i) -> i }.count() > 1) {
                val (r, i) = values.first()
                format(r, i, isPair = true)
            } else {
                val (r, i) = values.first()
                format(r, i, isPair = false)
            }
        }
}
