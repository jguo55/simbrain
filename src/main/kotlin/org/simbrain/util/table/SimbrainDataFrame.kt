package org.simbrain.util.table

import org.simbrain.util.UserParameter
import org.simbrain.util.isIntegerValued
import org.simbrain.util.isRealValued
import org.simbrain.util.propertyeditor.EditableObject
import org.simbrain.util.stats.ProbabilityDistribution
import org.simbrain.util.stats.distributions.UniformRealDistribution
import smile.data.type.DataType
import javax.swing.table.AbstractTableModel

/**
 * Provides access to tabular data that can be viewed by a [SimbrainTablePanel].
 * Meant to be similar to a Pandas dataframe.
 * TODO: Methods similar to those found in Pandas should be added as the need arises
 * BasicDataFrame is the main implementation of this class.
 * MatrixDataFrame is for purely numeric data.
 */
abstract class SimbrainDataFrame : AbstractTableModel() {

    abstract var columns: MutableList<Column>

    val events = TableEvents()

    /**
     * If false, null entries cannot be edited.
     */
    var allowNullEditing = false

    /**
     * Index list of column classes. Previously overrode [getColumnClass] but this created problems.
     */
    val columnClasses: List<Class<*>>
        get() = columns.map { it.type.clazz() }

    override fun getColumnName(col: Int): String {
        return columns[col].name
    }

    /**
     * True if cells can be edited, and if the table structure can be edited.
     */
    abstract val isMutable: Boolean

    /**
     * Table-wide cell randomizer for arbitrary groups of cells.
     */
    @UserParameter(label = "Table Randomizer")
    var cellRandomizer = UniformRealDistribution()

    /**
     * This can be directly edited, but events must be manually fired. See dataworld.update
     */
    var currentRowIndex = 0

    /**
     * Check that the provided column index is within range
     */
    fun validateColumnIndex(colIndex: Int): Boolean {
        return colIndex in 0 until columnCount
    }

    /**
     * Check that the provided row index is within range
     */
    fun validateRowIndex(rowIndex: Int): Boolean {
        return rowIndex in 0 until rowCount
    }

    /**
     * Returns a column (assumed to be numeric) as a double array.
     */
    fun getDoubleColumn(col: Int): DoubleArray {
        if (columns[col].isNumeric()) {
            return (0 until rowCount)
                .map { (getValueAt(it, col) as Number).toDouble() }
                .toDoubleArray()
        }
        throw Error("getDoubleColumn called on a non-numeric column")
    }

    fun getFloatColumn(col: Int): FloatArray {
        if (columns[col].isNumeric()) {
            return (0 until rowCount)
                .map { (getValueAt(it, col) as Number).toFloat() }
                .toFloatArray()
        }
        throw Error("getFloatColumn called on a non-numeric column")
    }

    fun getIntColumn(col: Int): IntArray {
        if (columns[col].isNumeric()) {
            return (0 until rowCount)
                .map { (getValueAt(it, col) as Number).toInt() }
                .toIntArray()
        }
        throw Error("getIntColumn called on a non-numeric column")
    }

    fun getBooleanColumn(col: Int): BooleanArray {
        if (columns[col].isNumeric()) {
            return (0 until rowCount)
                .map { (getValueAt(it, col) as Number).toInt() == 0 }
                .toBooleanArray()
        }
        throw Error("getIntColumn called on a non-numeric column")
    }

    fun getStringColumn(col: Int): Array<String> {
        if (columns[col].type == Column.DataType.StringType ) {
            return (0 until rowCount)
                .map { (getValueAt(it, col) as String) }
                .toTypedArray()
        }
        throw Error("getStringColumn called on a column that is not a String")
    }

    /**
     * Returns all double columns as an array of double arrays.
     */
    fun getColumnMajorArray(): Array<DoubleArray> {
        return (0 until columnCount)
            .filter { columns[it].isNumeric() }
            .map { getDoubleColumn(it) }
            .toTypedArray()
    }

    /**
     * If all columns in [colIndices] are instances of one of the types in [classes], return true
     */
    fun columnsOfType(colIndices: List<Int>, vararg classes: Class<*>): Boolean {
        return colIndices.all {
            classes.contains(columnClasses[it])
        }
    }

    /**
     * Ensure all columns have the indicated type or types.
     */
    fun columnsOfType(vararg classes: Class<*>): Boolean {
        return  columnsOfType((0 until columnCount).toList(), *classes)
    }

    private fun getDoubleRowUnsafe(row: Int, replaceInvalid: Double = Double.NaN): DoubleArray {
        // No type check
        return (0 until columnCount)
            .map { ((getValueAt(row, it) ?: replaceInvalid) as Number).toDouble() }
            .toDoubleArray()
    }

    private fun getFloatRowUnsafe(row: Int): FloatArray {
        // No type check
        return (0 until columnCount)
            .map { (getValueAt(row, it) as Number).toFloat() }
            .toFloatArray()
    }

    /**
     * Returns a 2d double array using provided column indices.
     *
     * Note that numeric types are cast to doubles.
     */
    fun get2DDoubleArray(colIndices: List<Int>): Array<DoubleArray> {
        if (!columnsOfType(colIndices, Double::class.java, Int::class.java, Float::class.java)) {
            throw Error("getDoubleArray called on a non-double column")
        }
        return (0 until rowCount)
            .map { rowIndex ->
                colIndices.map { colIndex ->
                    (getValueAt(rowIndex, colIndex) as Number).toDouble()
                }.toDoubleArray()
            }.toTypedArray()
    }


    /**
     * Returns a 2d double array using columns in the provided range.
     */
    fun get2DDoubleArray(indices: IntRange): Array<DoubleArray> {
        return get2DDoubleArray(indices.toList())
    }

    /**
     * Returns an array of double array rows for the table (comparable to "row major" order).
     *
     * Numeric types are cast to doubles.
     */
    fun get2DDoubleArray(replaceInvalid: Double = Double.NaN): Array<DoubleArray> {
        if (!columnsOfType(Double::class.java)) {
            throw Error("getDoubleArray called on a non-numeric column")
        }
        return (0 until rowCount)
            .map { getDoubleRowUnsafe(it, replaceInvalid) }
            .toTypedArray()
    }

    /**
     * Returns an array of float array rows for the table (comparable to "row major" order).
     *
     * Doubles and ints are cast to floats.
     */
    fun getFloat2DArray(): Array<FloatArray> {
        if (!columnsOfType(Double::class.java, Int::class.java)) {
            throw Error("getFloat2DArray called on a non-numeric column")
        }
        return (0 until rowCount)
            .map { getFloatRowUnsafe(it) }
            .toTypedArray()
    }

    override fun toString(): String {
        val opts = ImportExportOptions().apply {
            includeColumnNames = true
            includeRowNames = true
        }
        return toStringLists(opts).joinToString("\n") { it.joinToString(" ") }
    }
    /**
     * Returns a list of the provided type.
     * If columns do not have consistent types of class cast exception will be thrown.
     */
    inline fun <reified T> getRow(index: Int): List<T> = (0 until columnCount).map {
        when(T::class) {
            String::class -> getValueAt(index, it).toString()
            Double::class -> getValueAt(index, it)?.let { (it as Number).toDouble() } ?: Double.NaN
            Float::class -> getValueAt(index, it)?.let { (it as Number).toFloat() } ?: Float.NaN
            Int::class -> (getValueAt(index, it) as Number).toInt()
            Boolean::class -> (getValueAt(index, it) as Number).toInt() == 0
            else -> throw IllegalArgumentException("Unsupported type ${T::class}")
        } as T
    }

    fun getCurrentStringRow() = getRow<String>(currentRowIndex)

    fun getCurrentDoubleRow() = getRow<Double>(currentRowIndex)

    /**
     * Returns an array of float array columns for the table.
     *
     * Doubles and ints are cast to floats.
     */
    fun getColumnMajorIntArray(): Array<IntArray> {
        return (0 until columnCount)
            .filter { columns[it].isNumeric() }
            .map { getIntColumn(it) }
            .toTypedArray()
    }

    open fun randomizeColumn(col: Int) {}

    /**
     * Override to provide this functionality.
     */
    open fun insertColumn(selectedColumn: Int) {}

    open fun deleteColumn(selectedColumn: Int, fireEvent: Boolean = true) {}

    var rowNames = listOf<String?>()
        set(value) {
            field = value
            fireTableStructureChanged()
            events.rowNameChanged.fire()
        }

    fun getRowName(row: Int): String {
        return rowNames.getOrNull(row) ?: (row + 1).toString()
    }

    fun getAllRowNames() = (0 until rowCount).map { getRowName(it) }

    var columnNames: List<String>
        get() = columns.map { it.name }
        set(value) {
            columns = columns.mapIndexed { i, col ->
                Column(value.getOrNull(i) ?: "Column ${i + 1}", col.type)
            }.toMutableList()
            fireTableDataChanged()
        }

    open fun insertRow(selectedRow: Int) {}

    open fun setRow(selectedRow: Int, row: Array<out Any?>) {}

    open fun deleteRow(selectedRow: Int, fireEvent: Boolean = true) {}

    fun insertRowAtBottom() {
        insertRow(rowCount)
    }

    fun deleteLastRow() {
        deleteRow(rowCount - 1 , true)
    }

    fun canEditAt(rowIndex: Int, columnIndex: Int): Boolean {
        return allowNullEditing || getValueAt(rowIndex, columnIndex) != null
    }
}

class Column(
    @UserParameter(label = "Name", order = 1)
    val columName: String,

    @UserParameter(label = "Type", order = 2)
    var type: DataType = DataType.DoubleType
) : EditableObject {

    /**
     * Construct a column using a Smile data type object.
     */
    constructor(name: String, smileDataType: smile.data.type.DataType) : this(
        name,
        smileToSimbrainDataType(smileDataType)
    )

    @UserParameter(label = "Enabled", order = 10)
    var enabled = true

    /**
     * Randomizer for this column.
     */
    @UserParameter(label = "Column Randomizer", order = 20)
    var columnRandomizer: ProbabilityDistribution = UniformRealDistribution()

    fun getRandom(): Number {
        if (type == DataType.DoubleType) {
            return columnRandomizer.sampleDouble()
        } else if (type == DataType.IntType) {
            return columnRandomizer.sampleInt()
        }
        return 0
    }

    override val name: String
        get() = columName

    enum class DataType {
        DoubleType {
            override fun clazz(): Class<*> {
                return Double::class.java
            }
            override val defaultValue = 0.0
        },
        IntType {
            override fun clazz(): Class<*> {
                return Int::class.java
            }
            override val defaultValue = 0
        },
        StringType {
            override fun clazz(): Class<*> {
                return String::class.java
            }
            override val defaultValue = ""
        };

        abstract fun clazz(): Class<*>
        abstract val defaultValue: Any

    }

    fun isNumeric(): Boolean {
        return type == DataType.DoubleType || type == DataType.IntType
    }

}

/**
 * Create a column from a value of unknown type. Try treating it as integer, then double, and if that fails treat it as
 * a String.
 */
fun createColumn(name: String, value: Any?): Column {
    if (value.isIntegerValued()) {
        return Column(name, Column.DataType.IntType)
    }
    if (value.isRealValued()) {
        return Column(name, Column.DataType.DoubleType)
    }
    if (value is String) {
        try {
            value.toInt()
            return Column(name, Column.DataType.IntType)
        } catch (e: NumberFormatException) {
            // Do nothing, move on to the next case
        }
        try {
            value.toDouble()
            return Column(name, Column.DataType.DoubleType)
        } catch (e: NumberFormatException) {
            // Do nothing, move on to the next case
        }
    }
    return Column(name, Column.DataType.StringType)

}

fun getDataType(clazz: Class<*>): Column.DataType {
    return when (clazz) {
        Double::class.java -> Column.DataType.DoubleType
        Float::class.java -> Column.DataType.DoubleType
        Int::class.java -> Column.DataType.IntType
        Byte::class.java -> Column.DataType.IntType
        String::class.java -> Column.DataType.StringType
        else -> Column.DataType.StringType
    }
}

fun smileToSimbrainDataType(smileDataType: DataType): Column.DataType {
    return when (smileDataType.id()) {
        DataType.ID.Double -> Column.DataType.DoubleType
        DataType.ID.Integer -> Column.DataType.IntType
        else -> Column.DataType.StringType
    }
}


fun SimbrainDataFrame.toStringLists(options: ImportExportOptions) = buildList {
    if (options.includeColumnNames) {
        buildList {
            if (options.includeRowNames) {
                add("")
            }
            addAll(columnNames)
        }.also { add(it) }
    }
    (0 until rowCount).forEach { i ->
        buildList {
            if (options.includeRowNames) {
                add(getRowName(i))
            }
            addAll(getRow<String>(i))
        }.also { add(it) }
    }

}